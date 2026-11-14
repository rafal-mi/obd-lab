package com.example.obdlab.obd

/**
 * Byte-oriented connection to a serial OBD adapter.
 *
 * Implementations (USB and Bluetooth) own their connection and must not
 * assume that one read returns an entire ELM/STN response.
 */
interface ObdTransport {
    suspend fun connect()
    suspend fun write(bytes: ByteArray)
    /** Returns up to [maxBytes] bytes; an empty array signals end of stream. */
    suspend fun read(maxBytes: Int = 256): ByteArray
    suspend fun disconnect()
}
