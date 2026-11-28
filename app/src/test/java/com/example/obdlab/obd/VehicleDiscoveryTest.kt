package com.example.obdlab.obd
import org.junit.Assert.*
import org.junit.Test
class VehicleDiscoveryTest {
    @Test fun focusFixture() {
        val a = VehicleDiscovery.parseCan("SEARCHING...\r7E8 06 41 00 98 3B 00 11",0).single()
        assertEquals(setOf(1,4,5,0x0B,0x0C,0x0D,0x0F,0x10,0x1C,0x20),a.pids)
        assertEquals(setOf(0x21,0x23),VehicleDiscovery.parseCan("7E8 06 41 20 A0 00 00 00",0x20).single().pids)
    }
    @Test fun separateResponders() {
        assertEquals(2,VehicleDiscovery.parseCan("7E8 06 41 00 98 3B 00 11\r7E9 06 41 00 00 00 00 00",0).size)
        assertTrue(VehicleDiscovery.parseCan("41 00 98 3B 00 11",0).isEmpty())
    }
}
