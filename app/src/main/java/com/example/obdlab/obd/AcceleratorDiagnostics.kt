package com.example.obdlab.obd

import java.util.Locale

enum class AcceleratorPid(val id: Int, val label: String) {
    D(0x49, "Position D"), E(0x4A, "Position E"),
    F(0x4B, "Position F"), Relative(0x5A, "Relative position");

    val hex: String get() = "%02X".format(Locale.ROOT, id)
    val command: String get() = "01" + hex
}

data class DiagnosticSample(
    val elapsedMs: Long,
    val pid: AcceleratorPid,
    val rawResponse: String,
    val value: Int?
) {
    val percent: Double? get() = value?.let { 100.0 * it / 255.0 }
}

/**
 * Parses ELM automatic-formatted responses with headers disabled.
 * Multiple responders are deliberately rejected rather than mixed.
 */
object AcceleratorDiagnostics {
    fun payload(response: String, pid: Int, count: Int): List<Int>? {
        val expected = "%02X".format(Locale.ROOT, pid)
        val lines = response.lineSequence().map { it.trim().replace(" ", "").uppercase(Locale.ROOT) }
            .filter { it.startsWith("41$expected") }.toList()
        val line = lines.singleOrNull() ?: return null
        if (line.length != 4 + count * 2 || !line.all { it in "0123456789ABCDEF" }) return null
        return line.drop(4).chunked(2).map { it.toInt(16) }
    }

    fun supported(response: String, base: Int): Set<Int>? {
        val bytes = payload(response, base, 4) ?: return null
        return (0 until 32).filter { offset ->
            bytes[offset / 8] and (0x80 shr (offset % 8)) != 0
        }.map { base + it + 1 }.toSet()
    }

    fun sample(response: String, pid: AcceleratorPid, elapsedMs: Long) =
        DiagnosticSample(elapsedMs, pid, response, payload(response, pid.id, 1)?.single())

    fun csv(samples: List<DiagnosticSample>, source: String? = null): String = buildString {
        appendLine("elapsed_ms,pid,raw_byte_hex,value_percent,raw_response" + if (source != null) ",source" else "")
        for (sample in samples) {
            val raw = sample.value?.let { "%02X".format(Locale.ROOT, it) }.orEmpty()
            val percent = sample.percent?.let { "%.4f".format(Locale.ROOT, it) }.orEmpty()
            val response = sample.rawResponse.replace("\"", "\"\"")
            val sourceColumn = source?.let { ",\"" + it.replace("\"", "\"\"") + "\"" }.orEmpty()
            appendLine("${sample.elapsedMs},${sample.pid.hex},$raw,$percent,\"$response\"$sourceColumn")
        }
    }
}
