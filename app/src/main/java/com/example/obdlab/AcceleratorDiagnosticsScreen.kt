package com.example.obdlab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.obdlab.obd.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import androidx.compose.ui.graphics.Color
import com.example.obdlab.ui.chart.*

@Composable
fun AcceleratorDiagnosticsScreen(
    connection: DiagnosticSessionManager,
    recording: AcceleratorRecordingState,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val state by connection.state.collectAsState()
    val busy by connection.busy.collectAsState()
    val connected = state is DiagnosticSessionManager.State.Connected
    var running by recording.running
    var stopRequested by recording.stopRequested
    var failed by recording.failed
    var status by recording.status
    var selected by recording.selected
    val samples = recording.samples
    val transcript = recording.transcript
    fun copy(label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    }
    fun goBack() {
        if (running) stopRequested = true
        else onBack()
    }
    BackHandler { goBack() }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Accelerator diagnostics", style = MaterialTheme.typography.headlineSmall)
        Text(status)
        if (!connected) Text("Offline — connect an adapter or simulator from Home / Connection.")
        if (recording.source.value.isNotEmpty()) Text("Recording source: " + recording.source.value)
        Button(
            enabled = connected && !running && !busy,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            onClick = {
                recording.source.value = state.description
                failed = false
                running = true
                stopRequested = false
                samples.clear()
                transcript.clear()
                selected = emptyList()
                scope.launch {
                    try {
                        connection.exclusive {
                    suspend fun request(command: String): String {
                        val response = command(command)
                        transcript.add("$command > $response")
                        return response
                    }
                        status = "Discovering supported PIDs…"
                        // Reset adapter state so prior console addressing cannot leak into this run.
                        for (command in listOf("ATZ", "ATE0", "ATL0", "ATS1", "ATH0", "ATSP0")) {
                            if (stopRequested) return@exclusive
                            val response = request(command)
                            if (command != "ATZ" && !response.lineSequence().any { it.trim() == "OK" })
                                error("Adapter rejected $command")
                        }
                        val supported = mutableSetOf<Int>()
                        for (base in listOf(0, 0x20, 0x40)) {
                            if (stopRequested) return@exclusive
                            val response = request("01%02X".format(Locale.ROOT, base))
                            val page = AcceleratorDiagnostics.supported(response, base)
                                ?: error("Cannot decode PID bitmap for %02X (multiple or malformed responses)".format(base))
                            supported.addAll(page)
                            if (base + 32 !in page) break
                        }
                        selected = AcceleratorPid.entries.filter { it.id in supported }
                        if (selected.isEmpty()) {
                            status = "No standard accelerator pedal PIDs advertised. Copy discovery log."
                            return@exclusive
                        }
                        val start = SystemClock.elapsedRealtime()
                        status = if (recording.source.value.contains("Simulation")) "Recording synthetic pedal movement." else "Recording — slowly press and release the pedal three times."
                        while (!stopRequested && SystemClock.elapsedRealtime() - start < 120_000 && samples.size < 2000) {
                            for (pid in selected) {
                                if (stopRequested || samples.size >= 2000) break
                                val timestamp = SystemClock.elapsedRealtime() - start
                                val response = request(pid.command)
                                samples.add(AcceleratorDiagnostics.sample(response, pid, timestamp))
                            }
                            delay(100)
                        }
                        status = "Recording finished: ${samples.size} samples."
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        failed = true
                        status = "Stopped: ${e.message}. Copy data, then reconnect."
                    } finally {
                        running = false
                        if (stopRequested && !failed) status = "Stopped: ${samples.size} samples."
                    }
                }
            }
        ) { Text("Start diagnostic recording") }
        Button(
            onClick = { stopRequested = true },
            enabled = running && !stopRequested,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text(if (stopRequested && running) "Stopping after current request…" else "Stop") }
        Text("Samples: ${samples.size}")
        val elapsed = samples.lastOrNull()?.elapsedMs ?: 0L
        Text("Elapsed: %.1f s".format(Locale.ROOT, elapsed / 1000.0))
        val colors = listOf(Color(0xFF42A5F5), Color(0xFFFFB74D), Color(0xFFAB47BC), Color(0xFF26A69A))
        val chartSeries = selected.map { pid ->
            val series = samples.filter { it.pid == pid }
            ChartSeries(pid.label + " (" + pid.hex + ")", colors[pid.ordinal],
                series.map { ChartPoint(it.elapsedMs, it.percent) })
        }
        if (selected.isNotEmpty()) {
            Text("Live readings", style = MaterialTheme.typography.titleMedium)
            for (pid in selected) {
                val series = samples.filter { it.pid == pid }
                val stats = SeriesStats.from(series.map { ChartPoint(it.elapsedMs, it.percent) })
                val latest = series.lastOrNull()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(pid.label + " (PID " + pid.hex + ")", style = MaterialTheme.typography.titleMedium)
                        Text(latest?.percent?.let { "%.1f%%".format(Locale.ROOT, it) } ?: "—",
                            style = MaterialTheme.typography.headlineMedium)
                        Text("Raw: " + (latest?.value?.let { "%d / 0x%02X".format(Locale.ROOT, it, it) } ?: "—"))
                        Text("Min: " + formatPercent(stats.min) + "   Max: " + formatPercent(stats.max))
                        Text("Rate: " + (stats.rateHz?.let { "%.1f Hz".format(Locale.ROOT, it) } ?: "—") +
                            "   Valid: ${stats.validCount}/${series.size}")
                    }
                }
            }
            Text("Combined signals", style = MaterialTheme.typography.titleMedium)
            Text("All channels share the same time and percentage axes. Latest 20 seconds.")
            val window = ChartWindow.trailing(elapsed, 20_000)
            TimeSeriesChart(chartSeries, window, 0.0, 100.0, "%", Modifier.fillMaxWidth())
            for (series in chartSeries) {
                Text(series.label, style = MaterialTheme.typography.titleSmall)
                TimeSeriesChart(listOf(series), window, 0.0, 100.0, "%", Modifier.fillMaxWidth())
            }
        }
        OutlinedButton(enabled = !running && samples.isNotEmpty(), onClick = {
            copy("Accelerator samples CSV", AcceleratorDiagnostics.csv(samples, recording.source.value))
        }, modifier = Modifier.fillMaxWidth()) { Text("Copy CSV") }
        OutlinedButton(enabled = !running && transcript.isNotEmpty(), onClick = {
            copy("Accelerator diagnostic log", "Source: " + recording.source.value + "\n" + transcript.joinToString("\n"))
        }, modifier = Modifier.fillMaxWidth()) { Text("Copy raw log") }
        OutlinedButton(enabled = !running, onClick = { goBack() },
            modifier = Modifier.fillMaxWidth()) { Text("Back to home") }
    }
}

/** Retained by the activity across screen navigation. */
class AcceleratorRecordingState {
    val running = mutableStateOf(false)
    val stopRequested = mutableStateOf(false)
    val failed = mutableStateOf(false)
    val status = mutableStateOf("Parked, ignition on, engine off. Press Start.")
    val selected = mutableStateOf(emptyList<AcceleratorPid>())
    val source = mutableStateOf("")
    val samples = mutableStateListOf<DiagnosticSample>()
    val transcript = mutableStateListOf<String>()
}

private fun formatPercent(value: Double?): String = value?.let { "%.1f%%".format(Locale.ROOT, it) } ?: "—"
