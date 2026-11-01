package com.example.obdlab.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EngineRpmTest {
    @Test fun spacedBytes() {
        assertEquals(1726, EngineRpm.decode("41 0C 1A F8\r"))
    }

    @Test fun compactWithCanHeader() {
        assertEquals(1726, EngineRpm.decode("7E8 04 41 0C 1A F8\r"))
    }

    @Test fun noVehicleResponse() {
        assertNull(EngineRpm.decode("NO DATA\r"))
    }
}
