package com.xjb.linti.data.backup

import com.xjb.linti.data.db.FavoriteEntity
import com.xjb.linti.data.db.ImportJobEntity
import com.xjb.linti.data.db.NoteEntity
import com.xjb.linti.data.db.PracticeProgressEntity
import com.xjb.linti.data.db.QuestionAttemptEntity
import com.xjb.linti.data.db.QuestionBankEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.db.QuizSessionEntity
import com.xjb.linti.data.db.WrongItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    @Test
    fun `round trips all local data tables including nullable fields`() {
        val payload = BackupPayload(
            exportedAt = 123456,
            banks = listOf(QuestionBankEntity(1, "题库甲", 10, 20, "备注")),
            questions = listOf(
                QuestionEntity(
                    id = 2,
                    bankId = 1,
                    sourceNumber = "1",
                    type = "SINGLE",
                    stem = "虚构题干",
                    optionsJson = """[{"label":"A","text":"甲"}]""",
                    answer = "A",
                    explanation = "虚构解析",
                    tags = null,
                    knowledgePoint = "考点",
                    confidence = 0.9,
                    needsReview = true,
                    reviewReasonsJson = """["待确认"]""",
                    sourceFileName = "a.txt",
                    rawSnippet = null,
                    createdAt = 10,
                    updatedAt = 20,
                ),
            ),
            importJobs = listOf(
                ImportJobEntity(
                    id = 3,
                    fileName = "a.txt",
                    mimeType = "text/plain",
                    kind = "txt",
                    method = "LOCAL",
                    status = "CONFIRMED",
                    recognizedCount = 1,
                    confirmedCount = 1,
                    pendingCount = 0,
                    failedCount = 0,
                    createdAt = 10,
                    targetBankId = 1,
                    warningsJson = "[]",
                ),
            ),
            favorites = listOf(FavoriteEntity(2, 30)),
            notes = listOf(NoteEntity(2, "笔记内容", 30, 40)),
            wrongItems = listOf(WrongItemEntity(2, 1, 3, 50, false, true)),
            progress = listOf(PracticeProgressEntity(1, "SEQUENCE", 5, 5, 60)),
            sessions = listOf(
                QuizSessionEntity(
                    id = 7,
                    bankId = 1,
                    bankName = "题库甲",
                    mode = "EXAM",
                    configJson = "{}",
                    questionIdsJson = "[2]",
                    answersJson = """{"2":"A"}""",
                    flagsJson = "[]",
                    bookmarksJson = "[2]",
                    startedAt = 70,
                    endedAt = 90,
                    score = 2.0,
                    passed = true,
                    statsJson = "[]",
                    status = "COMPLETED",
                    answeredCount = 1,
                    correctCount = 1,
                    wrongCount = 0,
                    unansweredCount = 0,
                ),
            ),
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(payload))

        assertEquals(1, decoded.banks.size)
        assertEquals("题库甲", decoded.banks.single().name)
        assertEquals(1, decoded.questions.size)
        assertTrue(decoded.questions.single().needsReview)
        assertEquals(1L, decoded.importJobs.single().targetBankId)
        assertEquals(1, decoded.favorites.size)
        assertEquals("笔记内容", decoded.notes.single().content)
        assertEquals(3, decoded.wrongItems.single().wrongCount)
        assertEquals(5, decoded.progress.single().currentIndex)
        assertEquals(2.0, decoded.sessions.single().score!!, 0.0)
        assertEquals(true, decoded.sessions.single().passed)
        assertNull(decoded.questions.single().tags)
        assertNull(decoded.questions.single().rawSnippet)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects unsupported backup version`() {
        BackupCodec.decode("""{"version":99,"exportedAt":0}""")
    }

    @Test
    fun `round trips question attempts and round started at for version two`() {
        val payload = BackupPayload(
            version = 2,
            exportedAt = 999,
            banks = listOf(QuestionBankEntity(1, "题库甲", 10, 20, null)),
            questions = listOf(
                QuestionEntity(
                    id = 2, bankId = 1, sourceNumber = "1", type = "SINGLE", stem = "题干",
                    optionsJson = "[]", answer = "A", explanation = "", tags = null,
                    knowledgePoint = null, confidence = 1.0, needsReview = false,
                    reviewReasonsJson = "[]", sourceFileName = "a.txt", rawSnippet = null,
                    createdAt = 1, updatedAt = 2,
                ),
            ),
            progress = listOf(PracticeProgressEntity(1, "SEQUENCE", 3, 2, 60, 777)),
            questionAttempts = listOf(
                QuestionAttemptEntity(
                    id = 5, questionId = 2, bankId = 1, mode = "SEQUENCE",
                    answer = "A", isCorrect = true, answeredAt = 800, sessionId = null,
                ),
                QuestionAttemptEntity(
                    id = 6, questionId = 2, bankId = 1, mode = "EXAM",
                    answer = "B", isCorrect = false, answeredAt = 900, sessionId = 42,
                ),
            ),
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(payload))

        assertEquals(2, decoded.version)
        assertEquals(2, decoded.questionAttempts.size)
        assertEquals(5L, decoded.questionAttempts[0].id)
        assertNull(decoded.questionAttempts[0].sessionId)
        assertEquals(42L, decoded.questionAttempts[1].sessionId)
        assertFalse(decoded.questionAttempts[1].isCorrect)
        assertEquals(777L, decoded.progress.single().roundStartedAt)
    }

    @Test
    fun `decodes version one backup with empty attempts and zero round started at`() {
        val v1 = """
            {
              "version": 1,
              "exportedAt": 10,
              "banks": [],
              "questions": [],
              "progress": [
                {"bankId": 1, "mode": "SEQUENCE", "currentIndex": 5, "answeredCount": 5, "lastOpenedAt": 60}
              ]
            }
        """.trimIndent()

        val decoded = BackupCodec.decode(v1)

        assertEquals(1, decoded.version)
        assertTrue(decoded.questionAttempts.isEmpty())
        assertEquals(0L, decoded.progress.single().roundStartedAt)
        assertEquals(5, decoded.progress.single().answeredCount)
    }
}
