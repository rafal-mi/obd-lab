package com.example.obdlab.obd

import org.junit.Assert.*
import org.junit.Test

class AcceleratorDiagnosticsTest {
    @Test fun discoversPedalPids() {
        assertEquals(setOf(0x49, 0x4A, 0x4B, 0x5A),
            AcceleratorDiagnostics.supported("41 40 00 E0 00 40", 0x40))
        assertEquals(setOf(0x20), AcceleratorDiagnostics.supported("410000000001", 0))
    }

    @Test fun rejectsAmbiguousAndInvalidPayloads() {
        assertNull(AcceleratorDiagnostics.supported("414000E00040\r414000000000", 0x40))
        assertNull(AcceleratorDiagnostics.supported("NO DATA", 0x40))
        assertNull(AcceleratorDiagnostics.payload("41498000", 0x49, 1))
    }

    @Test fun convertsAndExports() {
        val sample = AcceleratorDiagnostics.sample("41 49 80", AcceleratorPid.D, 123)
        assertEquals(128, sample.value)
        assertEquals(100.0 * 128 / 255, sample.percent!!, 0.00001)
        assertTrue(AcceleratorDiagnostics.csv(listOf(sample)).contains("123,49,80,50.1961"))
        assertNull(AcceleratorDiagnostics.sample("NO DATA", AcceleratorPid.E, 124).value)
    }
}
