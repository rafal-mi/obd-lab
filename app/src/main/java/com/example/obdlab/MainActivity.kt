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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.obdlab.obd.BluetoothObdTransport
import com.example.obdlab.obd.ObdTransport
import com.example.obdlab.obd.EngineRpm
import com.example.obdlab.obd.ElmSession
import com.example.obdlab.obd.UsbObdTransport
import com.example.obdlab.ui.theme.OBDLabTheme
import kotlinx.coroutines.launch

private const val USB_PERMISSION_ACTION = "com.example.obdlab.USB_PERMISSION"

class MainActivity : ComponentActivity() {
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
        super.onDestroy()
    }
}

@Composable
private fun ObdTerminal(
    devices: List<UsbDevice>,
    permissionDevice: UsbDevice?,
    permissionMessage: String?,
    refresh: () -> List<UsbDevice>,
    requestPermission: (UsbDevice) -> Unit,
    createTransport: (UsbDevice) -> UsbObdTransport,
) {
    var available by remember { mutableStateOf(devices) }
    var selected by remember { mutableStateOf<UsbDevice?>(null) }
    var session by remember { mutableStateOf<ElmSession?>(null) }
    var transport by remember { mutableStateOf<ObdTransport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("Connect a USB or paired Bluetooth OBD adapter.\n") }
    var customCommand by remember { mutableStateOf("ATI") }
    var rpm by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val btManager = remember { context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager }
    var paired by remember { mutableStateOf(emptyList<android.bluetooth.BluetoothDevice>()) }
    var btMessage by remember { mutableStateOf("") }
    var connectionLabel by remember { mutableStateOf("Disconnected") }

    @SuppressLint("MissingPermission")
    fun refreshBluetooth() {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            btMessage = "Bluetooth permission required"
            return
        }
        val adapter = btManager.adapter
        paired = if (adapter?.isEnabled == true) adapter.bondedDevices.orEmpty().toList() else emptyList()
        btMessage = if (adapter == null) "Bluetooth unavailable"
                    else if (!adapter.isEnabled) "Enable Bluetooth in Android settings"
                    else if (paired.isEmpty()) "No paired Bluetooth devices" else ""
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) refreshBluetooth() else btMessage = "Bluetooth permission denied"
    }


    LaunchedEffect(permissionDevice) {
        val device = permissionDevice ?: return@LaunchedEffect
        if (device.deviceId != selected?.deviceId) return@LaunchedEffect
        busy = true
        val candidate = createTransport(device)
        try {
            candidate.connect()
            val newSession = ElmSession(candidate)
            log += "Connected to USB device ${device.deviceName} at 38400 baud\n"
            for (cmd in listOf("ATE0", "ATI", "STI", "ATDPN")) {
                val result = newSession.command(cmd)
                log += "> $cmd\n${result.trim()}\n"
            }
            transport = candidate
            session = newSession
            connectionLabel = "USB connected"
        } catch (error: Exception) {
            log += "Connect failed: ${error.message}\n"
            runCatching { candidate.disconnect() }
        } finally {
            busy = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val previous = transport
            if (previous != null) scope.launch { previous.disconnect() }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("OBD Lab — diagnostic terminal", style = MaterialTheme.typography.headlineSmall)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("USB", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { available = refresh() }, enabled = !busy) {
            Text("Refresh USB devices")
        }
        if (available.isEmpty()) Text("No USB devices detected")
        available.forEach { device ->
            OutlinedButton(
                onClick = {
                    selected = device
                    requestPermission(device)
                },
                enabled = !busy && session == null
            ) {
                Text("Connect ${device.vendorId.toString(16)}:${device.productId.toString(16)} — ${device.deviceName}")
            }
        }
        if (permissionMessage != null) Text(permissionMessage)

        Text("Bluetooth Classic", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(enabled = !busy, onClick = {
            if (Build.VERSION.SDK_INT >= 31 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            else refreshBluetooth()
        }) { Text("Show paired Bluetooth devices") }
        if (btMessage.isNotEmpty()) Text(btMessage)
        paired.forEach { device ->
            @SuppressLint("MissingPermission")
            val label = (device.name ?: "Unnamed") + " (" + device.address + ")"
            OutlinedButton(enabled = session == null && !busy, onClick = {
                scope.launch {
                    busy = true
                    val candidate = BluetoothObdTransport(device)
                    try {
                        candidate.connect()
                        val newSession = ElmSession(candidate)
                        log += "Bluetooth connected: " + label + "\n"
                        for (cmd in listOf("ATE0", "ATI", "ATDPN")) {
                            val response = newSession.command(cmd)
                            log += "> " + cmd + "\n" + response.trim() + "\n"
                        }
                        transport = candidate
                        session = newSession
                        connectionLabel = "Bluetooth: " + label
                    } catch (error: Exception) {
                        log += "Bluetooth failed: " + error.message + "\n"
                        runCatching { candidate.disconnect() }
                    } finally { busy = false }
                }
            }) { Text("Connect " + label) }
        }
        Text(connectionLabel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ATI", "STI", "ATDPN").forEach { cmd ->
                Button(enabled = session != null && !busy, onClick = {
                    val current = session ?: return@Button
                    scope.launch {
                        busy = true
                        try {
                            val result = current.command(cmd)
                            log += "> $cmd\n$result\n"
                        } catch (error: Exception) {
                            log += "$cmd failed: ${error.message}\n"
                        } finally {
                            busy = false
                        }
                    }
                }) { Text(cmd) }
            }
        }
        Button(enabled = session != null && !busy, onClick = {
            val current = session ?: return@Button
            scope.launch {
                busy = true
                rpm = null
                try {
                    val result = current.command("010C")
                    log += "> 010C\n${result.trim()}\n"
                    rpm = EngineRpm.decode(result)
                    if (rpm == null) log += "No valid engine RPM response (41 0C A B). Check ignition and vehicle connection.\n"
                } catch (error: Exception) {
                    log += "010C failed: ${error.message}\n"
                } finally {
                    busy = false
                }
            }
        }) { Text("Read engine RPM (010C)") }
        if (rpm != null) Text("Engine speed: $rpm RPM", style = MaterialTheme.typography.titleMedium)
        }
        Text("Responses", style = MaterialTheme.typography.titleMedium)
        val responseScroll = rememberScrollState()
        LaunchedEffect(log) { responseScroll.animateScrollTo(responseScroll.maxValue) }
        Text(log, modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(responseScroll), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = customCommand,
            onValueChange = { customCommand = it },
            label = { Text("Adapter command") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = session != null && !busy && customCommand.isNotBlank(), onClick = {
                val current = session ?: return@Button
                val cmd = customCommand
                scope.launch {
                    busy = true
                    try { log += "> $cmd\n${current.command(cmd)}\n" }
                    catch (error: Exception) { log += "$cmd failed: ${error.message}\n" }
                    finally { busy = false }
                }
            }) { Text("Send") }
            OutlinedButton(enabled = transport != null && !busy, onClick = {
                val previous = transport ?: return@OutlinedButton
                scope.launch {
                    busy = true
                    try {
                        previous.disconnect()
                        log += "Disconnected\n"
                    } finally {
                        session = null
                        rpm = null
                        transport = null
                        connectionLabel = "Disconnected"
                        busy = false
                    }
                }
            }) { Text("Disconnect") }
        }

    }
}
