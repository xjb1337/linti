package com.xjb.linti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class StudyStatsCalculatorTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun attempt(
        questionId: Long,
        bankId: Long = 10L,
        answeredAt: Long = 1_000L,
        isCorrect: Boolean = true,
        sessionId: Long? = null,
    ) = AttemptStatRecord(questionId, bankId, answeredAt, isCorrect, sessionId)

    private fun legacySession(
        id: Long,
        bankId: Long? = 10L,
        answeredCount: Int = 3,
        correctCount: Int = 2,
        at: Long = 1_000L,
        answeredQuestionIds: Set<Long> = setOf(7L, 8L, 9L),
        status: String = "COMPLETED",
    ) = LegacySessionRecord(id, bankId, answeredCount, correctCount, at, answeredQuestionIds, status)

    @Test
    fun `aggregate merges attempts and un-backfilled sessions without double counting`() {
        val attempts = listOf(
            attempt(1L, answeredAt = 2_000L, isCorrect = true, sessionId = 100L),
            attempt(2L, answeredAt = 2_000L, isCorrect = false, sessionId = 100L),
            attempt(3L, answeredAt = 3_000L, isCorrect = true),
        )
        val sessions = listOf(
            legacySession(100L, answeredCount = 2, correctCount = 1),
            legacySession(101L, answeredCount = 4, correctCount = 3),
        )

        val result = StudyStatsCalculator.aggregate(
            attempts = attempts,
            sessions = sessions,
            todayStart = 0L,
            zone = zone,
        )

        assertEquals(7, result.totalAnswered)
        assertEquals(5, result.totalCorrect)
        assertEquals(2, result.sessionCount)
        assertTrue(result.accuracy > 0.0)
    }

    @Test
    fun `aggregate ignores sessions that were backfilled into attempts`() {
        val attempts = listOf(
            attempt(1L, sessionId = 100L),
            attempt(2L, sessionId = 100L),
        )
        val sessions = listOf(legacySession(100L, answeredCount = 2, correctCount = 1))

        val result = StudyStatsCalculator.aggregate(attempts, sessions, 0L, zone)

        assertEquals(2, result.totalAnswered)
        assertEquals(2, result.totalCorrect)
    }

    @Test
    fun `aggregate excludes non-completed sessions and counts today separately`() {
        val attempts = listOf(
            attempt(1L, answeredAt = 5_000L),
            attempt(2L, answeredAt = 50_000L),
        )
        val sessions = listOf(
            legacySession(200L, status = "IN_PROGRESS", answeredCount = 99, correctCount = 99),
        )

        val result = StudyStatsCalculator.aggregate(attempts, sessions, 10_000L, zone)

        assertEquals(2, result.totalAnswered)
        assertEquals(1, result.todayAnswered)
        assertEquals(0, result.sessionCount)
    }

    @Test
    fun `answeredQuestionIds unions attempts and un-backfilled sessions for the bank`() {
        val attempts = listOf(
            attempt(1L, bankId = 10L, sessionId = 100L),
            attempt(2L, bankId = 10L),
            attempt(3L, bankId = 11L),
        )
        val sessions = listOf(
            legacySession(100L, bankId = 10L, answeredQuestionIds = setOf(7L, 8L)),
            legacySession(101L, bankId = 10L, answeredQuestionIds = setOf(9L)),
            legacySession(102L, bankId = 11L, answeredQuestionIds = setOf(20L)),
        )

        val result = StudyStatsCalculator.answeredQuestionIds(attempts, sessions, 10L)

        assertEquals(setOf(1L, 2L, 9L), result)
    }
}
