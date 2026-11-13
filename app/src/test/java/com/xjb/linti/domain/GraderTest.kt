package com.xjb.linti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraderTest {
    @Test
    fun `single choice is all or nothing and missing is unanswered`() {
        val questions = listOf(question(1, QuestionType.SINGLE, "C", 2.0))
        val correct = Grader.grade(questions, mapOf(1L to "c"), ExamConfig())
        val wrong = Grader.grade(questions, mapOf(1L to "B"), ExamConfig())
        val missing = Grader.grade(questions, emptyMap(), ExamConfig())

        assertEquals(2.0, correct.totalScore, 0.0)
        assertEquals(QuestionOutcome.CORRECT, correct.outcomes.getValue(1).outcome)
        assertEquals(0.0, wrong.totalScore, 0.0)
        assertEquals(QuestionOutcome.WRONG, wrong.outcomes.getValue(1).outcome)
        assertEquals(QuestionOutcome.UNANSWERED, missing.outcomes.getValue(1).outcome)
        assertEquals(1, missing.unansweredCount)
    }

    @Test
    fun `multi choice supports no partial and partial rules`() {
        val question = question(
            id = 1,
            type = QuestionType.MULTI,
            answer = "ACD",
            points = 3.0,
            options = listOf(
                QuestionOption("A", "甲"),
                QuestionOption("B", "乙"),
                QuestionOption("C", "丙"),
                QuestionOption("D", "丁"),
            ),
        )
        val answers = mapOf(1L to "A、C")

        val noScore = Grader.grade(
            listOf(question),
            answers,
            ExamConfig(multiSelectRule = MultiSelectRule.LESS_NO_SCORE),
        )
        val partial = Grader.grade(
            listOf(question),
            answers,
            ExamConfig(multiSelectRule = MultiSelectRule.LESS_PARTIAL),
        )
        val extra = Grader.grade(
            listOf(question),
            mapOf(1L to "ABC"),
            ExamConfig(multiSelectRule = MultiSelectRule.LESS_PARTIAL),
        )

        assertEquals(0.0, noScore.totalScore, 0.0)
        assertEquals(2.0, partial.totalScore, 0.0)
        assertEquals(QuestionOutcome.WRONG, partial.outcomes.getValue(1).outcome)
        assertEquals(0.0, extra.totalScore, 0.0)
    }

    @Test
    fun `multi choice exact match gets full points`() {
        val question = question(
            id = 1,
            type = QuestionType.MULTI,
            answer = "ACD",
            points = 2.0,
            options = listOf(
                QuestionOption("A", "甲"),
                QuestionOption("B", "乙"),
                QuestionOption("C", "丙"),
                QuestionOption("D", "丁"),
            ),
        )

        val result = Grader.grade(
            listOf(question),
            mapOf(1L to "D、A、C"),
            ExamConfig(multiSelectRule = MultiSelectRule.LESS_NO_SCORE),
        )

        assertEquals(2.0, result.totalScore, 0.0)
        assertEquals(QuestionOutcome.CORRECT, result.outcomes.getValue(1).outcome)
    }

    @Test
    fun `judge accepts normalized Chinese and boolean tokens`() {
        val question = question(1, QuestionType.JUDGE, "true", 2.0)

        val correct = Grader.grade(listOf(question), mapOf(1L to "正确"), ExamConfig())
        val alsoCorrect = Grader.grade(listOf(question), mapOf(1L to "T"), ExamConfig())
        val wrong = Grader.grade(listOf(question), mapOf(1L to "错误"), ExamConfig())

        assertEquals(2.0, correct.totalScore, 0.0)
        assertEquals(2.0, alsoCorrect.totalScore, 0.0)
        assertEquals(0.0, wrong.totalScore, 0.0)
    }

    @Test
    fun `fill in is trim fullwidth and case normalized`() {
        val question = question(1, QuestionType.FILL, "Hello 世界", 2.0)
        val result = Grader.grade(
            listOf(question),
            mapOf(1L to "  ｈｅｌｌｏ　世界  "),
            ExamConfig(),
        )

        assertEquals(2.0, result.totalScore, 0.0)
        assertEquals(QuestionOutcome.CORRECT, result.outcomes.getValue(1).outcome)
    }

    @Test
    fun `reports total pass status and per type statistics`() {
        val questions = listOf(
            question(1, QuestionType.SINGLE, "A", 2.0),
            question(
                2,
                QuestionType.MULTI,
                "AB",
                2.0,
                options = listOf(
                    QuestionOption("A", "甲"),
                    QuestionOption("B", "乙"),
                ),
            ),
            question(3, QuestionType.JUDGE, "false", 2.0),
            question(4, QuestionType.FILL, "肺水肿", 2.0),
        )
        val result = Grader.grade(
            questions,
            mapOf(
                1L to "A",
                2L to "AB",
                3L to "错误",
            ),
            ExamConfig(passScore = 5.0),
        )

        assertEquals(6.0, result.totalScore, 0.0)
        assertEquals(8.0, result.maxScore, 0.0)
        assertTrue(result.passed)
        assertEquals(3, result.correctCount)
        assertEquals(0, result.wrongCount)
        assertEquals(1, result.unansweredCount)
        assertEquals(3, result.answeredCount)
        assertEquals(0.75, result.accuracy, 0.0001)
        assertEquals(4, result.typeStats.size)
        val singleStats = result.typeStats.first { it.type == QuestionType.SINGLE }
        assertEquals(1, singleStats.total)
        assertEquals(1, singleStats.correct)
        assertEquals(1.0, singleStats.accuracy, 0.0)
    }

    @Test
    fun `fails when score is below pass line`() {
        val result = Grader.grade(
            listOf(question(1, QuestionType.SINGLE, "A", 2.0)),
            mapOf(1L to "B"),
            ExamConfig(passScore = 1.0),
        )

        assertFalse(result.passed)
    }

    private fun question(
        id: Long,
        type: QuestionType,
        answer: String,
        points: Double,
        options: List<QuestionOption> = emptyList(),
    ) = QuizQuestion(
        questionId = id,
        bankId = 1,
        sourceNumber = id.toString(),
        type = type,
        stem = "虚构题干 $id",
        options = options,
        correctAnswer = answer,
        explanation = "虚构解析",
        points = points,
    )
}
