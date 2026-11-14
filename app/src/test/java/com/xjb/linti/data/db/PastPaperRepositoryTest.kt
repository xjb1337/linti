package com.xjb.linti.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.PracticeCommit
import com.xjb.linti.domain.PastPaperSource
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuizSession
import com.xjb.linti.domain.groupPastPapersByYear
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
class PastPaperRepositoryTest {
    private lateinit var db: LintiDatabase
    private lateinit var repo: LintiRepository
    private var bank = 0L
    private var otherBank = 0L

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LintiDatabase::class.java).allowMainThreadQueries().build()
        repo = LintiRepository(db)
        bank = repo.createBank("虚构官方发布考生回忆题库")
        otherBank = repo.createBank("虚构另一题库")
    }
    @After fun close() = db.close()
    private suspend fun question(number: String, bankId: Long = bank) = repo.saveQuestion(Question(
        bankId = bankId, sourceNumber = number, stem = "虚构题 $number", answer = "A", sourceFileName = "虚构官方发布考生回忆.pdf",
    ))

    @Test fun groupByYearAndNameAndDefaultSourceNeverInferred() = runBlocking {
        val older = repo.createPastPaper(bank, 2022, "虚构旧卷")
        val beta = repo.createPastPaper(bank, 2024, "虚构B卷")
        val alpha = repo.createPastPaper(bank, 2024, " 虚构A卷 ")
        val q = question("1")
        repo.addQuestionsToPastPaper(alpha, listOf(q))
        val groups = groupPastPapersByYear(repo.getPastPapers(bank))
        assertEquals(listOf(2024, 2022), groups.keys.toList())
        assertEquals(listOf(alpha, beta, older), groups.values.flatten().map { it.id })
        assertEquals("虚构A卷", repo.getPastPaper(alpha)!!.name)
        assertEquals(PastPaperSource.UNVERIFIED, repo.getPastPaper(alpha)!!.source)
        assertEquals(1, repo.getPastPaper(alpha)!!.questionCount)
        repo.updatePastPaper(alpha, 2024, "甲卷", PastPaperSource.RECALLED, " 虚构备注 ")
        assertEquals(PastPaperSource.RECALLED, repo.getPastPaper(alpha)!!.source)
        assertEquals("虚构备注", repo.getPastPaper(alpha)!!.sourceNote)
        assertTrue(runCatching { repo.createPastPaper(bank, 2024, "  ") }.isFailure)
    }

    @Test fun numberUniquenessAndBankMembershipAreAtomicAndScopedToOnePaper() = runBlocking {
        val first = repo.createPastPaper(bank, 2024, "甲")
        val second = repo.createPastPaper(bank, 2024, "乙")
        val a = question(" 7 ")
        val b = question("7")
        val c = question("8")
        val foreign = question("9", otherBank)
        repo.addQuestionsToPastPaper(first, listOf(a))
        repo.addQuestionsToPastPaper(second, listOf(a, c))
        assertTrue(runCatching { repo.addQuestionsToPastPaper(first, listOf(c, b)) }.exceptionOrNull()!!.message!!.contains("7"))
        assertEquals(listOf(a), repo.getPastPaperItems(first).map { it.questionId })
        assertTrue(runCatching { repo.addQuestionsToPastPaper(first, listOf(c, foreign)) }.isFailure)
        assertTrue(runCatching { repo.addQuestionsToPastPaper(first, listOf(a)) }.isFailure)
        repo.addQuestionsToPastPaper(first, listOf(c))
        assertTrue(runCatching { repo.updatePastPaperNumber(first, c, " 7 ") }.isFailure)
        assertTrue(runCatching { repo.updatePastPaperNumber(first, c, "  ") }.isFailure)
        repo.updatePastPaperNumber(first, c, " 二 ")
        assertEquals(listOf("7", "二"), repo.getPastPaperItems(first).map { it.originalNumber })
        val blank = question("")
        val third = repo.createPastPaper(bank, 2021, "丙")
        repo.addQuestionsToPastPaper(third, listOf(blank))
        assertEquals("1", repo.getPastPaperItems(third).single().originalNumber)
        val conflict = question("")
        repo.updatePastPaperNumber(third, blank, "2")
        assertTrue(runCatching { repo.addQuestionsToPastPaper(third, listOf(conflict)) }.isFailure)
        assertEquals(1, repo.getPastPaperItems(third).size)
    }

    @Test fun orderingRemovalDeleteAndMovingQuestionReindexAllAffectedPapers() = runBlocking {
        val first = repo.createPastPaper(bank, 2024, "甲")
        val second = repo.createPastPaper(bank, 2023, "乙")
        val a = question("甲")
        val b = question("乙")
        val c = question("丙")
        val d = question("丁")
        repo.addQuestionsToPastPaper(first, listOf(c, a, b, d))
        repo.addQuestionsToPastPaper(second, listOf(b, c, a))
        assertEquals(listOf(c, a, b, d), repo.getPastPaperQuestions(first).map { it.id })
        repo.movePastPaperItem(first, b, -1)
        repo.movePastPaperItem(first, c, 1)
        assertEquals(listOf(b, c, a, d), repo.getPastPaperQuestions(first).map { it.id })
        repo.removePastPaperItem(first, c)
        assertEquals(listOf(1, 2, 3), repo.getPastPaperItems(first).map { it.position })
        repo.deleteQuestion(b)
        assertEquals(listOf(a, d), repo.getPastPaperQuestions(first).map { it.id })
        assertEquals(listOf(c, a), repo.getPastPaperQuestions(second).map { it.id })
        assertEquals(listOf(1, 2), repo.getPastPaperItems(first).map { it.position })
        assertEquals(listOf(1, 2), repo.getPastPaperItems(second).map { it.position })
        repo.saveQuestion(repo.getQuestion(a)!!.copy(bankId = otherBank))
        assertEquals(listOf(d), repo.getPastPaperQuestions(first).map { it.id })
        assertEquals(listOf(c), repo.getPastPaperQuestions(second).map { it.id })
        assertEquals(1, repo.getPastPaperItems(first).single().position)
        repo.deletePastPaper(first)
        assertNotNull(repo.getQuestion(d))
        db.openHelper.writableDatabase.execSQL("DELETE FROM question_banks WHERE id = $bank")
        assertTrue(repo.getPastPapers(bank).isEmpty())
        assertTrue(db.pastPaperDao().getAllItems().isEmpty())
        assertNotNull(repo.getQuestion(a))
    }

    @Test fun paperPracticeCountsMasteryWithoutSequenceProgress() = runBlocking {
        val q = question("1")
        fun commit(correct: Boolean, token: String) = PracticeCommit(bank, PracticeMode.PAPER, q, bank,
            if (correct) "A" else "B", correct, 0, 100, 0, submissionId = token)
        repo.commitPracticeAnswer(commit(false, "虚构错"))
        repo.commitPracticeAnswer(commit(true, "虚构对一"))
        repo.commitPracticeAnswer(commit(true, "虚构对二"))
        assertEquals(2, db.wrongItemDao().getByQuestion(q)!!.correctStreak)
        assertFalse(db.wrongItemDao().getByQuestion(q)!!.pendingReview)
        assertTrue(QuizSession.supportsRecite(PracticeMode.PAPER))
        assertNull(repo.getSequenceProgress(bank))
        assertEquals(listOf("PAPER", "PAPER", "PAPER"), db.questionAttemptDao().getAll().map { it.mode })
        assertEquals(3, repo.studyAggregate(0, java.time.ZoneId.of("UTC")).totalAnswered)
        val backup = repo.exportBackupJson()
        repo.restoreBackupJson(backup)
        assertEquals(listOf("PAPER", "PAPER", "PAPER"), db.questionAttemptDao().getAll().map { it.mode })
        assertEquals(3, repo.studyAggregate(0, java.time.ZoneId.of("UTC")).totalAnswered)
        assertNull(repo.getSequenceProgress(bank))
    }

    @Test fun backupEightRemapsPaperIdsAndOldVersionsNeverRestorePapers() = runBlocking {
        val paper = repo.createPastPaper(bank, 2024, "虚构甲卷", PastPaperSource.OFFICIAL, "虚构来源")
        val other = repo.createPastPaper(bank, 2023, "虚构乙卷")
        val a = question("1")
        val b = question("2")
        repo.addQuestionsToPastPaper(paper, listOf(b, a))
        repo.addQuestionsToPastPaper(other, listOf(a))
        val backup = repo.exportBackupJson()
        assertEquals(10, JSONObject(backup).getInt("version"))
        repo.restoreBackupJson(backup)
        val restored = repo.getPastPapers(bank).first { it.name == "虚构甲卷" }
        assertNotEquals(paper, restored.id)
        assertEquals(PastPaperSource.OFFICIAL, restored.source)
        assertEquals("虚构来源", restored.sourceNote)
        assertEquals(listOf(b, a), repo.getPastPaperQuestions(restored.id).map { it.id })
        assertEquals(listOf("2", "1"), repo.getPastPaperItems(restored.id).map { it.originalNumber })
        for (version in 1..7) {
            val legacy = JSONObject(backup).put("version", version)
            // 旧版即便带有未知字段也不能恢复真题卷。
            repo.restoreBackupJson(legacy.toString())
            assertTrue("version=$version", repo.getPastPapers(bank).isEmpty())
            assertNotNull(repo.getQuestion(a))
        }
    }

    @Test fun corruptBackupsAreRejectedWithoutChangingExistingData() = runBlocking {
        val paper = repo.createPastPaper(bank, 2024, "虚构卷")
        val a = question("1")
        val b = question("2")
        val foreign = question("3", otherBank)
        repo.addQuestionsToPastPaper(paper, listOf(a, b))
        val backup = repo.exportBackupJson()
        val invalid = listOf(
            JSONObject(backup).apply { remove("pastPapers") },
            JSONObject(backup).apply { remove("questions") },
            JSONObject(backup).apply { put("version", 7); remove("questions") },
            JSONObject(backup).apply { put("version", 1); put("banks", JSONArray().put("损坏")) },
            JSONObject(backup).apply { put("favorites", "损坏") },
            JSONObject(backup).apply { getJSONArray("questions").getJSONObject(0).remove("stem") },
            JSONObject(backup).apply { getJSONArray("questions").getJSONObject(0).put("stem", 123) },
            JSONObject(backup).apply { put("pastPaperItems", JSONArray().put("损坏")) },
            JSONObject(backup).apply { getJSONArray("pastPapers").getJSONObject(0).put("sourceCategory", "自动推断") },
            JSONObject(backup).apply { getJSONArray("pastPapers").getJSONObject(0).put("name", "  ") },
            JSONObject(backup).apply { getJSONArray("pastPapers").getJSONObject(0).put("year", 2024.5) },
            JSONObject(backup).apply { getJSONArray("questions").put(getJSONArray("questions").getJSONObject(0)) },
            JSONObject(backup).apply { getJSONArray("pastPaperItems").getJSONObject(0).put("questionId", foreign) },
            JSONObject(backup).apply { getJSONArray("pastPaperItems").getJSONObject(1).put("originalNumber", "1") },
            JSONObject(backup).apply { getJSONArray("pastPaperItems").getJSONObject(1).put("position", 7) },
        )
        invalid.forEach { broken ->
            assertTrue(runCatching { repo.restoreBackupJson(broken.toString()) }.isFailure)
            assertEquals("虚构卷", repo.getPastPaper(paper)!!.name)
            assertEquals(listOf(a, b), repo.getPastPaperQuestions(paper).map { it.id })
            assertNotNull(repo.getQuestion(foreign))
        }
    }
}
