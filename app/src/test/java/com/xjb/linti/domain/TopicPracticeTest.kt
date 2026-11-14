package com.xjb.linti.domain

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopicPracticeTest {

    @Test
    fun `filters eligible questions by selected banks and types`() {
        val questions = listOf(
            question(1, bankId = 1, type = QuestionType.SINGLE),
            question(2, bankId = 1, type = QuestionType.MULTI, answer = "AB"),
            question(3, bankId = 1, type = QuestionType.JUDGE, answer = "正确"),
            question(4, bankId = 2, type = QuestionType.SINGLE),
        )

        val filtered = TopicPractice.eligibleQuestions(
            allQuestions = questions,
            bankIds = setOf(1),
            types = setOf(QuestionType.SINGLE, QuestionType.MULTI),
        )

        assertEquals(listOf(1L, 2L), filtered.map { it.id })
        assertEquals(
            mapOf(
                QuestionType.SINGLE to 1,
                QuestionType.MULTI to 1,
                QuestionType.JUDGE to 1,
                QuestionType.FILL to 0,
            ),
            TopicPractice.countsByType(questions, setOf(1)),
        )
        assertEquals(
            2,
            TopicPractice.eligibleQuestions(
                allQuestions = questions,
                bankIds = setOf(1, 2),
                types = setOf(QuestionType.SINGLE),
            ).size,
        )
        assertTrue(
            TopicPractice.eligibleQuestions(
                allQuestions = questions,
                bankIds = emptySet(),
                types = setOf(QuestionType.SINGLE),
            ).isEmpty(),
        )
    }

    @Test
    fun `excludes bad and review questions from filtering and counts`() {
        val questions = listOf(
            question(1, bankId = 1, type = QuestionType.SINGLE),
            question(2, bankId = 1, type = QuestionType.SINGLE, needsReview = true),
            question(3, bankId = 1, type = QuestionType.SINGLE, stem = " "),
            question(4, bankId = 1, type = QuestionType.SINGLE, answer = "Z"),
            question(
                5,
                bankId = 1,
                type = QuestionType.SINGLE,
                options = listOf(QuestionOption("A", "只有一项")),
            ),
            question(6, bankId = 1, type = QuestionType.MULTI, answer = "AB"),
            question(7, bankId = 1, type = QuestionType.JUDGE, answer = "正确"),
            question(8, bankId = 1, type = QuestionType.FILL, answer = "虚构答案"),
            question(9, bankId = 1, type = QuestionType.FILL, answer = " "),
            question(10, bankId = 1, type = QuestionType.JUDGE, answer = "不确定"),
        )

        val eligible = TopicPractice.eligibleQuestions(
            allQuestions = questions,
            bankIds = setOf(1),
            types = QuestionType.entries.toSet(),
        )

        assertEquals(listOf(1L, 6L, 7L, 8L), eligible.map { it.id })
        assertEquals(
            mapOf(
                QuestionType.SINGLE to 1,
                QuestionType.MULTI to 1,
                QuestionType.JUDGE to 1,
                QuestionType.FILL to 1,
            ),
            TopicPractice.countsByType(questions, setOf(1)),
        )
    }

    @Test
    fun `count options and default selection follow available question count`() {
        assertCounts(available = 0, enabled = emptySet(), all = null, default = null)
        assertCounts(available = 7, enabled = setOf(7), all = 7, default = 7)
        assertCounts(available = 10, enabled = setOf(10), all = null, default = 10)
        assertCounts(available = 14, enabled = setOf(10, 14), all = 14, default = 14)
        assertCounts(available = 20, enabled = setOf(10, 20), all = null, default = 20)
        assertCounts(available = 35, enabled = setOf(10, 20, 35), all = 35, default = 20)
        assertCounts(available = 50, enabled = setOf(10, 20, 50), all = null, default = 20)
        assertCounts(available = 120, enabled = setOf(10, 20, 50), all = null, default = 20)
    }

    @Test
    fun `draw returns requested count without duplicates`() {
        val candidates = (1L..20L).map {
            question(it, bankId = 1, type = QuestionType.SINGLE)
        }

        val result = TopicPractice.drawQuestions(candidates, count = 8, random = Random(7))

        assertTrue(result is TopicDrawResult.Success)
        val drawn = (result as TopicDrawResult.Success).questions
        assertEquals(8, drawn.size)
        assertEquals(8, drawn.map { it.id }.distinct().size)
        assertTrue(drawn.all { it in candidates })
    }

    @Test
    fun `draw uses injected random source deterministically`() {
        val candidates = (1L..20L).map {
            question(it, bankId = 1, type = QuestionType.SINGLE)
        }

        val first = TopicPractice.drawQuestions(candidates, 8, Random(42))
        val second = TopicPractice.drawQuestions(candidates, 8, Random(42))

        assertEquals(first, second)
    }

    @Test
    fun `draw fails when requested count exceeds available questions`() {
        val candidates = listOf(question(1, bankId = 1, type = QuestionType.SINGLE))

        val result = TopicPractice.drawQuestions(candidates, count = 2)

        assertTrue(result is TopicDrawResult.Failure)
        assertEquals("练习题量不能超过可练题数", (result as TopicDrawResult.Failure).message)
    }

    @Test
    fun `draw rejects non-positive count`() {
        val candidates = listOf(question(1, bankId = 1, type = QuestionType.SINGLE))

        assertTrue(TopicPractice.drawQuestions(candidates, 0) is TopicDrawResult.Failure)
        assertTrue(TopicPractice.drawQuestions(candidates, -1) is TopicDrawResult.Failure)
    }

    @Test
    fun `topic mode keeps ordinary practice commit and grading behavior`() {
        val config = ExamConfig()
        val questionId = 1L
        val selected = QuizSession.select(
            state = AnswerState(),
            questionId = questionId,
            type = QuestionType.SINGLE,
            rawAnswer = "A",
            mode = PracticeMode.TOPIC,
            config = config,
        )

        assertTrue(QuizSession.commitsEnabled(PracticeMode.TOPIC, config))
        assertTrue(questionId in selected.committedIds)
        assertTrue(QuizSession.isRevealed(selected, questionId, PracticeMode.TOPIC, config))
        assertTrue(QuizSession.needsConfirm(QuestionType.MULTI, PracticeMode.TOPIC, config))
    }

    private fun assertCounts(
        available: Int,
        enabled: Set<Int>,
        all: Int?,
        default: Int?,
    ) {
        val options = TopicPractice.countOptions(available)
        val expectedCounts = TopicPractice.fixedCounts + listOfNotNull(all)
        assertEquals(expectedCounts, options.map { it.count })
        options.forEach { option ->
            assertEquals(option.count in enabled, option.enabled)
        }
        assertEquals(all, options.singleOrNull { it.isAll }?.count)
        assertEquals(default, TopicPractice.defaultCount(available))
    }

    private fun question(
        id: Long,
        bankId: Long,
        type: QuestionType,
        answer: String = when (type) {
            QuestionType.SINGLE -> "A"
            QuestionType.MULTI -> "AB"
            QuestionType.JUDGE -> "正确"
            QuestionType.FILL -> "虚构答案"
        },
        stem: String = "虚构题干 $id",
        needsReview: Boolean = false,
        options: List<QuestionOption> = when (type) {
            QuestionType.SINGLE, QuestionType.MULTI -> listOf(
                QuestionOption("A", "虚构选项甲"),
                QuestionOption("B", "虚构选项乙"),
                QuestionOption("C", "虚构选项丙"),
            )
            QuestionType.JUDGE, QuestionType.FILL -> emptyList()
        },
    ): Question = Question(
        id = id,
        bankId = bankId,
        sourceNumber = id.toString(),
        type = type,
        stem = stem,
        options = options,
        answer = answer,
        needsReview = needsReview,
    )
}
