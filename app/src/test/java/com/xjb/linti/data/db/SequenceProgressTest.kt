package com.xjb.linti.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SequenceProgressTest {

    private fun progress(roundStartedAt: Long = 0L): LegacyProgressRow =
        LegacyProgressRow(bankId = 1L, mode = SessionBackfill.SEQUENCE, roundStartedAt = roundStartedAt)

    private fun seed(questionId: Long, answeredAt: Long): AttemptSeed = AttemptSeed(
        questionId = questionId,
        bankId = 1L,
        mode = SessionBackfill.SEQUENCE,
        answer = "A",
        isCorrect = true,
        answeredAt = answeredAt,
    )

    private fun sessionOneAndTwo(): List<AttemptSeed> {
        val firstRound = (1L..50L).map { seed(it, 100L + it) }
        val secondRound = (51L..60L).map { seed(it, 200L + it) }
        val repeated = seed(3L, 999L)
        return firstRound + secondRound + repeated
    }

    @Test
    fun `two sessions accumulate sixty distinct answered questions`() {
        val attempts = sessionOneAndTwo()
        val existing = (1L..60L).toSet()
        val counts = SessionBackfill.recomputeSequenceCounts(listOf(progress()), attempts, existing)

        assertEquals(listOf(1L to 60), counts)
    }

    @Test
    fun `answering the same question twice counts once`() {
        val attempts = listOf(seed(7L, 100L), seed(7L, 200L), seed(8L, 300L))
        val counts = SessionBackfill.recomputeSequenceCounts(
            listOf(progress()), attempts, setOf(7L, 8L),
        )

        assertEquals(listOf(1L to 2), counts)
    }

    @Test
    fun `deleted questions are excluded from the count`() {
        val attempts = sessionOneAndTwo()
        val existing = (1L..59L).toSet()
        val counts = SessionBackfill.recomputeSequenceCounts(listOf(progress()), attempts, existing)

        assertEquals(listOf(1L to 59), counts)
    }

    @Test
    fun `restarting a round starts from zero while old attempts remain`() {
        val attempts = sessionOneAndTwo()
        val existing = (1L..60L).toSet()
        val restart = 1_000L
        val counts = SessionBackfill.recomputeSequenceCounts(
            listOf(progress(roundStartedAt = restart)), attempts, existing,
        )

        assertEquals(listOf(1L to 0), counts)
        assertEquals(61, attempts.size)
        assertTrue(attempts.all { it.answeredAt < restart })
    }

    @Test
    fun `latest per question keeps the most recent attempt`() {
        val attempts = sessionOneAndTwo()
        val latest = SessionBackfill.latestPerQuestion(attempts)

        assertEquals(60, latest.size)
        assertEquals(999L, latest.getValue(3L).answeredAt)
        assertEquals(251L, latest.getValue(51L).answeredAt)
    }
}
