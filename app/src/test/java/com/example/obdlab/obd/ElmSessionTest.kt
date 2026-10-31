package com.example.obdlab.obd

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ElmSessionTest {
    @Test
    fun readsFragmentedResponseUntilPrompt() = runBlocking {
        val fake = FakeTransport(listOf("ELM327 ", "v1.3a\r\r", ">"))
        val response = ElmSession(fake).command("ATI")
        assertEquals("ELM327 v1.3a\r\r", response)
        assertEquals("ATI\r", fake.written)
    }

    @Test
    fun rejectsMultilineCommands() = runBlocking {
        val fake = FakeTransport(emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { ElmSession(fake).command("ATI\rSTI") }
        }
    }

    private class FakeTransport(chunks: List<String>) : ObdTransport {
        private val pending = ArrayDeque(chunks)
        var written = ""
        override suspend fun connect() = Unit
        override suspend fun write(bytes: ByteArray) {
            written += bytes.toString(Charsets.US_ASCII)
        }
        override suspend fun read(maxBytes: Int): ByteArray =
            if (pending.isEmpty()) byteArrayOf() else pending.removeFirst().toByteArray()
        override suspend fun disconnect() = Unit
    }
}
