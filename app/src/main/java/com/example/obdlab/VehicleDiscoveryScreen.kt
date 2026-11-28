package com.example.obdlab

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DiscoveryState {
    var status by mutableStateOf("Connect an adapter or simulator to discover vehicle capabilities.")
    var source by mutableStateOf("")
    var protocol by mutableStateOf("")
    var adapter by mutableStateOf("")
    var pages by mutableStateOf(emptyList<VehicleDiscovery.Page>())
    val raw = mutableStateListOf<String>()
    fun report() = buildString {
        appendLine("Source: $source")
        appendLine("Adapter: $adapter")
        appendLine("Protocol: $protocol")
        appendLine("Status: $status")
        pages.forEach { appendLine("${it.responder} ${VehicleDiscovery.hex(it.base)} ${it.bitmap}: ${it.pids.joinToString { p -> VehicleDiscovery.hex(p) }}") }
        raw.forEach { appendLine(it) }
    }
}

@Composable
fun VehicleDiscoveryScreen(connection: DiagnosticSessionManager, discovery: DiscoveryState) {
    val state by connection.state.collectAsState()
    val busy by connection.busy.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var exportStatus by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            val report = discovery.report()
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(report.toByteArray()) }
                    }
                    exportStatus = "Report saved."
                } catch (e: Exception) { exportStatus = "Export failed: ${e.message}" }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Vehicle discovery", style = MaterialTheme.typography.headlineSmall)
        Text(discovery.status)
        if (discovery.source.isNotEmpty()) Text("Source: " + discovery.source)
        Button(enabled = state is DiagnosticSessionManager.State.Connected && !busy && !running,
            onClick = {
                running = true
                discovery.source = state.description
                discovery.pages = emptyList()
                discovery.raw.clear()
                discovery.protocol = ""
                discovery.adapter = ""
                discovery.status = "Initializing adapter…"
                scope.launch {
                    try {
                        connection.exclusive {
                            suspend fun request(cmd: String): String {
                                discovery.raw.add("TX: $cmd")
                                val response = command(cmd)
                                discovery.raw.add("RX: $response")
                                return response
                            }
                            try {
                                request("ATZ")
                                discovery.adapter = request("ATI").trim()
                                for (cmd in listOf("ATE0","ATL0","ATS1","ATH1","ATSP0")) {
                                    check(request(cmd).lineSequence().any { it.trim() == "OK" }) { "Adapter rejected $cmd" }
                                }
                                val first = request("0100")
                                discovery.protocol = request("ATDP").trim()
                                val number = request("ATDPN").trim().uppercase()
                                if (number !in setOf("6","7","8","9","A6","A7","A8","A9")) {
                                    discovery.status = "Protocol identified; header parsing currently supports CAN only. Export raw report."
                                    return@exclusive
                                }
                                var pages = VehicleDiscovery.parseCan(first,0)
                                check(pages.isNotEmpty()) { "No recognized CAN bitmap response" }
                                discovery.pages = pages
                                var base = 0
                                while (base < 0xE0) {
                                    val eligible = pages.filter { base+32 in it.pids }.map { it.responder }.toSet()
                                    if (eligible.isEmpty()) break
                                    base += 32
                                    pages = VehicleDiscovery.parseCan(request("01" + VehicleDiscovery.hex(base)),base)
                                        .filter { it.responder in eligible }
                                    if (pages.map { it.responder }.toSet() != eligible) {
                                        discovery.pages = discovery.pages + pages
                                        error("Incomplete continuation at PID " + VehicleDiscovery.hex(base))
                                    }
                                    discovery.pages = discovery.pages + pages
                                }
                                for (cmd in listOf("03","07","0A")) request(cmd)
                                discovery.status = "Discovery complete. DTC responses retained raw."
                                Log.i("PidDiscovery", discovery.pages.joinToString { "${it.responder}: ${it.bitmap}" })
                            } finally {
                                request("ATH0")
                            }
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        discovery.status = "Discovery stopped: ${e.message}"
                        Log.w("PidDiscovery", discovery.status, e)
                    } finally { running = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Discover vehicle") }
        if (discovery.adapter.isNotEmpty()) Text("Adapter: " + discovery.adapter)
        if (discovery.protocol.isNotEmpty()) Text("Protocol: " + discovery.protocol)
        for ((responder,pages) in discovery.pages.groupBy { it.responder }) {
            Text("Responder $responder", style = MaterialTheme.typography.titleMedium)
            val pids = pages.flatMap { it.pids }.toSortedSet()
            Text("Advertised: ${pids.size} PIDs (including continuation)")
            Text(if (pids.any { it in setOf(0x49,0x4A,0x4B,0x5A) }) "Standard pedal PIDs advertised" else "No standard pedal PIDs found in parsed pages")
            for (page in pages) {
                Text("01${VehicleDiscovery.hex(page.base)}: ${page.bitmap}")
                Text(page.bitmap.split(" ").joinToString(" ") { it.toInt(16).toString(2).padStart(8,'0') })
            }
            for (pid in pids) Text("${VehicleDiscovery.hex(pid)} — ${VehicleDiscovery.name(pid)}")
        }
        OutlinedButton(enabled = !running && discovery.raw.isNotEmpty(), onClick = {
            export.launch("vehicle-discovery-${System.currentTimeMillis()}.txt")
        }) { Text("Save discovery report") }
        Text(exportStatus)
        if (discovery.raw.isNotEmpty()) {
            Text("Raw communication", style = MaterialTheme.typography.titleMedium)
            discovery.raw.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
