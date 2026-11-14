package com.xjb.linti.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.domain.Question
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
class ChapterRepositoryTest {
    private lateinit var db: LintiDatabase
    private lateinit var repo: LintiRepository
    private var bank = 0L
    private var otherBank = 0L
    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LintiDatabase::class.java).allowMainThreadQueries().build()
        repo = LintiRepository(db)
        bank = repo.createBank("虚构题库甲", now = 1)
        otherBank = repo.createBank("虚构题库乙", now = 1)
    }
    @After fun close() { db.close() }
    private fun question(bankId: Long = bank, chapterId: Long? = null) = Question(bankId = bankId, chapterId = chapterId, stem = "虚构题目", answer = "A")

    @Test fun createRenameRejectsBlankAndDuplicatesWithinBank() = runBlocking {
        val first = repo.createChapter(bank, " 第一章 ", 1)
        assertEquals("第一章", repo.getChapters(bank).single().name)
        assertTrue(runCatching { repo.createChapter(bank, "第一章") }.isFailure)
        assertTrue(runCatching { repo.createChapter(bank, "  ") }.isFailure)
        val second = repo.createChapter(bank, "第二章")
        assertTrue(runCatching { repo.renameChapter(second, " 第一章 ") }.isFailure)
        repo.renameChapter(first, " 新名称 ")
        assertEquals("新名称", repo.getChapters(bank).first().name)
        repo.createChapter(otherBank, "新名称")
        assertEquals(1, repo.getChapters(otherBank).size)
    }
    @Test fun reorderAndDeleteReturnsQuestionsToUncategorized() = runBlocking {
        val a = repo.createChapter(bank, "甲")
        val b = repo.createChapter(bank, "乙")
        val c = repo.createChapter(bank, "丙")
        val q = repo.saveQuestion(question(chapterId = b))
        repo.moveChapter(c, -1)
        assertEquals(listOf(a, c, b), repo.getChapters(bank).map { it.id })
        repo.deleteChapter(b)
        assertNull(repo.getQuestion(q)!!.chapterId)
        assertEquals(listOf(a, c), repo.getChapters(bank).map { it.id })
    }
    @Test fun batchMoveIsAtomicAndRejectsCrossBankReferences() = runBlocking {
        val a = repo.createChapter(bank, "甲")
        val foreign = repo.createChapter(otherBank, "外部章节")
        val first = repo.saveQuestion(question())
        val second = repo.saveQuestion(question())
        val outsider = repo.saveQuestion(question(otherBank))
        assertTrue(runCatching { repo.saveQuestion(question(chapterId = foreign)) }.isFailure)
        assertTrue(runCatching { repo.moveQuestions(bank, setOf(first, outsider), a) }.isFailure)
        assertNull(repo.getQuestion(first)!!.chapterId)
        assertTrue(runCatching { repo.moveQuestions(bank, setOf(first, second), foreign) }.isFailure)
        repo.moveQuestions(bank, setOf(first, second), a)
        assertEquals(listOf(a, a), repo.getQuestions(bank).map { it.chapterId })
        repo.moveQuestions(bank, setOf(first, second), null)
        assertTrue(repo.getQuestions(bank).all { it.chapterId == null })
        repo.saveQuestion(repo.getQuestion(first)!!.copy(chapterId = a))
        repo.saveQuestion(repo.getQuestion(first)!!.copy(bankId = otherBank))
        assertNull(repo.getQuestion(first)!!.chapterId)
    }
    @Test fun failedSecondBatchRollsBackTheFirstBatch() = runBlocking {
        val chapter = repo.createChapter(bank, "虚构章节")
        val ids = db.questionDao().insertAll((1..501).map { question().toEntity() }).toCollection(linkedSetOf())
        val last = ids.last()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_last_move BEFORE UPDATE OF chapter_id ON questions WHEN OLD.id = $last BEGIN SELECT RAISE(ABORT, 'fixture'); END")
        assertTrue(runCatching { repo.moveQuestions(bank, ids, chapter) }.isFailure)
        assertTrue(repo.getQuestions(bank).all { it.chapterId == null })
    }
    @Test fun deletingBankCascadesChapters() = runBlocking {
        repo.createChapter(bank, "虚构章节")
        db.openHelper.writableDatabase.execSQL("DELETE FROM question_banks WHERE id = $bank")
        assertTrue(repo.getChapters(bank).isEmpty())
    }
    @Test fun backupRestoresChapterOrderAndRemapsReferences() = runBlocking {
        val a = repo.createChapter(bank, "甲", 11)
        val b = repo.createChapter(bank, "乙", 12)
        repo.moveChapter(b, -1, 13)
        val q1 = repo.saveQuestion(question(chapterId = a))
        val q2 = repo.saveQuestion(question(chapterId = b))
        val backup = repo.exportBackupJson(99)
        repo.restoreBackupJson(backup)
        val chapters = repo.getChapters(bank)
        assertEquals(listOf("乙", "甲"), chapters.map { it.name })
        assertEquals(chapters[1].id, repo.getQuestion(q1)!!.chapterId)
        assertEquals(chapters[0].id, repo.getQuestion(q2)!!.chapterId)
        assertNotEquals(a, chapters[1].id)
        assertEquals(listOf(0, 1), chapters.map { it.sortOrder })
    }
    @Test fun oldBackupWithoutChapterFieldsRestoresUncategorized() = runBlocking {
        val a = repo.createChapter(bank, "甲")
        val q = repo.saveQuestion(question(chapterId = a))
        val json = JSONObject(repo.exportBackupJson()).put("version", 3)
        json.remove("chapters")
        val qs = json.getJSONArray("questions")
        for (i in 0 until qs.length()) qs.getJSONObject(i).remove("chapterId")
        repo.restoreBackupJson(json.toString())
        assertTrue(repo.getChapters(bank).isEmpty())
        assertNull(repo.getQuestion(q)!!.chapterId)
    }
    @Test fun invalidBackupDoesNotDestroyExistingData() = runBlocking {
        val a = repo.createChapter(bank, "甲")
        val q = repo.saveQuestion(question(chapterId = a))
        val json = JSONObject(repo.exportBackupJson())
        json.getJSONArray("questions").getJSONObject(0).put("chapterId", 99999)
        assertTrue(runCatching { repo.restoreBackupJson(json.toString()) }.isFailure)
        assertEquals(a, repo.getQuestion(q)!!.chapterId)
        assertEquals("甲", repo.getChapters(bank).single().name)
    }
}
