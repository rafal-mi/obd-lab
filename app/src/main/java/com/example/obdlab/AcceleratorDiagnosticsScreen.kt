package com.example.obdlab

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.obdlab.obd.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

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
        for (pid in selected) {
            val series = samples.filter { it.pid == pid }
            val latest = series.lastOrNull()?.percent
            Text("${pid.label} (PID ${pid.hex}): " +
                (latest?.let { "%.1f%%".format(Locale.ROOT, it) } ?: "No valid response"))
            val color = MaterialTheme.colorScheme.primary
            val window = series.takeLast(120)
            Canvas(Modifier.fillMaxWidth().height(90.dp)) {
                val min = window.firstOrNull()?.elapsedMs ?: 0L
                val span = ((window.lastOrNull()?.elapsedMs ?: min) - min).coerceAtLeast(1)
                for ((a, b) in window.zipWithNext()) {
                    val y1 = a.percent ?: continue
                    val y2 = b.percent ?: continue
                    drawLine(color,
                        Offset(size.width * ((a.elapsedMs - min).toFloat() / span), size.height * (1f - y1.toFloat() / 100f)),
                        Offset(size.width * ((b.elapsedMs - min).toFloat() / span), size.height * (1f - y2.toFloat() / 100f)),
                        strokeWidth = 2.dp.toPx())
                }
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
