package com.xjb.linti.data.backup

import com.xjb.linti.data.db.FavoriteEntity
import com.xjb.linti.data.db.ImportJobEntity
import com.xjb.linti.data.db.NoteEntity
import com.xjb.linti.data.db.PracticeProgressEntity
import com.xjb.linti.data.db.QuestionBankEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.db.QuizSessionEntity
import com.xjb.linti.data.db.WrongItemEntity
import org.junit.Assert.assertEquals
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
}
