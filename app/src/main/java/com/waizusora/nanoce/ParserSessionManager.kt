package com.waizusora.nanoce

import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

internal interface ParserSessionBackend {
    fun create(language: String): Long
    fun update(handle: Long, source: String): String?
    fun destroy(handle: Long)
}

/** Owns native parser handles on one serial worker. */
internal class ParserSessionManager(
    private val backend: ParserSessionBackend,
    private val dispatchResult: ((() -> Unit) -> Unit),
    worker: Executor? = null
) {
    private data class Session(val handle: Long, val language: String)

    private val ownedWorker: ExecutorService? =
        if (worker == null) Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "nanoce-parser").apply { isDaemon = true }
        } else null
    private val workerExecutor: Executor = worker ?: requireNotNull(ownedWorker)
    private val lock = Any()
    private val generations = mutableMapOf<String, Long>()
    private val sessions = mutableMapOf<String, Session>() // worker-thread confined
    private var disposed = false

    fun request(documentKey: String, language: String, source: String, onResult: (String?) -> Unit) {
        synchronized(lock) {
            if (disposed) return
            val generation = generations[documentKey] ?: 0L
            // Queue insertion is atomic with the lifecycle generation check.
            execute {
                if (!isCurrent(documentKey, generation)) return@execute
                var session = sessions[documentKey]
                if (session != null && session.language != language) {
                    runCatching { backend.destroy(session.handle) }
                    sessions.remove(documentKey)
                    session = null
                }
                if (session == null) {
                    val handle = runCatching { backend.create(language) }.getOrDefault(0L)
                    if (handle == 0L) {
                        dispatchResult { if (isCurrent(documentKey, generation)) onResult(null) }
                        return@execute
                    }
                    session = Session(handle, language)
                    sessions[documentKey] = session
                }
                val result = runCatching {
                    backend.update(requireNotNull(session).handle, source)
                }.getOrNull()
                dispatchResult { if (isCurrent(documentKey, generation)) onResult(result) }
            }
        }
    }

    /** Invalidates queued results and destroys the handle after earlier updates. */
    fun closeDocument(documentKey: String) {
        synchronized(lock) {
            if (disposed) return
            generations[documentKey] = (generations[documentKey] ?: 0L) + 1L
            execute {
                sessions.remove(documentKey)?.let { session ->
                    runCatching { backend.destroy(session.handle) }
                }
            }
        }
    }

    fun closeDocuments(documentKeys: Collection<String>) {
        synchronized(lock) {
            if (disposed) return
            documentKeys.forEach { key -> generations[key] = (generations[key] ?: 0L) + 1L }
            execute {
                sessions.values.forEach { session -> runCatching { backend.destroy(session.handle) } }
                sessions.clear()
            }
        }
    }

    /** Idempotent; queued updates finish before remaining native handles are released. */
    fun dispose() {
        synchronized(lock) {
            if (disposed) return
            disposed = true
            generations.keys.toList().forEach { key -> generations[key] = (generations[key] ?: 0L) + 1L }
            execute(force = true) {
                sessions.values.forEach { session -> runCatching { backend.destroy(session.handle) } }
                sessions.clear()
            }
        }
        ownedWorker?.shutdown()
    }

    private fun isCurrent(documentKey: String, generation: Long): Boolean =
        synchronized(lock) { !disposed && (generations[documentKey] ?: 0L) == generation }

    private fun execute(force: Boolean = false, task: () -> Unit) {
        if (!force && synchronized(lock) { disposed }) return
        try {
            workerExecutor.execute(task)
        } catch (_: RejectedExecutionException) {
            // A lifecycle race must not crash the editor UI.
        }
    }
}

internal class NativeParserSessionBackend(private val bridge: NativeBridge) : ParserSessionBackend {
    override fun create(language: String): Long = bridge.nativeCreateParserSession(language)
    override fun update(handle: Long, source: String): String? =
        bridge.nativeUpdateParserSession(handle, source)
    override fun destroy(handle: Long) = bridge.nativeDestroyParserSession(handle)
}
