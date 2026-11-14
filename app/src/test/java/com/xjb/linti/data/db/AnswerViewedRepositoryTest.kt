package com.xjb.linti.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.backup.BackupCodec
import com.xjb.linti.data.backup.BackupPayload
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.PracticeCommit
import com.xjb.linti.domain.PracticeMode
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
class AnswerViewedRepositoryTest {
    private lateinit var db: LintiDatabase
    private lateinit var repository: LintiRepository

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LintiDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = LintiRepository(db) { 3 }
        db.questionBankDao().insert(QuestionBankEntity(id = 1, name = "虚构题库", createdAt = 1, updatedAt = 1))
        db.questionDao().insert(QuestionEntity(id = 1, bankId = 1, sourceNumber = "1", type = "SINGLE", stem = "虚构题", optionsJson = "[]", answer = "A", explanation = "虚构解析", sourceFileName = "虚构来源", createdAt = 1, updatedAt = 1))
        Unit
    }

    @After fun close() = db.close()

    private suspend fun commit(token: String, correct: Boolean, viewed: Boolean, at: Long) {
        repository.commitPracticeAnswer(PracticeCommit(
            bankId = 1, mode = PracticeMode.SEQUENCE, questionId = 1, questionBankId = 1,
            answer = if (correct) "A" else "B", isCorrect = correct, currentIndex = 0,
            answeredAt = at, roundStartedAt = 0, answerViewed = viewed, submissionId = token,
        ))
    }

    @Test fun viewedCorrectRemainsCorrectInRecordButNotInAccuracyOrStreak() = runBlocking {
        commit("虚构错题", false, false, 100)
        commit("虚构独立答对", true, false, 200)
        assertEquals(1, db.wrongItemDao().getByQuestion(1)!!.correctStreak)
        commit("虚构看后答对", true, true, 300)
        val viewed = db.questionAttemptDao().getAll().first { it.submissionId == "虚构看后答对" }
        assertTrue(viewed.isCorrect)
        assertTrue(viewed.answerViewed)
        assertEquals(1, db.wrongItemDao().getByQuestion(1)!!.correctStreak)
        val row = repository.statsFor(listOf(1)).getValue(1)
        assertEquals(3, row.totalCount)
        assertEquals(1, row.correctCount)
        val aggregate = repository.studyAggregate(0, ZoneId.of("UTC"))
        assertEquals(3, aggregate.totalAnswered)
        assertEquals(1, aggregate.totalCorrect)
        assertEquals(1.0 / 3.0, aggregate.accuracy, 0.0001)

        commit("虚构看后答错", false, true, 400)
        val wrong = db.wrongItemDao().getByQuestion(1)!!
        assertEquals(2, wrong.wrongCount)
        assertEquals(0, wrong.correctStreak)
        assertTrue(wrong.pendingReview)
        assertEquals(1, repository.statsFor(listOf(1)).getValue(1).correctCount)
        assertEquals(4, repository.studyAggregate(0, ZoneId.of("UTC")).totalAnswered)
    }

    @Test fun versionSevenRoundTripAndInvalidAttemptLeaveDatabaseUntouched() = runBlocking {
        commit("虚构提交一", true, true, 100)
        commit("虚构提交二", false, false, 200)
        val before = db.questionAttemptDao().getAll()
        val backup = repository.exportBackupJson(300)
        val json = JSONObject(backup)
        assertEquals(12, json.getInt("version"))
        assertTrue(json.getJSONArray("questionAttempts").getJSONObject(1).getBoolean("answerViewed"))
        repository.restoreBackupJson(backup)
        assertEquals(before, db.questionAttemptDao().getAll())
        val invalid = listOf(
            JSONObject(backup).apply { getJSONArray("questionAttempts").getJSONObject(0).remove("answerViewed") },
            JSONObject(backup).apply { getJSONArray("questionAttempts").getJSONObject(0).put("answerViewed", "true") },
            JSONObject(backup).apply { put("questionAttempts", JSONArray().put("虚构错误")) },
            JSONObject(backup).apply { remove("questionAttempts") },
            JSONObject(backup).apply { getJSONArray("questionAttempts").getJSONObject(0).put("questionId", 999) },
        )
        invalid.forEach { broken ->
            assertTrue(runCatching { repository.restoreBackupJson(broken.toString()) }.isFailure)
            assertEquals(before, db.questionAttemptDao().getAll())
            assertEquals("虚构题", db.questionDao().getById(1)!!.stem)
        }
    }

    @Test fun olderVersionsIgnoreViewedFieldAndVersionOneBackfillsAsFalse() = runBlocking {
        commit("虚构旧答题", true, true, 100)
        val backup = repository.exportBackupJson(300)
        for (version in 2..6) {
            val legacy = JSONObject(backup).put("version", version)
            // 即使旧格式被额外塞入同名字段，也按旧格式恢复。
            repository.restoreBackupJson(legacy.toString())
            val restored = db.questionAttemptDao().getAll().single()
            assertTrue(restored.isCorrect)
            assertFalse("version=$version", restored.answerViewed)
        }
        val session = QuizSessionEntity(
            id = 7, bankId = 1, bankName = "虚构题库", mode = "SEQUENCE", configJson = "{}",
            questionIdsJson = "[1]", answersJson = "{\"1\":\"A\"}", flagsJson = "[]", bookmarksJson = "[]",
            startedAt = 1, endedAt = 2, status = "COMPLETED", answeredCount = 1, correctCount = 1,
        )
        val legacyV1 = BackupCodec.encode(BackupPayload(
            version = 1, exportedAt = 300, banks = db.questionBankDao().getAll(),
            questions = db.questionDao().getAll(), sessions = listOf(session),
        ))
        repository.restoreBackupJson(legacyV1)
        val backfilled = db.questionAttemptDao().getAll().single()
        assertTrue(backfilled.isCorrect)
        assertFalse(backfilled.answerViewed)
    }
}
