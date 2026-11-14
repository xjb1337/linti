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
            passScore = 0.0,
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
            config = ExamConfig(singleCount = 5, passScore = 0.0),
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
            config = ExamConfig(singleCount = 2, wrongFirst = true, passScore = 0.0),
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
            config = ExamConfig(singleCount = 2, unansweredFirst = true, passScore = 0.0),
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
            config = ExamConfig(singleCount = 1, shuffleOptions = true, passScore = 0.0),
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

    @Test
    fun `available counts and exam draws exclude pending and unanswerable questions`() {
        val validSingle = question(11, QuestionType.SINGLE, "ａ、A").copy(
            options = listOf(QuestionOption("a", "甲"), QuestionOption("b", "乙")),
        )
        val validMulti = question(12, QuestionType.MULTI, "a、Ｃ")
        val validJudge = question(13, QuestionType.JUDGE, "×")
        val validFill = question(14, QuestionType.FILL, "答案")
        val invalidQuestions = listOf(
            question(21, QuestionType.SINGLE, "A").copy(needsReview = true),
            question(22, QuestionType.SINGLE, ""),
            question(23, QuestionType.SINGLE, "D"),
            question(24, QuestionType.SINGLE, "AB"),
            question(25, QuestionType.SINGLE, "unavailable"),
            question(26, QuestionType.MULTI, "AD"),
            question(27, QuestionType.JUDGE, "不太正确"),
            question(28, QuestionType.FILL, "  "),
            question(29, QuestionType.SINGLE, "A").copy(stem = " "),
            question(30, QuestionType.SINGLE, "A").copy(
                options = listOf(QuestionOption("A", "甲"), QuestionOption("a", "乙")),
            ),
            question(31, QuestionType.SINGLE, "A").copy(
                options = listOf(QuestionOption("A", "甲"), QuestionOption("B", " ")),
            ),
        )
        val questions = listOf(validSingle, validMulti, validJudge, validFill) + invalidQuestions

        val counts = ExamEligibility.availableCounts(questions)
        assertEquals(QuestionType.entries.associateWith { if (it == QuestionType.ESSAY) 0 else 1 }, counts)

        val result = builder.build(
            config = ExamConfig(
                singleCount = 3,
                multiCount = 2,
                judgeCount = 2,
                fillCount = 2,
                shuffleOptions = true,
                wrongFirst = true,
                unansweredFirst = true,
                passScore = 0.0,
            ),
            questions = questions,
            wrongQuestionIds = invalidQuestions.map { it.id }.toSet(),
            random = Random(6),
        )

        assertEquals(null, result.error)
        assertEquals(setOf(11L, 12L, 13L, 14L), result.questions.map { it.questionId }.toSet())
        assertEquals(counts, QuestionType.entries.associateWith { type -> result.questions.count { it.type == type } })
        val shuffledSingle = result.questions.first { it.questionId == validSingle.id }
        assertEquals("甲", shuffledSingle.options.first { it.label == shuffledSingle.correctAnswer }.text)
    }

    @Test
    fun `allows a multi question with one selectable correct option`() {
        val multi = question(15, QuestionType.MULTI, "B")
        val result = builder.build(
            ExamConfig(multiCount = 1, passScore = 2.0),
            listOf(multi),
            random = Random(7),
        )

        assertTrue(ExamEligibility.isEligible(multi))
        assertEquals(null, result.error)
        assertEquals(listOf(multi.id), result.questions.map { it.questionId })
    }

    @Test
    fun `checks pass score against the points actually drawn after a shortage`() {
        val singles = sampleQuestions().filter { it.type == QuestionType.SINGLE }
        val config = ExamConfig(singleCount = 5, singlePoints = 2.0, passScore = 7.0)

        val blocked = builder.build(config, singles, random = Random(8))
        assertEquals("及格分不能超过实际试卷满分 6 分", blocked.error)
        assertTrue(blocked.questions.isEmpty())
        assertTrue(blocked.warnings.any { it.contains("单选题不足") })

        val accepted = builder.build(config.copy(passScore = 6.0), singles, random = Random(8))
        assertEquals(null, accepted.error)
        assertEquals(6.0, accepted.questions.sumOf { it.points }, 0.0)
        assertEquals(6.0, config.copy(passScore = 6.0).passScore, 0.0)
    }

    @Test
    fun `rejects invalid individual points and nonfinite pass score`() {
        val questions = sampleQuestions()
        val negativePoints = builder.build(
            ExamConfig(singleCount = 1, multiCount = 1, singlePoints = -2.0, multiPoints = 4.0, passScore = 1.0),
            questions,
        )
        val nonfinitePassScore = builder.build(
            ExamConfig(singleCount = 1, passScore = Double.NaN),
            questions,
        )

        assertTrue(negativePoints.error?.contains("无效") == true)
        assertTrue(nonfinitePassScore.error?.contains("无效") == true)
        assertTrue(negativePoints.questions.isEmpty())
        assertTrue(nonfinitePassScore.questions.isEmpty())
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
