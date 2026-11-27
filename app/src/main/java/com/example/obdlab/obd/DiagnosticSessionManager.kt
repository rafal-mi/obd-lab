package com.example.obdlab.obd

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

/** Owns the transport and reserves it for a complete diagnostic operation. */
class DiagnosticSessionManager {
    sealed class State(val description: String) {
        object Disconnected : State("Disconnected")
        data class Connecting(val label: String) : State("Connecting: $label")
        data class Connected(val label: String) : State("Adapter connected: $label")
        data class Lost(val reason: String) : State("Connection lost: $reason")
    }

    private val lock = Mutex()
    private val _state = MutableStateFlow<State>(State.Disconnected)
    val state = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private var transport: ObdTransport? = null
    private var session: ElmSession? = null
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var closed = false

    private suspend fun <T> reserved(block: suspend () -> T): T {
        check(!closed) { "Session manager closed" }
        check(lock.tryLock()) { "Another diagnostic operation is running" }
        _busy.value = true
        try { return block() }
        finally { _busy.value = false; lock.unlock() }
    }

    suspend fun connect(candidate: ObdTransport, label: String, commands: List<String>,
                        log: (String, String) -> Unit = { _, _ -> }) = reserved {
        check(transport == null) { "Disconnect first" }
        _state.value = State.Connecting(label)
        transport = candidate
        try {
            candidate.connect()
            val opened = ElmSession(candidate)
            for (cmd in commands) log(cmd, opened.command(cmd))
            session = opened
            _state.value = State.Connected(label)
        } catch (e: Exception) {
            clear(State.Lost(e.message ?: "Connection failed"))
            throw e
        }
    }

    suspend fun <T> exclusive(block: suspend ElmSession.() -> T): T = reserved {
        val opened = checkNotNull(session) { "No adapter connected" }
        try { opened.block() }
        catch (e: Exception) {
            clear(State.Lost(e.message ?: "Diagnostic interrupted"))
            throw e
        }
    }

    suspend fun disconnect() = reserved { clear(State.Disconnected) }

    private suspend fun clear(next: State) {
        val previous = transport
        session = null
        transport = null
        _state.value = next
        withContext(NonCancellable) { runCatching { previous?.disconnect() } }
    }

    /** Activity teardown: close the underlying socket even if a read is blocked. */
    fun close() {
        closed = true
        val previous = transport
        transport = null
        session = null
        _state.value = State.Disconnected
        cleanup.launch { try { previous?.disconnect() } finally { cleanup.cancel() } }
    }
}
