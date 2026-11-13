package com.xjb.linti.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xjb.linti.data.repo.ExamRecord
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.SessionSnapshot
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.Grader
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizQuestion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamDraftPersistenceTest {
    @Test
    fun reopenVersionBarrierRollbackSubmitAndBackupRestore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "linti-exam-draft-test.db"
        context.deleteDatabase(name)
        var database = Room.databaseBuilder(context, LintiDatabase::class.java, name).build()
        try {
            database.questionBankDao().insert(QuestionBankEntity(id = 1, name = "题库", createdAt = 1, updatedAt = 1))
            listOf(1L, 2L).forEach { id ->
                database.questionDao().insert(
                    QuestionEntity(
                        id = id,
                        bankId = 1,
                        sourceNumber = id.toString(),
                        type = "SINGLE",
                        stem = "题干$id",
                        optionsJson = "[]",
                        answer = "A",
                        explanation = "",
                        sourceFileName = "test",
                        createdAt = 1,
                        updatedAt = 1,
                    ),
                )
            }
            val question = QuizQuestion(
                questionId = 1,
                bankId = 1,
                sourceNumber = "1",
                type = QuestionType.SINGLE,
                stem = "保存的原题",
                options = listOf(QuestionOption("A", "原乙"), QuestionOption("B", "原甲")),
                correctAnswer = "B",
                explanation = "原解析",
                labelMap = mapOf("A" to "B", "B" to "A"),
            )
            val initial = ExamDraftSnapshot(
                draftId = "exam-1",
                bankId = 1,
                bankName = "题库",
                config = ExamConfig(singleCount = 2, durationMinutes = 5, passScore = 2.0),
                questions = listOf(question, question.copy(questionId = 2)),
                answers = emptyMap(),
                committedIds = emptySet(),
                flaggedIds = emptySet(),
                bookmarkedIds = emptySet(),
                currentIndex = 0,
                startedAt = 1_000,
                revision = 0,
            )
            var repository = LintiRepository(database)
            repository.createExamDraft(initial)
            val latest = initial.copy(answers = mapOf(1L to "B"), currentIndex = 1, revision = 2)
            assertTrue(repository.saveExamDraft(latest))
            assertTrue(repository.saveExamDraft(initial.copy(answers = mapOf(1L to "A"), revision = 1)))
            database.close()

            database = Room.databaseBuilder(context, LintiDatabase::class.java, name).build()
            repository = LintiRepository(database)
            assertEquals(latest, repository.getExamDraft(1))

            val backup = repository.exportBackupJson()
            repository.restoreBackupJson(backup)
            assertNull(repository.getExamDraft(1))
            assertFalse(repository.saveExamDraft(latest.copy(revision = 3)))

            val restored = latest.copy(draftId = "exam-2", revision = 0)
            repository.createExamDraft(restored)
            val grading = Grader.grade(restored.questions, restored.answers, restored.config)
            val result = SessionSnapshot(
                bankId = 1,
                bankName = restored.bankName,
                mode = PracticeMode.EXAM,
                config = restored.config,
                questions = restored.questions,
                answers = restored.answers,
                flaggedIds = restored.flaggedIds,
                bookmarkedIds = restored.bookmarkedIds,
                startedAt = restored.startedAt,
                endedAt = 2_000,
                grading = grading,
            )
            assertTrue(runCatching {
                repository.submitExamSession(result.copy(bankId = 999), emptyList(), restored.draftId)
            }.isFailure)
            assertEquals(restored, repository.getExamDraft(1))
            assertTrue(repository.getAllSessions().isEmpty())

            database.openHelper.writableDatabase.execSQL("DELETE FROM questions WHERE id = 2")
            repository.submitExamSession(
                result,
                listOf(ExamRecord(1, 1, "A", true), ExamRecord(2, 1, "A", false)),
                restored.draftId,
            )
            assertNull(repository.getExamDraft(1))
            assertFalse(repository.saveExamDraft(restored.copy(revision = 3)))
            assertTrue(runCatching {
                repository.submitExamSession(result, emptyList(), restored.draftId)
            }.isFailure)
            assertEquals(1, repository.getAllSessions().size)
            assertEquals(listOf(1L), database.questionAttemptDao().getAll().map { it.questionId })
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
