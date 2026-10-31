package com.example.obdlab.obd

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.Result
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ElmSessionTest {
    @Test
    fun readsFragmentedResponseUntilPrompt() {
        val fake = FakeTransport(listOf("ELM327 ", "v1.3a\r\r", ">"))
        val response = immediate { ElmSession(fake).command("ATI") }
        assertEquals("ELM327 v1.3a\r\r", response)
        assertEquals("ATI\r", fake.written)
    }

    @Test
    fun rejectsMultilineCommands() {
        val fake = FakeTransport(emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            immediate { ElmSession(fake).command("ATI\rSTI") }
        }
    }

    /** Test-only runner: fake operations never suspend. */
    private fun <T> immediate(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return checkNotNull(result).getOrThrow()
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
