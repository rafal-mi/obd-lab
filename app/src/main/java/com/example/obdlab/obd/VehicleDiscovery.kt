package com.example.obdlab.obd

import java.util.Locale

/** ISO-TP single-frame CAN bitmap responses; never merge different responders. */
object VehicleDiscovery {
    data class Page(val responder: String, val base: Int, val bitmap: String, val pids: Set<Int>)
    fun parseCan(response: String, base: Int): List<Page> {
        val expected = "%02X".format(Locale.ROOT, base)
        return response.lineSequence().mapNotNull { line ->
            val t = line.trim().uppercase(Locale.ROOT).split(Regex("\\s+"))
            if (t.size !in 8..9 || !t[0].matches(Regex("[0-9A-F]{3}|[0-9A-F]{8}"))) return@mapNotNull null
            if (t.drop(1).any { !it.matches(Regex("[0-9A-F]{2}")) }) return@mapNotNull null
            if (t[1] != "06" || t[2] != "41" || t[3] != expected) return@mapNotNull null
            val bytes = t.subList(4,8)
            val bits = bytes.map { it.toInt(16) }
            val pids = (0 until 32).filter { bits[it/8] and (0x80 shr (it%8)) != 0 }.map { base+it+1 }.toSet()
            Page(t[0], base, bytes.joinToString(" "), pids)
        }.toList().distinct()
    }
    fun hex(n: Int) = "%02X".format(Locale.ROOT,n)
    fun name(n: Int) = when(n) {
        1 -> "Monitor status"; 4 -> "Engine load"; 5 -> "Coolant temperature"
        0x0B -> "Intake manifold pressure"; 0x0C -> "Engine RPM"; 0x0D -> "Vehicle speed"
        0x0F -> "Intake air temperature"; 0x10 -> "Mass air flow"; 0x1C -> "OBD standards"
        0x21 -> "Distance with MIL on"; 0x23 -> "Fuel rail gauge pressure"
        0x49 -> "Pedal D"; 0x4A -> "Pedal E"; 0x4B -> "Pedal F"; 0x5A -> "Relative pedal"
        else -> if (n % 32 == 0) "Continuation bitmap" else "Standard PID"
    }
}
