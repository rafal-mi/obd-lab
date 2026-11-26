package com.example.obdlab.obd

import java.util.Locale

object Dtc {
    /** Decode a two-byte SAE DTC, not a complete diagnostic response. */
    fun decode(high: Int, low: Int): String {
        require(high in 0..255 && low in 0..255)
        val category = "PCBU"[(high shr 6) and 3]
        val digit = (high shr 4) and 3
        val remainder = ((high and 15) shl 8) or low
        return String.format(Locale.ROOT, "%c%d%03X", category, digit, remainder)
    }
}
