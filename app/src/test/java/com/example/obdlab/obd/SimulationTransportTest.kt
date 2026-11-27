package com.example.obdlab.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SimulationTransportTest {
    @Test fun discoversAndSamplesSyntheticPids() = runBlocking {
        val manager = DiagnosticSessionManager()
        try {
            manager.connect(SimulationTransport(), "Simulation", listOf("ATE0"))
            assertTrue(manager.state.value is DiagnosticSessionManager.State.Connected)
            manager.exclusive {
                assertTrue(manager.busy.value)
                assertEquals(setOf(0x0C, 0x20), AcceleratorDiagnostics.supported(command("0100"), 0))
                assertEquals(setOf(0x40), AcceleratorDiagnostics.supported(command("0120"), 0x20))
                assertEquals(setOf(0x49, 0x4A, 0x5A), AcceleratorDiagnostics.supported(command("0140"), 0x40))
                assertNotNull(AcceleratorDiagnostics.sample(command("0149"), AcceleratorPid.D, 0).value)
            }
            assertFalse(manager.busy.value)
            manager.disconnect()
            assertEquals(DiagnosticSessionManager.State.Disconnected, manager.state.value)
        } finally { manager.close() }
    }

    @Test fun excludesConcurrentOperations() = runBlocking {
        val manager = DiagnosticSessionManager()
        try {
            manager.connect(SimulationTransport(), "Simulation", emptyList())
            manager.exclusive {
                try {
                    manager.exclusive { command("ATI") }
                    fail("Concurrent operation accepted")
                } catch (_: IllegalStateException) { }
                assertEquals("ELM327 SIMULATION\r", command("ATI"))
            }
        } finally { manager.close() }
    }
}
