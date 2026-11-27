package com.example.obdlab.obd

import org.junit.Assert.*
import org.junit.Test

class DiagnosticTest {
    @Test fun decodesCodes() {
        assertEquals("C1750", Dtc.decode(0x57, 0x50))
        assertEquals("P0133", Dtc.decode(0x01, 0x33))
        assertEquals("U1900", Dtc.decode(0xD9, 0x00))
        assertEquals("B0001", Dtc.decode(0x80, 0x01))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidByte() { Dtc.decode(256, 0) }

    @Test fun allowsReadsButNotWrites() {
        assertTrue(DiagnosticCommands.isAllowed("03"))
        assertTrue(DiagnosticCommands.isAllowed("atsh720"))
        assertTrue(DiagnosticCommands.isAllowed("0149"))
        assertFalse(DiagnosticCommands.isAllowed("04"))
        assertFalse(DiagnosticCommands.isAllowed("14FFFFFF"))
        assertFalse(DiagnosticCommands.isAllowed("03\r04"))
        assertFalse(DiagnosticCommands.isAllowed("ATMA"))
    }
}
