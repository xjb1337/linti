package com.xjb.linti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ExamBuilderTest {
    private val builder = ExamBuilder()

    @Test
    fun `builds configured counts and points by type`() {
        val questions = sampleQuestions()
        val config = ExamConfig(
            singleCount = 2,
            multiCount = 1,
            judgeCount = 1,
            fillCount = 1,
            singlePoints = 2.0,
            multiPoints = 3.0,
            judgePoints = 1.0,
            fillPoints = 4.0,
        )

        val result = builder.build(config, questions, random = Random(1))

        assertNotNull(result)
        assertEquals(null, result.error)
        assertEquals(5, result.questions.size)
        assertEquals(2, result.questions.count { it.type == QuestionType.SINGLE })
        assertEquals(1, result.questions.count { it.type == QuestionType.MULTI })
        assertEquals(1, result.questions.count { it.type == QuestionType.JUDGE })
        assertEquals(1, result.questions.count { it.type == QuestionType.FILL })
        assertEquals(3.0, result.questions.first { it.type == QuestionType.MULTI }.points, 0.0)
        assertEquals(4.0, result.questions.first { it.type == QuestionType.FILL }.points, 0.0)
    }

    @Test
    fun `reports shortage and fills what is available`() {
        val result = builder.build(
            config = ExamConfig(singleCount = 5),
            questions = sampleQuestions(),
            random = Random(2),
        )

        assertEquals(null, result.error)
        assertEquals(3, result.questions.size)
        assertTrue(result.warnings.any { it.contains("单选题不足") })
    }

    @Test
    fun `prioritizes wrong questions`() {
        val questions = sampleQuestions()
        val wrongId = questions.last { it.type == QuestionType.SINGLE }.id

        val result = builder.build(
            config = ExamConfig(singleCount = 2, wrongFirst = true),
            questions = questions,
            wrongQuestionIds = setOf(wrongId),
            random = Random(3),
        )

        assertTrue(result.questions.first().questionId == wrongId)
    }

    @Test
    fun `prioritizes unanswered questions`() {
        val singles = sampleQuestions().filter { it.type == QuestionType.SINGLE }
        val answeredId = singles[0].id
        val unansweredId = singles[1].id

        val result = builder.build(
            config = ExamConfig(singleCount = 2, unansweredFirst = true),
            questions = sampleQuestions(),
            answeredQuestionIds = setOf(answeredId),
            random = Random(4),
        )

        assertEquals(unansweredId, result.questions.first().questionId)
    }

    @Test
    fun `shuffling options rewrites correct answer label`() {
        val question = Question(
            id = 10,
            bankId = 1,
            type = QuestionType.SINGLE,
            stem = "选项乱序测试题干",
            options = listOf(
                QuestionOption("A", "甲"),
                QuestionOption("B", "乙"),
                QuestionOption("C", "丙"),
                QuestionOption("D", "丁"),
            ),
            answer = "C",
        )

        val result = builder.build(
            config = ExamConfig(singleCount = 1, shuffleOptions = true),
            questions = listOf(question),
            random = Random(5),
        )

        val quiz = result.questions.single()
        assertEquals(listOf("A", "B", "C", "D"), quiz.options.map { it.label })
        val correctText = quiz.options.first { it.label == quiz.correctAnswer }.text
        assertEquals("丙", correctText)
    }

    @Test
    fun `returns explicit errors for empty config and empty bank`() {
        val emptyConfig = builder.build(ExamConfig(), sampleQuestions())
        val emptyBank = builder.build(ExamConfig(singleCount = 1), emptyList())

        assertTrue(emptyConfig.error?.contains("未设置") == true)
        assertTrue(emptyBank.error?.contains("没有") == true)
    }

    private fun sampleQuestions(): List<Question> = listOf(
        question(1, QuestionType.SINGLE, "A"),
        question(2, QuestionType.SINGLE, "B"),
        question(3, QuestionType.SINGLE, "C"),
        question(4, QuestionType.MULTI, "AB"),
        question(5, QuestionType.MULTI, "AC"),
        question(6, QuestionType.JUDGE, "true"),
        question(7, QuestionType.FILL, "虚构答案"),
    )

    private fun question(id: Long, type: QuestionType, answer: String) = Question(
        id = id,
        bankId = 1,
        sourceNumber = id.toString(),
        type = type,
        stem = "第 $id 道虚构教材题",
        options = if (type == QuestionType.SINGLE || type == QuestionType.MULTI) {
            listOf(
                QuestionOption("A", "甲"),
                QuestionOption("B", "乙"),
                QuestionOption("C", "丙"),
            )
        } else {
            emptyList()
        },
        answer = answer,
        explanation = "虚构解析",
    )
}
