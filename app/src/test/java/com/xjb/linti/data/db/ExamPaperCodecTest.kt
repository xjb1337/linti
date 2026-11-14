package com.xjb.linti.data.db

import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.Grader
import com.xjb.linti.domain.MultiSelectRule
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizQuestion
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamPaperCodecTest {
    // 乱序考卷：考场字母 A/B 与题库原字母相反，正确答案是考场的 B
    private fun shuffledSingle(id: Long, explanation: String? = "原解析") = QuizQuestion(
        questionId = id,
        bankId = 1,
        sourceNumber = "3.$id",
        type = QuestionType.SINGLE,
        stem = "考题$id",
        options = listOf(QuestionOption("A", "原乙"), QuestionOption("B", "原甲")),
        correctAnswer = "B",
        explanation = explanation.orEmpty(),
        points = 2.0,
        labelMap = mapOf("A" to "B", "B" to "A"),
    )

    private fun encodeQuestions(
        questions: List<QuizQuestion>,
        answers: Map<Long, String>,
        config: ExamConfig = ExamConfig(
            singleCount = 2,
            multiCount = 1,
            durationMinutes = 5,
            passScore = 3.0,
            multiSelectRule = MultiSelectRule.LESS_PARTIAL,
        ),
    ) = ExamPaperCodec.encode(
        sessionId = 9,
        questions = questions,
        grading = Grader.grade(questions, answers, config),
    )

    @Test
    fun `round trip preserves exam letters partial score and unanswered`() {
        val multi = QuizQuestion(
            questionId = 3,
            bankId = 1,
            sourceNumber = "5.1",
            type = QuestionType.MULTI,
            stem = "多选",
            options = listOf(
                QuestionOption("A", "甲"),
                QuestionOption("B", "乙"),
                QuestionOption("C", "丙"),
            ),
            correctAnswer = "ABC",
            explanation = "",
            points = 3.0,
        )
        val questions = listOf(shuffledSingle(1), multi, shuffledSingle(2, explanation = null))
        val answers = mapOf(1L to "A", 3L to "AB") // 2 未做；1 答错（考场字母）
        val (entity, rows) = encodeQuestions(questions, answers)

        assertEquals(9L, entity.sessionId)
        assertEquals(ExamPaperCodec.FORMAT_VERSION, entity.formatVersion)
        assertEquals(3, entity.questionCount)

        val decoded = ExamPaperCodec.decode(entity, rows)

        assertEquals(listOf(1L, 3L, 2L), decoded.map { it.questionId })
        assertEquals(listOf("3.1", "5.1", "3.2"), decoded.map { it.sourceNumber })
        // 考场字母保持原样：userAnswer/correctAnswer 不映射回题库
        assertEquals("A", decoded[0].userAnswer)
        assertEquals("B", decoded[0].correctAnswer)
        assertEquals(listOf("A", "B"), decoded[0].options.map { it.label })
        assertEquals(2.0, decoded[0].maxScore, 0.0)
        assertEquals("原解析", decoded[0].explanation)
        // 少选拿到部分分但判为 WRONG，保留实际得分
        assertEquals(2.0, decoded[1].score, 0.0)
        assertEquals(3.0, decoded[1].maxScore, 0.0)
        assertEquals(QuestionOutcome.WRONG, decoded[1].outcome)
        // 未做题：作答为 null，出现在全部试题而不是错题里
        assertNull(decoded[2].userAnswer)
        assertEquals(QuestionOutcome.UNANSWERED, decoded[2].outcome)
        assertEquals(0.0, decoded[2].score, 0.0)
        // 没有原解析的题不补造
        assertNull(decoded[2].explanation)
    }

    @Test
    fun `long stem and explanation are chunked and reassembled`() {
        val longStem = "干".repeat(20_000) + "甲乙"
        val longExplanation = "析".repeat(40_000)
        val question = shuffledSingle(1).copy(stem = longStem, explanation = longExplanation)
        val (entity, rows) = encodeQuestions(listOf(question), mapOf(1L to "B"))

        val questionRows = rows.filter { it.kind == "question" && it.ordinal == 0 }
        assertTrue("长题干应拆成多块", questionRows.size > 2)

        val decoded = ExamPaperCodec.decode(entity, rows)
        assertEquals(longStem, decoded.single().stem)
        assertEquals(longExplanation, decoded.single().explanation)
        assertEquals("B", decoded.single().correctAnswer)
    }

    @Test(expected = IllegalStateException::class)
    fun `missing question chunk fails to decode`() {
        val (entity, rows) = encodeQuestions(
            listOf(shuffledSingle(1), shuffledSingle(2)),
            mapOf(1L to "A"),
        )
        val broken = rows.filterNot { it.kind == "question" && it.ordinal == 1 }
        ExamPaperCodec.decode(entity, broken)
    }

    @Test(expected = IllegalStateException::class)
    fun `header count mismatch fails to decode`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1)), emptyMap())
        ExamPaperCodec.decode(entity.copy(questionCount = 2), rows)
    }

    @Test(expected = IllegalStateException::class)
    fun `unsupported format version fails to decode`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1)), emptyMap())
        ExamPaperCodec.decode(entity.copy(formatVersion = 99), rows)
    }

    @Test(expected = IllegalStateException::class)
    fun `missing required field fails to decode`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1)), mapOf(1L to "A"))
        val broken = rows.map { row ->
            if (row.kind == "question") {
                val json = JSONObject(row.content)
                json.remove("score")
                row.copy(content = json.toString())
            } else {
                row
            }
        }
        ExamPaperCodec.decode(entity, broken)
    }

    @Test(expected = IllegalStateException::class)
    fun `missing userAnswer field fails to decode even though null is allowed`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1)), emptyMap())
        val broken = rows.map { row ->
            if (row.kind == "question") {
                // 未做题的 userAnswer 是显式 null；直接删掉字段则必须拒绝
                val json = JSONObject(row.content)
                json.remove("userAnswer")
                row.copy(content = json.toString())
            } else {
                row
            }
        }
        ExamPaperCodec.decode(entity, broken)
    }

    @Test
    fun `explicit null userAnswer and explanation survive round trip`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1, explanation = null)), emptyMap())

        val decoded = ExamPaperCodec.decode(entity, rows)

        assertNull(decoded.single().userAnswer)
        assertNull(decoded.single().explanation)
    }

    @Test(expected = IllegalStateException::class)
    fun `extra question chunk beyond count fails to decode`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1)), emptyMap())
        ExamPaperCodec.decode(
            entity,
            rows + ExamPaperChunkEntity(entity.sessionId, "question", entity.questionCount, 0, """{"questionId":9}"""),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `extra metadata chunk fails to decode`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1)), emptyMap())
        ExamPaperCodec.decode(
            entity,
            rows + ExamPaperChunkEntity(entity.sessionId, "metadata", 1, 0, """{"version":1}"""),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `unknown chunk kind fails to decode`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1)), emptyMap())
        ExamPaperCodec.decode(
            entity,
            rows + ExamPaperChunkEntity(entity.sessionId, "junk", 0, 0, "x"),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `duplicate chunk index fails to decode`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1)), emptyMap())
        val duplicated = rows.first { it.kind == "question" }
        ExamPaperCodec.decode(entity, rows + duplicated)
    }

    @Test
    fun `validate backup rejects non exam session owner`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1)), mapOf(1L to "A"))
        val practiceSession = QuizSessionEntity(
            id = 9L,
            bankId = 1,
            bankName = "题库",
            mode = "SEQUENCE",
            configJson = "{}",
            questionIdsJson = "[]",
            answersJson = "{}",
            flagsJson = "[]",
            bookmarksJson = "[]",
            startedAt = 1,
            status = "COMPLETED",
        )
        val rejected = runCatching {
            ExamPaperCodec.validateBackup(listOf(entity), rows, sessions = listOf(practiceSession))
        }
        assertTrue(rejected.isFailure)

        // 同一会话补成已交卷模考后通过
        val examSession = practiceSession.copy(mode = "EXAM")
        ExamPaperCodec.validateBackup(listOf(entity), rows, sessions = listOf(examSession))
    }

    @Test
    fun `validate backup rejects unknown session and missing chunks`() {
        val (entity, rows) = encodeQuestions(listOf(shuffledSingle(1)), mapOf(1L to "A"))
        val examSession = QuizSessionEntity(
            id = 9L,
            bankId = 1,
            bankName = "题库",
            mode = "EXAM",
            configJson = "{}",
            questionIdsJson = "[]",
            answersJson = "{}",
            flagsJson = "[]",
            bookmarksJson = "[]",
            startedAt = 1,
            status = "COMPLETED",
        )

        // 快照正常、归属会话存在：通过
        ExamPaperCodec.validateBackup(listOf(entity), rows, sessions = listOf(examSession))

        // 缺少所属会话：拒绝
        val withoutSession = runCatching {
            ExamPaperCodec.validateBackup(listOf(entity), rows, sessions = emptyList())
        }
        assertTrue(withoutSession.isFailure)

        // 丢块：拒绝
        val missingChunk = runCatching {
            ExamPaperCodec.validateBackup(
                listOf(entity),
                rows.filterNot { it.kind == "metadata" },
                sessions = listOf(examSession),
            )
        }
        assertTrue(missingChunk.isFailure)
    }
}
