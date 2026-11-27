package com.example.obdlab

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.Alignment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.obdlab.obd.BluetoothObdTransport
import com.example.obdlab.obd.ObdTransport
import com.example.obdlab.obd.EngineRpm
import com.example.obdlab.obd.UsbObdTransport
import com.example.obdlab.ui.theme.OBDLabTheme
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import com.example.obdlab.obd.DiagnosticSessionManager
import com.example.obdlab.obd.SimulationTransport
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.example.obdlab.obd.DiagnosticCommands
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val USB_PERMISSION_ACTION = "com.example.obdlab.USB_PERMISSION"

class MainActivity : ComponentActivity() {
    private val connection = DiagnosticSessionManager()
    private val accelerator = AcceleratorRecordingState()
    private val usbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }
    private var permissionResult by mutableStateOf<UsbDevice?>(null)
    private var permissionMessage by mutableStateOf<String?>(null)

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != USB_PERMISSION_ACTION) return
            @Suppress("DEPRECATION")
            val device = if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            else intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                permissionResult = device
                permissionMessage = null
            } else {
                permissionMessage = "USB permission denied"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val filter = IntentFilter(USB_PERMISSION_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(permissionReceiver(), filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(permissionReceiver(), filter)
        }
        setContent {
            OBDLabTheme {
                ObdTerminal(
                    connection = connection,
                    accelerator = accelerator,
                    devices = usbManager.deviceList.values.toList(),
                    permissionDevice = permissionResult,
                    permissionMessage = permissionMessage,
                    refresh = { usbManager.deviceList.values.toList() },
                    requestPermission = { device ->
                        permissionResult = null
                        permissionMessage = null
                        val intent = Intent(USB_PERMISSION_ACTION).setPackage(packageName)
                        val pending = PendingIntent.getBroadcast(
                            this, 0, intent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        )
                        usbManager.requestPermission(device, pending)
                    },
                    createTransport = { UsbObdTransport(applicationContext, it) }
                )
            }
        }
    }

    private fun permissionReceiver(): BroadcastReceiver = usbPermissionReceiver

    override fun onDestroy() {
        unregisterReceiver(usbPermissionReceiver)
        connection.close()
        super.onDestroy()
    }
}


private data class TerminalEntry(val id: Int, val kind: String, val value: String, val timestamp: String)

@Composable
private fun ObdTerminal(
    connection: DiagnosticSessionManager,
    accelerator: AcceleratorRecordingState,
    devices: List<UsbDevice>,
    permissionDevice: UsbDevice?,
    permissionMessage: String?,
    refresh: () -> List<UsbDevice>,
    requestPermission: (UsbDevice) -> Unit,
    createTransport: (UsbDevice) -> UsbObdTransport,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val btManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    var usbDevices by remember { mutableStateOf(devices) }
    var selectedUsb by remember { mutableStateOf<UsbDevice?>(null) }
    var paired by remember { mutableStateOf(emptyList<android.bluetooth.BluetoothDevice>()) }
    var btMessage by remember { mutableStateOf("") }
    val busy by connection.busy.collectAsState()
    val state by connection.state.collectAsState()
    val connected = state is DiagnosticSessionManager.State.Connected
    var screen by remember { mutableStateOf(AppScreen.Home) }
    var commandText by remember { mutableStateOf("ATI") }
    var nextId by remember { mutableIntStateOf(0) }
    val entries = remember { mutableStateListOf<TerminalEntry>() }
    fun record(kind: String, value: String) {
        entries.add(TerminalEntry(nextId++, kind, value, SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(Date())))
    }

    @SuppressLint("MissingPermission")
    fun refreshBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            btMessage = "Bluetooth permission required"
            return
        }
        val adapter = btManager.adapter
        paired = if (adapter?.isEnabled == true) adapter.bondedDevices.orEmpty().sortedBy { it.name ?: it.address } else emptyList()
        btMessage = when {
            adapter == null -> "Bluetooth unavailable"
            !adapter.isEnabled -> "Enable Bluetooth in Android settings"
            paired.isEmpty() -> "No paired Bluetooth devices"
            else -> ""
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) refreshBluetooth() else btMessage = "Bluetooth permission denied"
    }

    suspend fun connect(candidate: ObdTransport, label: String, commands: List<String>) {
        entries.clear()
        record("status", "Connecting to " + label)
        try {
            connection.connect(candidate, label, commands) { cmd, response ->
                record("command", cmd)
                record("response", response)
            }
            record("status", "Adapter connected")
        } catch (error: Exception) {
            record("error", "Connection failed: " + error.message)
        }
    }

    fun send(cmd: String) {
        if (!connected || busy || cmd.isBlank()) return
        if (!DiagnosticCommands.isAllowed(cmd)) {
            record("error", "Command not in read-only console allowlist")
            return
        }
        keyboard?.hide()
        scope.launch {
            record("command", cmd)
            try {
                val response = connection.exclusive { command(cmd) }
                record("response", response.ifEmpty { "(empty response)" })
                if (cmd.equals("010C", ignoreCase = true)) {
                    val rpm = EngineRpm.decode(response)
                    if (rpm != null) record("status", "Engine speed: " + rpm + " RPM")
                    else record("status", "No valid engine RPM data")
                }
            } catch (error: Exception) {
                record("error", cmd + ": " + (error.message ?: "Unknown error"))
            }
        }
    }

    LaunchedEffect(permissionDevice) {
        val device = permissionDevice ?: return@LaunchedEffect
        if (device.deviceId == selectedUsb?.deviceId && !connected && !busy) {
            connect(createTransport(device), "USB: " + device.deviceName, listOf("ATE0", "ATI", "STI", "ATDPN"))
        }
    }

    BackHandler(enabled = screen != AppScreen.Home && screen != AppScreen.Accelerator) {
        if (!busy) screen = AppScreen.Home
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("OBD Lab", style = MaterialTheme.typography.headlineSmall)
        Text(state.description, color = MaterialTheme.colorScheme.secondary)
        if (screen != AppScreen.Home && screen != AppScreen.Accelerator) {
            OutlinedButton(enabled = !busy, onClick = { screen = AppScreen.Home }) { Text("Home") }
        }
        HorizontalDivider()
        when (screen) {
        AppScreen.Home -> {
            Button(onClick = { screen = AppScreen.Connection }, modifier = Modifier.fillMaxWidth()) { Text("Connection") }
            Button(onClick = { screen = AppScreen.Console }, modifier = Modifier.fillMaxWidth()) { Text("Diagnostic console") }
            Button(onClick = { screen = AppScreen.Accelerator }, modifier = Modifier.fillMaxWidth()) { Text("Accelerator diagnostics") }
        }
        AppScreen.Accelerator -> {
            AcceleratorDiagnosticsScreen(
                connection = connection,
                recording = accelerator,
                onBack = { screen = AppScreen.Home }
            )
        }
        AppScreen.Connection -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (connected) {
                Button(enabled = !busy, onClick = { scope.launch { connection.disconnect() } }) { Text("Disconnect") }
            }
            OutlinedButton(enabled = !busy && !connected, onClick = {
                scope.launch { connect(SimulationTransport(), "Simulation (synthetic data)", listOf("ATE0", "ATI")) }
            }) { Text("Connect simulator") }
            Text("Devices", style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground)
            Text("USB adapters", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = { usbDevices = refresh() }, enabled = !busy) {
                Text("Refresh USB devices")
            }
            if (usbDevices.isEmpty()) Text("No USB devices detected")
            usbDevices.forEach { device ->
                OutlinedButton(enabled = !busy && !connected, onClick = {
                    selectedUsb = device
                    requestPermission(device)
                }) {
                    Text("Connect USB " + device.vendorId.toString(16) + ":" + device.productId.toString(16))
                }
            }
            permissionMessage?.let { Text(it) }
            HorizontalDivider()
            Text("Bluetooth Classic", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(enabled = !busy, onClick = {
                if (Build.VERSION.SDK_INT >= 31 &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                else refreshBluetooth()
            }) { Text("Show paired devices") }
            if (btMessage.isNotEmpty()) Text(btMessage)
            paired.forEach { device ->
                @SuppressLint("MissingPermission")
                val label = (device.name ?: "Unnamed") + " (" + device.address + ")"
                OutlinedButton(enabled = !busy && !connected, onClick = {
                    scope.launch { connect(BluetoothObdTransport(device), "Bluetooth: " + label, listOf("ATE0", "ATI", "ATDPN")) }
                }) { Text("Connect " + label) }
            }
        }
        AppScreen.Console -> {
            Text("Diagnostic Console", style = MaterialTheme.typography.titleMedium)
            if (!connected) Text("Connect an adapter or simulator to send commands.")
            HorizontalDivider()
            val listState = rememberLazyListState()
            LaunchedEffect(entries.size) {
                if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    val color = when (entry.kind) {
                        "command" -> MaterialTheme.colorScheme.primary
                        "error" -> MaterialTheme.colorScheme.error
                        "status" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onBackground
                    }
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("${entry.timestamp} ${entry.kind.uppercase()}", style = MaterialTheme.typography.labelSmall, color = color)
                        Text(entry.value.replace("\r\n", "\n").replace('\r', '\n'), color = color, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            OutlinedButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("OBD diagnostic log", entries.joinToString("\n") { "${it.timestamp} ${it.kind}: ${it.value}" }))
            }, enabled = entries.isNotEmpty()) { Text("Copy log") }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiagnosticCommands.presets.forEach { cmd ->
                    OutlinedButton(enabled = connected && !busy, onClick = { send(cmd) }) {
                        Text(cmd)
                    }
                }
            }
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                label = { Text("Adapter command") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(commandText.trim()) }),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = connected && !busy && commandText.isNotBlank(),
                onClick = { send(commandText.trim()) },
                modifier = Modifier.align(Alignment.End)
            ) { Text(if (busy) "Working…" else "Send") }
        }
        }
    }
}

private enum class AppScreen { Home, Connection, Console, Accelerator }
