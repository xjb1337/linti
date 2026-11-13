package com.xjb.linti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizCountdownTest {

    @Test
    fun `remaining is computed from wall clock`() {
        val startedAt = 1_000_000L
        val now = startedAt + 25 * 60_000L
        val state = QuizCountdown.state(startedAt, 60, now)

        assertFalse(state.unlimited)
        assertEquals(35 * 60_000L, state.remainingMillis)
        assertEquals("35:00", state.display)
        assertFalse(state.warning)
        assertFalse(state.expired)
    }

    @Test
    fun `warning appears at five minutes and less`() {
        val startedAt = 0L
        val now = 56 * 60_000L
        val state = QuizCountdown.state(startedAt, 60, now)

        assertEquals("04:00", state.display)
        assertTrue(state.warning)
        assertFalse(state.expired)
    }

    @Test
    fun `expired triggers auto submit`() {
        val startedAt = 0L
        val now = 60 * 60_000L + 1L
        val state = QuizCountdown.state(startedAt, 60, now)

        assertEquals(0L, state.remainingMillis)
        assertEquals("00:00", state.display)
        assertTrue(state.expired)
        assertFalse(state.warning)
    }

    @Test
    fun `zero duration is unlimited`() {
        val state = QuizCountdown.state(5000L, 0, 999_999L)

        assertTrue(state.unlimited)
        assertEquals(0L, state.remainingMillis)
        assertEquals("", state.display)
        assertFalse(state.warning)
        assertFalse(state.expired)
    }

    @Test
    fun `injected clock is the source of truth`() {
        var now = 0L
        val clock = QuizClock { now }
        val startedAt = 0L
        assertEquals(60 * 60_000L, QuizCountdown.state(startedAt, 60, clock.nowMillis()).remainingMillis)
        now = 59 * 60_000L
        assertEquals(60_000L, QuizCountdown.state(startedAt, 60, clock.nowMillis()).remainingMillis)
    }
}
