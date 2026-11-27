package com.example.obdlab.obd

import java.util.Locale
import kotlin.math.cos

/** In-process synthetic ELM transport, not a real vehicle or fault diagnosis. */
class SimulationTransport : ObdTransport {
    private var connected = false
    private var response = byteArrayOf()
    private var step = 0
    private var headers = false
    override suspend fun connect() { connected = true; step = 0 }
    override suspend fun disconnect() { connected = false; response = byteArrayOf() }
    override suspend fun write(bytes: ByteArray) {
        check(connected)
        val cmd = bytes.toString(Charsets.US_ASCII).trim().uppercase(Locale.ROOT)
        val pedal = (127.5 * (1 - cos(step * Math.PI / 80))).toInt().coerceIn(0,255)
        val answer = when (cmd) {
            "ATZ" -> { headers = false; step = 0; "ELM327 SIMULATION" }
            "ATI" -> "ELM327 SIMULATION"
            "ATH1" -> { headers = true; "OK" }
            "ATH0" -> { headers = false; "OK" }
            "ATDP" -> "ISO 15765-4 (CAN 11/500) SIMULATED"
            "ATDPN" -> "A6"
            "0100" -> "41 00 00 10 00 01"
            "0120" -> "41 20 00 00 00 01"
            "0140" -> "41 40 00 C0 00 40"
            "0149" -> "41 49 %02X".format(Locale.ROOT, pedal)
            "014A" -> "41 4A %02X".format(Locale.ROOT, pedal / 2)
            "015A" -> { step++; "41 5A %02X".format(Locale.ROOT, pedal) }
            "010C" -> "41 0C 00 00"
            "03" -> "43 00"
            "07" -> "47 00"
            "0A" -> "4A 00"
            else -> if (cmd.startsWith("AT")) "OK" else "NO DATA"
        }
        val payload = if (headers && cmd.matches(Regex("[0-9A-F]+")) && answer != "NO DATA")
            "7E8 %02X ".format(Locale.ROOT, answer.split(" ").size) + answer else answer
        response = (payload + "\r>").toByteArray(Charsets.US_ASCII)
    }
    override suspend fun read(maxBytes: Int): ByteArray {
        check(connected)
        val count = minOf(maxBytes, response.size)
        val chunk = response.copyOfRange(0, count)
        response = response.copyOfRange(count, response.size)
        return chunk
    }
}
