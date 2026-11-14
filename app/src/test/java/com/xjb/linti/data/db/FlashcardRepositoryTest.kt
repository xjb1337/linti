package com.xjb.linti.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.PracticeCommit
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.QuizClock
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class FlashcardRepositoryTest {
    private lateinit var db: LintiDatabase
    private lateinit var repo: LintiRepository
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun clock(now: Long) = QuizClock { now }

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LintiDatabase::class.java).allowMainThreadQueries().build()
        repo = LintiRepository(db)
        (1L..2).forEach { db.questionBankDao().insert(QuestionBankEntity(id = it, name = "虚构题库$it", createdAt = 1, updatedAt = 1)) }
        (1L..3).forEach { id -> db.questionDao().insert(QuestionEntity(id = id, bankId = if (id == 3L) 2 else 1, sourceNumber = "$id", type = "SINGLE", stem = "虚构题$id", optionsJson = "[]", answer = "A", explanation = "", sourceFileName = "虚构来源", createdAt = 1, updatedAt = 1)) }
    }
    @After fun close() = db.close()

    @Test fun deletingQuestionAndBankCascadesOnlyTheirReviews() = runBlocking {
        (1L..3).forEach { repo.reviewFlashcard(it, true, clock(100_000), zone) }
        db.openHelper.writableDatabase.execSQL("DELETE FROM questions WHERE id = 1")
        assertNull(db.flashcardReviewDao().get(1))
        assertNotNull(db.flashcardReviewDao().get(2))
        db.openHelper.writableDatabase.execSQL("DELETE FROM question_banks WHERE id = 2")
        assertNull(db.flashcardReviewDao().get(3))
        assertNotNull(db.flashcardReviewDao().get(2))
        repo.deleteQuestionsInBank(1)
        assertTrue(db.flashcardReviewDao().getAll().isEmpty())
    }

    @Test fun editingAndMovingQuestionPreservesReviewAndUpdatesBank() = runBlocking {
        repo.reviewFlashcard(1, false, clock(100_000), zone)
        val original = db.flashcardReviewDao().get(1)!!
        val question = repo.getQuestion(1)!!
        repo.saveQuestion(question.copy(stem = "修改后的虚构题", answer = "B"))
        assertEquals(original, db.flashcardReviewDao().get(1))
        repo.saveQuestion(repo.getQuestion(1)!!.copy(bankId = 2))
        assertEquals(original.copy(bankId = 2), db.flashcardReviewDao().get(1))
        assertEquals(2L, repo.flashcardSnapshot(2).reviews.first { it.questionId == 1L }.bankId)
        db.openHelper.writableDatabase.execSQL("DELETE FROM question_banks WHERE id = 1")
        assertEquals(original.copy(bankId = 2), db.flashcardReviewDao().get(1))
    }

    @Test fun scoringAndAnsweringAreIndependent() = runBlocking {
        val commit = PracticeCommit(bankId = 1, mode = PracticeMode.SEQUENCE, questionId = 1, questionBankId = 1, answer = "B", isCorrect = false, currentIndex = 0, answeredAt = 100, roundStartedAt = 0, submissionId = "虚构提交")
        repo.commitPracticeAnswer(commit)
        val wrong = db.wrongItemDao().getByQuestion(1)
        val attempts = db.questionAttemptDao().getAll()
        repo.reviewFlashcard(1, false, clock(100_000), zone)
        val review = db.flashcardReviewDao().get(1)
        assertEquals(wrong, db.wrongItemDao().getByQuestion(1))
        assertEquals(attempts, db.questionAttemptDao().getAll())
        repo.commitPracticeAnswer(commit.copy(answer = "A", isCorrect = true, answeredAt = 200, submissionId = "虚构提交二"))
        assertEquals(review, db.flashcardReviewDao().get(1))
        repo.reviewFlashcard(2, true, clock(200_000), zone)
        assertNull(db.wrongItemDao().getByQuestion(2))
        assertEquals(2, db.questionAttemptDao().getAll().size)
    }

    @Test fun backupSixRoundTripLegacyAndCorruptAreAtomic() = runBlocking {
        repo.reviewFlashcard(1, false, clock(100_000), zone)
        repo.reviewFlashcard(1, true, clock(200_000), zone)
        repo.reviewFlashcard(3, true, clock(300_000), zone)
        val saved = db.flashcardReviewDao().getAll()
        val backup = repo.exportBackupJson(400_000)
        assertEquals(6, JSONObject(backup).getInt("version"))
        repo.restoreBackupJson(backup)
        assertEquals(saved, db.flashcardReviewDao().getAll())
        val badBackups = listOf(
            JSONObject(backup).apply { getJSONArray("flashcardReviews").getJSONObject(0).put("questionId", 999) },
            JSONObject(backup).apply { getJSONArray("flashcardReviews").getJSONObject(0).put("bankId", 2) },
            JSONObject(backup).apply { getJSONArray("flashcardReviews").getJSONObject(0).put("reviewCount", 0) },
            JSONObject(backup).apply { remove("flashcardReviews") },
            JSONObject(backup).apply { put("flashcardReviews", JSONArray().put("虚构错误")) },
            JSONObject(backup).apply { getJSONArray("flashcardReviews").put(getJSONArray("flashcardReviews").getJSONObject(0)) },
        )
        badBackups.forEach { broken ->
            assertTrue(runCatching { repo.restoreBackupJson(broken.toString()) }.isFailure)
            assertEquals(saved, db.flashcardReviewDao().getAll())
            assertEquals(3, repo.getAllQuestions().size)
        }
        (1..5).forEach { version ->
            val legacy = JSONObject(backup).put("version", version).apply { remove("flashcardReviews") }
            repo.restoreBackupJson(legacy.toString())
            assertTrue(db.flashcardReviewDao().getAll().isEmpty())
            assertEquals(3, repo.flashcardSnapshot(null).questions.size)
        }
    }
}
