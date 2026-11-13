package com.xjb.linti.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExamSubmissionCoordinatorTest {
    @Test
    fun `simultaneous requests and later repeats use one committed attempt`() = runTest {
        val coordinator = ExamSubmissionCoordinator(this)
        val release = CompletableDeferred<Unit>()
        var writes = 0
        val first = coordinator.submit(1) { markCommitted ->
            writes++
            release.await()
            markCommitted()
        }
        runCurrent()
        val duplicates = List(10) { coordinator.submit(1) { writes++ } }
        duplicates.forEach { assertSame(first, it) }
        assertEquals(1, writes)

        release.complete(Unit)
        first.join()
        assertSame(first, coordinator.submit(1) { writes++ })
        runCurrent()
        assertEquals(1, writes)
    }

    @Test
    fun `failed save releases the attempt for a later retry`() = runTest {
        val coordinator = ExamSubmissionCoordinator(this)
        var attempts = 0
        var failures = 0
        val first = coordinator.submit(2) { _ ->
            try {
                withContext(NonCancellable) {
                    attempts++
                    throw IllegalStateException("save failed")
                }
            } catch (_: IllegalStateException) {
                failures++
            }
        }
        first.join()
        val retry = coordinator.submit(2) { markCommitted ->
            attempts++
            markCommitted()
        }
        retry.join()

        assertEquals(2, attempts)
        assertEquals(1, failures)
        assertSame(retry, coordinator.submit(2) { attempts++ })
    }

    @Test
    fun `cancelling the requesting screen does not cancel its save`() = runTest {
        val owner = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val coordinator = ExamSubmissionCoordinator(owner)
        val release = CompletableDeferred<Unit>()
        var writes = 0
        var published = false
        val screen = launch {
            coordinator.submit(3) { markCommitted ->
                withContext(NonCancellable) {
                    release.await()
                    writes++
                    markCommitted()
                    published = true
                }
            }.join()
        }
        runCurrent()
        screen.cancel()
        release.complete(Unit)
        runCurrent()

        assertEquals(1, writes)
        assertTrue(published)
        coordinator.submit(3) { writes++ }.join()
        assertEquals(1, writes)
        owner.cancel()
    }

    @Test
    fun `cancellation after commit still publishes and cannot start a second save`() = runTest {
        val parent = SupervisorJob()
        val owner = CoroutineScope(parent + StandardTestDispatcher(testScheduler))
        val coordinator = ExamSubmissionCoordinator(owner)
        val releasePublication = CompletableDeferred<Unit>()
        var writes = 0
        var published = false
        val first = coordinator.submit(4) { markCommitted ->
            withContext(NonCancellable) {
                writes++
                markCommitted()
                releasePublication.await()
                published = true
            }
        }
        runCurrent()
        parent.cancel()
        releasePublication.complete(Unit)
        runCurrent()

        assertEquals(1, writes)
        assertTrue(published)
        assertSame(first, coordinator.submit(4) { writes++ })
        assertEquals(1, writes)
    }

    @Test
    fun `new session is independent while an old session finishes`() = runTest {
        val coordinator = ExamSubmissionCoordinator(this)
        val releaseOld = CompletableDeferred<Unit>()
        var oldWrites = 0
        var newWrites = 0
        val old = coordinator.submit(5) { markCommitted ->
            oldWrites++
            releaseOld.await()
            markCommitted()
        }
        runCurrent()
        val current = coordinator.submit(6) { markCommitted ->
            newWrites++
            markCommitted()
        }
        current.join()
        releaseOld.complete(Unit)
        old.join()

        assertEquals(1, oldWrites)
        assertEquals(1, newWrites)
        assertSame(current, coordinator.submit(6) { newWrites++ })
    }

    @Test
    fun `navigation is consumed once and only by its matching session`() {
        val event = ExamNavigationEvent()
        event.publish(7)

        assertFalse(event.consume(token = 7, currentSessionToken = 8))
        assertFalse(event.consume(token = 8, currentSessionToken = 8))
        assertEquals(7L, event.pending.value)
        assertTrue(event.consume(token = 7, currentSessionToken = 7))
        assertFalse(event.consume(token = 7, currentSessionToken = 7))
        assertEquals(null, event.pending.value)
    }
}
