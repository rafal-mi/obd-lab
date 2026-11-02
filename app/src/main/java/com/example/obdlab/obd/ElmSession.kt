package com.example.obdlab.obd

import java.io.ByteArrayOutputStream

/**
 * Serializes adapter commands and reads until the ELM/STN '>' prompt.
 * No PID decoding or vehicle-specific assumptions live here.
 *
 * Use from one coroutine at a time; callers must provide a transport whose
 * read operation eventually returns or throws on timeout/disconnection.
 */
class ElmSession(private val transport: ObdTransport) {
    suspend fun command(command: String, maxResponseBytes: Int = 8192): String {
        require(command.isNotBlank()) { "Command must not be blank" }
        require('\r' !in command && '\n' !in command) { "One command per call" }
        require(maxResponseBytes > 0)

        transport.write((command + "\r").toByteArray(Charsets.US_ASCII))
        val response = ByteArrayOutputStream()
        while (response.size() < maxResponseBytes) {
            val chunk = transport.read(minOf(256, maxResponseBytes - response.size()))
            if (chunk.isEmpty()) throw IllegalStateException("Adapter disconnected before prompt")
            val promptIndex = chunk.indexOf('>'.code.toByte())
            if (promptIndex >= 0) {
                response.write(chunk, 0, promptIndex)
                return response.toString(Charsets.US_ASCII.name())
            }
            response.write(chunk)
        }
        throw IllegalStateException("Adapter response exceeded $maxResponseBytes bytes")
    }
}
