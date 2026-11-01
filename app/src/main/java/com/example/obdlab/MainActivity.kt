package com.example.obdlab

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                UsbTerminal(
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
private fun UsbTerminal(
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
    var transport by remember { mutableStateOf<UsbObdTransport?>(null) }
    var busy by remember { mutableStateOf(false) }
    var log by remember { mutableStateOf("Connect a USB OBD adapter, then tap Refresh.\n") }
    var customCommand by remember { mutableStateOf("ATI") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(permissionDevice) {
        val device = permissionDevice ?: return@LaunchedEffect
        if (device.deviceId != selected?.deviceId) return@LaunchedEffect
        busy = true
        val candidate = createTransport(device)
        try {
            candidate.connect()
            transport = candidate
            session = ElmSession(candidate)
            log += "Connected to USB device ${device.deviceName} at 38400 baud\n"
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
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("OBD Lab — USB terminal", style = MaterialTheme.typography.headlineSmall)
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
        Text(if (session == null) "Disconnected" else "Connected")
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
        OutlinedTextField(
            value = customCommand,
            onValueChange = { customCommand = it },
            label = { Text("Adapter command") },
            singleLine = true,
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
                        transport = null
                        busy = false
                    }
                }
            }) { Text("Disconnect") }
        }
        Text("Responses", style = MaterialTheme.typography.titleMedium)
        Text(
            log,
            modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
