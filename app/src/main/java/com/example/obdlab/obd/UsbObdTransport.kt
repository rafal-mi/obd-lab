package com.example.obdlab.obd

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * USB Host serial transport. USB permission must be granted before connect().
 * The verified VSCAN STN2120 uses an FTDI FT230X (0403:6015).
 */
class UsbObdTransport(
    private val context: Context,
    private val device: UsbDevice,
    private val baudRate: Int = 38400,
) : ObdTransport {
    private var port: UsbSerialPort? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        check(manager.hasPermission(device)) { "USB device permission not granted" }
        val driver = UsbSerialProber.getDefaultProber().probeDevice(device)
            ?: error("Unsupported USB serial device")
        val connection = manager.openDevice(device) ?: error("Unable to open USB device")
        val candidate = driver.ports.first()
        try {
            candidate.open(connection)
            candidate.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port = candidate
        } catch (error: Exception) {
            candidate.close()
            throw error
        }
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        checkNotNull(port) { "Not connected" }.write(bytes, IO_TIMEOUT_MS)
    }

    override suspend fun read(maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        require(maxBytes > 0)
        val buffer = ByteArray(maxBytes)
        val count = checkNotNull(port) { "Not connected" }.read(buffer, IO_TIMEOUT_MS)
        if (count == 0) throw java.net.SocketTimeoutException("USB serial read timed out")
        buffer.copyOf(count)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        port?.close()
        port = null
    }

    companion object {
        private const val IO_TIMEOUT_MS = 3000
    }
}
