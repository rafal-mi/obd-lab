package com.example.obdlab.obd

/**
 * Decode a successful OBD-II Mode 01 PID 0C response (41 0C A B).
 * Accepts plain hex bytes or common CAN header prefixes; ignores adapter chatter.
 * Multiple ECU responses are possible; returns the first valid response.
 */
object EngineRpm {
    fun decode(response: String): Int? {
        for (line in response.lines()) {
            val normalized = line.trim().uppercase()
            if (normalized.contains("NO DATA") || normalized.contains("UNABLE TO CONNECT")) continue
            // Support both spaced and compact byte output, optionally preceded by a CAN ID.
            val tokens = normalized.split(Regex("\\s+")).filter { it.isNotBlank() }
            val payload = tokens.filter { it.matches(Regex("[0-9A-F]+")) }
                .joinToString("")
            // Search the response marker and extract exactly two data bytes.
            val index = payload.indexOf("410C")
            if (index >= 0 && payload.length >= index + 8) {
                val a = payload.substring(index + 4, index + 6).toIntOrNull(16) ?: continue
                val b = payload.substring(index + 6, index + 8).toIntOrNull(16) ?: continue
                return (256 * a + b) / 4
            }
        }
        return null
    }
}
