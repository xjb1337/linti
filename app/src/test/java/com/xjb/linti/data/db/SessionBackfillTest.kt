package com.xjb.linti.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionBackfillTest {

    private fun question(
        id: Long,
        type: String = "SINGLE",
        answer: String = "A",
        optionsJson: String = """[{"label":"A","text":"甲"},{"label":"B","text":"乙"}]""",
    ) = LegacyQuestionRow(
        id = id,
        bankId = 10L,
        type = type,
        answer = answer,
        optionsJson = optionsJson,
    )

    private fun session(
        id: Long,
        mode: String = "SEQUENCE",
        questionIds: String = "[1,2,3]",
        answers: String = """{"1":"A","2":"B"}""",
        startedAt: Long = 1_000L,
        endedAt: Long? = 2_000L,
    ) = LegacySessionRow(
        id = id,
        mode = mode,
        startedAt = startedAt,
        endedAt = endedAt,
        questionIdsJson = questionIds,
        answersJson = answers,
    )

    @Test
    fun `buildAttempts grades each non-empty answer and skips blank ones`() {
        val seeds = SessionBackfill.buildAttempts(
            sessions = listOf(session(100L)),
            questions = mapOf(1L to question(1L), 2L to question(2L), 3L to question(3L)),
        )

        assertEquals(2, seeds.size)
        val first = seeds.first { it.questionId == 1L }
        val second = seeds.first { it.questionId == 2L }
        assertTrue(first.isCorrect)
        assertFalse(second.isCorrect)
        assertEquals("A", first.answer)
        assertEquals("B", second.answer)
        assertEquals("SEQUENCE", first.mode)
        assertEquals(10L, first.bankId)
        assertEquals(2_000L, first.answeredAt)
        assertEquals(100L, first.sessionId)
    }

    @Test
    fun `buildAttempts skips EXAM sessions`() {
        val seeds = SessionBackfill.buildAttempts(
            sessions = listOf(session(100L, mode = "EXAM")),
            questions = mapOf(1L to question(1L), 2L to question(2L)),
        )
        assertTrue(seeds.isEmpty())
    }

    @Test
    fun `buildAttempts skips deleted questions and uses startedAt when endedAt missing`() {
        val seeds = SessionBackfill.buildAttempts(
            sessions = listOf(
                session(
                    101L,
                    questionIds = "[1,99]",
                    answers = """{"1":"A","99":"A"}""",
                    endedAt = null,
                ),
            ),
            questions = mapOf(1L to question(1L)),
        )

        assertEquals(1, seeds.size)
        assertEquals(1L, seeds.single().questionId)
        assertEquals(1_000L, seeds.single().answeredAt)
    }

    @Test
    fun `buildAttempts grades judge and fill and marks partial multi as wrong`() {
        val seeds = SessionBackfill.buildAttempts(
            sessions = listOf(
                session(
                    102L,
                    questionIds = "[1,2,3]",
                    answers = """{"1":"正确","2":"北京","3":"A"}""",
                ),
            ),
            questions = mapOf(
                1L to question(1L, type = "JUDGE", answer = "true"),
                2L to question(2L, type = "FILL", answer = "北京"),
                3L to question(3L, type = "MULTI", answer = "AC", optionsJson = """[{"label":"A","text":"甲"},{"label":"C","text":"丙"}]"""),
            ),
        )

        assertEquals(3, seeds.size)
        assertTrue(seeds.first { it.questionId == 1L }.isCorrect)
        assertTrue(seeds.first { it.questionId == 2L }.isCorrect)
        assertFalse(seeds.first { it.questionId == 3L }.isCorrect)
    }

    @Test
    fun `recomputeSequenceCounts counts distinct questions per bank since round start`() {
        val progress = listOf(
            LegacyProgressRow(bankId = 10L, mode = "SEQUENCE", roundStartedAt = 100L),
            LegacyProgressRow(bankId = 11L, mode = "WRONG", roundStartedAt = 0L),
        )
        val attempts = listOf(
            AttemptSeed(1L, 10L, "SEQUENCE", "A", true, 200L),
            AttemptSeed(1L, 10L, "SEQUENCE", "A", true, 300L),
            AttemptSeed(2L, 10L, "SEQUENCE", "B", false, 50L),
            AttemptSeed(3L, 10L, "SEQUENCE", "A", true, 400L),
            AttemptSeed(4L, 10L, "WRONG", "A", true, 500L),
        )

        val counts = SessionBackfill.recomputeSequenceCounts(
            progressRows = progress,
            attempts = attempts,
            existingQuestionIds = setOf(1L, 2L, 3L, 4L),
        )

        assertEquals(1, counts.size)
        assertEquals(10L to 2, counts.single())
    }

    @Test
    fun `latestPerQuestion keeps the most recent attempt`() {
        val attempts = listOf(
            AttemptSeed(1L, 10L, "SEQUENCE", "A", true, 100L, id = 1L),
            AttemptSeed(1L, 10L, "SEQUENCE", "B", false, 300L, id = 3L),
            AttemptSeed(1L, 10L, "SEQUENCE", "A", true, 200L, id = 2L),
            AttemptSeed(2L, 10L, "SEQUENCE", "A", true, 150L, id = 4L),
        )

        val latest = SessionBackfill.latestPerQuestion(attempts)

        assertEquals(2, latest.size)
        assertEquals(3L, latest.getValue(1L).id)
        assertEquals("B", latest.getValue(1L).answer)
        assertEquals(4L, latest.getValue(2L).id)
    }

    @Test
    fun `backfill combines buildAttempts and recomputeSequenceCounts`() {
        val data = LegacyBackfillData(
            sessions = listOf(session(100L, questionIds = "[1,2]", answers = """{"1":"A","2":"B"}""")),
            questions = listOf(question(1L), question(2L)),
            progress = listOf(LegacyProgressRow(10L, "SEQUENCE", 0L)),
        )

        val (seeds, counts) = SessionBackfill.backfill(data)

        assertEquals(2, seeds.size)
        assertEquals(10L to 2, counts.single())
        assertTrue(seeds.any { it.isCorrect })
        assertTrue(seeds.any { !it.isCorrect })
    }
}
