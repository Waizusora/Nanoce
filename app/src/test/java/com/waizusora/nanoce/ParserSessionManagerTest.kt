package com.waizusora.nanoce

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque
import java.util.concurrent.Executor

class ParserSessionManagerTest {
    private class QueueExecutor : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { tasks.addLast(command) }
        fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
    }

    private class FakeBackend : ParserSessionBackend {
        private var nextHandle = 1L
        val active = mutableSetOf<Long>()
        val destroyed = mutableListOf<Long>()
        val updates = mutableListOf<Pair<Long, String>>()
        override fun create(language: String): Long = nextHandle++.also(active::add)
        override fun update(handle: Long, source: String): String? {
            check(handle in active) { "update used a destroyed parser handle" }
            updates += handle to source
            return "$handle:$source"
        }
        override fun destroy(handle: Long) {
            check(active.remove(handle)) { "parser handle destroyed more than once" }
            destroyed += handle
        }
    }

    @Test fun switchingTabsKeepsIndependentParserSessions() {
        val queue = QueueExecutor()
        val backend = FakeBackend()
        val results = mutableListOf<String?>()
        val manager = ParserSessionManager(backend, { it() }, queue)
        manager.request("doc-a", "rust", "fn a() {}") { results.add(it) }
        manager.request("doc-b", "json", "{\"b\": true}") { results.add(it) }
        manager.request("doc-a", "rust", "fn a() { 1 }") { results.add(it) }
        queue.drain()
        assertEquals(2, backend.active.size)
        assertEquals(3, backend.updates.size)
        assertEquals(3, results.size)
        assertTrue(results.all { it != null })
        manager.dispose()
        queue.drain()
        assertTrue(backend.active.isEmpty())
    }

    @Test fun closingDocumentInvalidatesQueuedWork() {
        val queue = QueueExecutor()
        val backend = FakeBackend()
        val results = mutableListOf<String?>()
        val manager = ParserSessionManager(backend, { it() }, queue)
        manager.request("doc-a", "json", "{}") { results.add(it) }
        manager.closeDocument("doc-a")
        queue.drain()
        assertTrue(backend.active.isEmpty())
        assertTrue(backend.updates.isEmpty())
        assertTrue(results.isEmpty())
    }

    @Test fun closingExistingDocumentDestroysItsNativeSession() {
        val queue = QueueExecutor()
        val backend = FakeBackend()
        val manager = ParserSessionManager(backend, { it() }, queue)
        manager.request("doc-a", "json", "{}") {}
        queue.drain()
        val handle = backend.active.single()
        manager.closeDocument("doc-a")
        queue.drain()
        assertFalse(handle in backend.active)
        assertEquals(listOf(handle), backend.destroyed)
    }

    @Test fun activityRecreationReleasesOldSessionsAndCreatesFreshOnes() {
        val firstQueue = QueueExecutor()
        val backend = FakeBackend()
        val firstOwner = ParserSessionManager(backend, { it() }, firstQueue)
        firstOwner.request("doc-a", "kotlin", "fun old() {}") {}
        firstQueue.drain()
        val oldHandle = backend.active.single()
        firstOwner.dispose()
        firstQueue.drain()
        assertTrue(backend.active.isEmpty())

        val secondQueue = QueueExecutor()
        val recreatedOwner = ParserSessionManager(backend, { it() }, secondQueue)
        recreatedOwner.request("doc-a", "kotlin", "fun restored() {}") {}
        secondQueue.drain()
        val newHandle = backend.active.single()
        assertNotEquals(oldHandle, newHandle)
        recreatedOwner.dispose()
        secondQueue.drain()
        assertTrue(backend.active.isEmpty())
    }

    @Test fun changingLanguageReplacesNativeSession() {
        val queue = QueueExecutor()
        val backend = FakeBackend()
        val manager = ParserSessionManager(backend, { it() }, queue)
        manager.request("doc-a", "json", "{}") {}
        queue.drain()
        val oldHandle = backend.active.single()
        manager.request("doc-a", "rust", "fn main() {}") {}
        queue.drain()
        assertEquals(1, backend.active.size)
        assertFalse(oldHandle in backend.active)
        assertEquals(1, backend.destroyed.size)
        manager.dispose()
        queue.drain()
        assertTrue(backend.active.isEmpty())
    }
}
