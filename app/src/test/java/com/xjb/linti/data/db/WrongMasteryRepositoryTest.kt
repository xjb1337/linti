package com.xjb.linti.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.repo.ExamRecord
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.PracticeCommit
import com.xjb.linti.data.repo.SessionSnapshot
import com.xjb.linti.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class WrongMasteryRepositoryTest {
    private lateinit var db: LintiDatabase
    private lateinit var repository: LintiRepository
    private var threshold: Int? = 2

    @Before fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LintiDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = LintiRepository(db) { threshold }
        db.questionBankDao().insert(QuestionBankEntity(id = 1, name = "虚构题库", createdAt = 1, updatedAt = 1))
        (1L..2L).forEach { id ->
            db.questionDao().insert(QuestionEntity(id = id, bankId = 1, sourceNumber = "$id", type = "SINGLE", stem = "虚构题$id", optionsJson = "[]", answer = "A", explanation = "", sourceFileName = "虚构来源", createdAt = 1, updatedAt = 1))
        }
    }
    @After fun tearDown() { db.close() }

    private fun commit(id: Long = 1, correct: Boolean = true, mode: PracticeMode = PracticeMode.SEQUENCE,
        token: String, at: Long = 100, viewed: Boolean = false) = PracticeCommit(
        bankId = 1, mode = mode, questionId = id, questionBankId = 1, answer = if (correct) "A" else "B",
        isCorrect = correct, currentIndex = 0, answeredAt = at, roundStartedAt = 0,
        answerViewed = viewed, submissionId = token,
    )

    @Test fun independentPracticeSubmissionsAreIdempotentAndApplyAllModes() = runBlocking {
        val wrong = commit(correct = false, token = "wrong")
        listOf(async { repository.commitPracticeAnswer(wrong) }, async { repository.commitPracticeAnswer(wrong) }).awaitAll()
        assertEquals(1, db.wrongItemDao().getByQuestion(1)!!.wrongCount)
        assertEquals(1, db.questionAttemptDao().getAll().size)
        val correct = commit(mode = PracticeMode.TOPIC, token = "correct", at = 200)
        repository.commitPracticeAnswer(correct)
        repository.commitPracticeAnswer(correct.copy(answeredAt = 201))
        assertEquals(1, db.wrongItemDao().getByQuestion(1)!!.correctStreak)
        assertEquals(2, db.questionAttemptDao().getAll().size)
        repository.commitPracticeAnswer(commit(mode = PracticeMode.FAVORITE, token = "viewed", viewed = true))
        assertEquals(1, db.wrongItemDao().getByQuestion(1)!!.correctStreak)
        repository.commitPracticeAnswer(commit(mode = PracticeMode.WRONG, token = "master", at = 300))
        val mastered = db.wrongItemDao().getByQuestion(1)!!
        assertEquals(2, mastered.correctStreak); assertEquals(300L, mastered.masteredAt); assertFalse(mastered.pendingReview)
        assertTrue(repository.getPendingWrongRows().isEmpty())
        assertTrue(repository.getPendingWrongRows(questionId = 1).isEmpty())
        repository.commitPracticeAnswer(commit(mode = PracticeMode.RECITE, correct = false, token = "recite"))
        repository.commitPracticeAnswer(commit(mode = PracticeMode.FLASHCARD, correct = false, token = "flash"))
        assertEquals(mastered, db.wrongItemDao().getByQuestion(1))
        repository.commitPracticeAnswer(commit(id = 2, token = "new-correct"))
        assertNull(db.wrongItemDao().getByQuestion(2))
        repository.commitPracticeAnswer(commit(mode = PracticeMode.SEQUENCE, correct = false, token = "viewed-wrong", viewed = true, at = 400))
        val reopened = db.wrongItemDao().getByQuestion(1)!!
        assertEquals(2, reopened.wrongCount); assertEquals(0, reopened.correctStreak); assertNull(reopened.masteredAt); assertTrue(reopened.pendingReview)
        assertEquals(listOf(1L), repository.getPendingWrongRows(questionIds = setOf(1, 2)).map { it.wrongItem.questionId })
        assertTrue(repository.getPendingWrongRows(questionIds = setOf(2)).isEmpty())
    }

    @Test fun changingThresholdAndDisablingOnlyAffectLaterAnswers() = runBlocking {
        repository.commitPracticeAnswer(commit(correct = false, token = "w"))
        threshold = null
        repeat(3) { repository.commitPracticeAnswer(commit(token = "c$it")) }
        assertEquals(3, db.wrongItemDao().getByQuestion(1)!!.correctStreak)
        assertTrue(db.wrongItemDao().getByQuestion(1)!!.pendingReview)
        threshold = 2
        assertTrue(db.wrongItemDao().getByQuestion(1)!!.pendingReview)
        repository.commitPracticeAnswer(commit(token = "next", at = 500))
        assertEquals(500L, db.wrongItemDao().getByQuestion(1)!!.masteredAt)
        threshold = 5
        repository.commitPracticeAnswer(commit(token = "later", at = 600))
        assertEquals(500L, db.wrongItemDao().getByQuestion(1)!!.masteredAt)
        repository.commitPracticeAnswer(commit(correct = false, token = "again", at = 700))
        repository.markWrongCorrected(1, 800)
        val manual = db.wrongItemDao().getByQuestion(1)!!
        assertEquals(800L, manual.masteredAt); assertEquals(2, manual.wrongCount); assertFalse(manual.pendingReview)
    }

    @Test fun examSubmissionCountsOnceAndWrongAnswersReopenMasteredItems() = runBlocking {
        repository.commitPracticeAnswer(commit(correct = false, token = "w"))
        repository.commitPracticeAnswer(commit(token = "practice"))
        val question = QuizQuestion(questionId = 1, bankId = 1, sourceNumber = "1", type = QuestionType.SINGLE,
            stem = "虚构考题", options = listOf(QuestionOption("A", "虚构甲"), QuestionOption("B", "虚构乙")), correctAnswer = "A", explanation = "")
        val questions = listOf(question)
        val config = ExamConfig(singleCount = 1, passScore = 0.0)
        val answers = mapOf(1L to "A")
        val snapshot = SessionSnapshot(1, "虚构题库", PracticeMode.EXAM, config, questions, answers,
            emptySet(), emptySet(), 1000, 2000, Grader.grade(questions, answers, config))
        fun draft(id: String) = ExamDraftSnapshot(id, 1, "虚构题库", config, questions, answers,
            emptySet(), emptySet(), emptySet(), 0, 1000, 0)
        repository.createExamDraft(draft("exam-1"))
        val record = ExamRecord(1, 1, "A", true)
        repository.submitExamSession(snapshot, listOf(record, record), "exam-1")
        assertEquals(2, db.wrongItemDao().getByQuestion(1)!!.correctStreak)
        assertEquals(2000L, db.wrongItemDao().getByQuestion(1)!!.masteredAt)
        assertTrue(runCatching { repository.submitExamSession(snapshot, listOf(record), "exam-1") }.isFailure)
        assertEquals(3, db.questionAttemptDao().getAll().size)
        repository.createExamDraft(draft("exam-2"))
        val wrongAnswers = mapOf(1L to "B")
        val wrongSnapshot = snapshot.copy(answers = wrongAnswers, endedAt = 3000,
            grading = Grader.grade(questions, wrongAnswers, config))
        repository.submitExamSession(wrongSnapshot, listOf(ExamRecord(1, 1, "B", false)), "exam-2")
        val reopened = db.wrongItemDao().getByQuestion(1)!!
        assertEquals(2, reopened.wrongCount); assertEquals(0, reopened.correctStreak)
        assertNull(reopened.masteredAt); assertTrue(reopened.pendingReview)
    }

    @Test fun backupFiveRoundTripLegacyVersionsAndDamagedBackups() = runBlocking {
        repository.commitPracticeAnswer(commit(correct = false, token = "w"))
        repository.commitPracticeAnswer(commit(token = "a"))
        repository.commitPracticeAnswer(commit(token = "b", at = 300))
        val before = db.wrongItemDao().getByQuestion(1)!!
        val attempts = db.questionAttemptDao().getAll()
        val backup = repository.exportBackupJson(999)
        assertEquals(7, JSONObject(backup).getInt("version"))
        repository.restoreBackupJson(backup)
        assertEquals(before, db.wrongItemDao().getByQuestion(1))
        assertEquals(attempts, db.questionAttemptDao().getAll())
        repository.commitPracticeAnswer(commit(token = "b", at = 999))
        assertEquals(attempts.size, db.questionAttemptDao().getAll().size)
        val broken = JSONObject(backup).also { it.getJSONArray("wrongItems").getJSONObject(0).put("correctStreak", -1) }
        assertTrue(runCatching { repository.restoreBackupJson(broken.toString()) }.isFailure)
        assertEquals(before, db.wrongItemDao().getByQuestion(1))
        assertEquals(attempts, db.questionAttemptDao().getAll())
        for (version in 1..4) {
            val legacy = JSONObject(backup).put("version", version)
            legacy.getJSONArray("wrongItems").getJSONObject(0).apply { remove("correctStreak"); remove("masteredAt") }
            repository.restoreBackupJson(legacy.toString())
            val restored = db.wrongItemDao().getByQuestion(1)!!
            assertEquals(before.wrongCount, restored.wrongCount); assertEquals(before.lastWrongAt, restored.lastWrongAt)
            assertEquals(0, restored.correctStreak); assertNull(restored.masteredAt)
            assertTrue(restored.pendingReview); assertFalse(restored.corrected)
            if (version >= 2) assertEquals(attempts, db.questionAttemptDao().getAll())
        }
    }
}
