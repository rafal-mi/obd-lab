package com.example.obdlab.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/**
 * Bluetooth Classic SPP transport for paired ELM327 adapters.
 * Caller must obtain BLUETOOTH_CONNECT runtime permission on Android 12+.
 */
@SuppressLint("MissingPermission")
class BluetoothObdTransport(private val device: BluetoothDevice) : ObdTransport {
    private var socket: BluetoothSocket? = null

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val candidate = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            candidate.connect()
            socket = candidate
        } catch (error: IOException) {
            candidate.close()
            throw error
        }
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        checkNotNull(socket) { "Not connected" }.outputStream.write(bytes)
    }

    override suspend fun read(maxBytes: Int): ByteArray = withContext(Dispatchers.IO) {
        require(maxBytes > 0)
        val buffer = ByteArray(maxBytes)
        val count = checkNotNull(socket) { "Not connected" }.inputStream.read(buffer)
        if (count < 0) byteArrayOf() else buffer.copyOf(count)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        socket?.close()
        socket = null
    }

    companion object {
        private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
