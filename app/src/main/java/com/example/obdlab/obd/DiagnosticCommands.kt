package com.example.obdlab.obd

import java.util.Locale

/** Explicit allowlist: no ECU writes, resets, actuator tests or DTC clearing. */
object DiagnosticCommands {
    val presets = listOf("ATI", "ATZ", "ATE0", "ATL0", "ATS1", "ATH1", "ATSP0",
        "0100", "ATDP", "ATDPN", "03", "07", "0A", "010C", "0140", "0149", "014A", "014B", "015A")

    fun isAllowed(command: String): Boolean {
        val normalized = command.trim().uppercase(Locale.ROOT)
        return normalized in presets || normalized == "STI" ||
            Regex("01[0-9A-F]{2}").matches(normalized) ||
            Regex("ATSH[0-9A-F]{3}").matches(normalized) ||
            Regex("ATCRA[0-9A-F]{3}").matches(normalized)
    }
}
