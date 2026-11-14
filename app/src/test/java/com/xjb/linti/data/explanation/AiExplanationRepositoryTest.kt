package com.xjb.linti.data.explanation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.db.AiExplanationEntity
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.db.QuestionBankEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.domain.Question
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
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
class AiExplanationRepositoryTest {
    private lateinit var db: LintiDatabase
    private lateinit var repo: LintiRepository
    private lateinit var explanations: AiExplanationRepository
    private val settings = FakeExplanationSettings()
    private var calls = 0
    private var result = "第一次虚构解析"
    private var fail = false
    private val fake = object : AiExplanationClient {
        override suspend fun explain(question: Question, config: AiExplanationConfig): String {
            calls++
            if (fail) throw AiExplanationException(AiExplanationError.TIMEOUT)
            return result
        }
    }

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LintiDatabase::class.java).allowMainThreadQueries().build()
        repo = LintiRepository(db)
        db.questionBankDao().insert(QuestionBankEntity(1, "虚构题库", 1, 1))
        db.questionDao().insert(QuestionEntity(id = 4, bankId = 1, sourceNumber = "1", type = "SINGLE", stem = "虚构题", optionsJson = "[]", answer = "A", explanation = "原解析", sourceFileName = "虚构", createdAt = 1, updatedAt = 5))
        explanations = AiExplanationRepository(db, settings, fake) { 100 }
    }
    @After fun close() = db.close()

    @Test fun generationOverwriteFailureDeleteAndCascade() = runBlocking {
        assertEquals(0, calls)
        explanations.generate(4)
        assertEquals("第一次虚构解析", db.aiExplanationDao().get(4)!!.content)
        assertEquals("原解析", db.questionDao().getById(4)!!.explanation)
        result = "第二次虚构解析"
        explanations.generate(4)
        assertEquals("第二次虚构解析", db.aiExplanationDao().get(4)!!.content)
        fail = true
        assertTrue(runCatching { explanations.generate(4) }.isFailure)
        assertEquals("第二次虚构解析", db.aiExplanationDao().get(4)!!.content)
        fail = false; result = " "
        assertTrue(runCatching { explanations.generate(4) }.isFailure)
        assertEquals("第二次虚构解析", db.aiExplanationDao().get(4)!!.content)
        val q = db.questionDao().getById(4)!!
        db.questionDao().update(q.copy(stem = "已修改", updatedAt = 6))
        assertTrue(explanations.observe(4).first()!!.stale)
        assertEquals("原解析", db.questionDao().getById(4)!!.explanation)
        explanations.delete(4)
        assertNull(db.aiExplanationDao().get(4))
        result = "恢复虚构解析"; explanations.generate(4)
        assertFalse(explanations.observe(4).first()!!.stale)
        repo.deleteQuestion(4)
        assertNull(db.aiExplanationDao().get(4))
    }

    @Test fun updateDuringRequestKeepsGeneratedVersionAndShowsStale() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val slow = AiExplanationRepository(db, settings, object : AiExplanationClient {
            override suspend fun explain(question: Question, config: AiExplanationConfig): String {
                started.complete(Unit); finish.await(); return "虚构解析"
            }
        })
        val pending = async { slow.generate(4) }
        started.await()
        db.questionDao().update(db.questionDao().getById(4)!!.copy(updatedAt = 9))
        finish.complete(Unit); pending.await()
        assertEquals(5L, db.aiExplanationDao().get(4)!!.questionUpdatedAt)
        assertTrue(slow.observe(4).first()!!.stale)
    }

    @Test fun backupNineRoundTripsAndOldVersionsNeverRestoreAi() = runBlocking {
        explanations.generate(4)
        val backup = repo.exportBackupJson(100)
        assertEquals(10, JSONObject(backup).getInt("version"))
        repo.restoreBackupJson(backup)
        assertEquals(AiExplanationEntity(4, "第一次虚构解析", "fictional-model", "CUSTOM", 5, 100), db.aiExplanationDao().get(4))
        assertEquals("原解析", db.questionDao().getById(4)!!.explanation)
        for (version in 1..8) {
            db.aiExplanationDao().upsert(AiExplanationEntity(4, "旧记录", "模型", "CUSTOM", 5, 100))
            val old = JSONObject(backup).put("version", version)
            repo.restoreBackupJson(old.toString())
            assertTrue("版本 $version", db.aiExplanationDao().getAll().isEmpty())
            assertNotNull(db.questionDao().getById(4))
        }
    }

    @Test fun invalidBackupDoesNotDestroyCurrentData() = runBlocking {
        explanations.generate(4)
        val backup = repo.exportBackupJson()
        val corrupt = listOf(
            JSONObject(backup).apply { remove("aiExplanations") },
            JSONObject(backup).put("aiExplanations", "broken"),
            JSONObject(backup).put("aiExplanations", JSONArray().put("broken")),
            JSONObject(backup).apply { getJSONArray("aiExplanations").getJSONObject(0).put("questionId", 999) },
            JSONObject(backup).apply { val a = getJSONArray("aiExplanations"); a.put(JSONObject(a.getJSONObject(0).toString())) },
            JSONObject(backup).apply { getJSONArray("aiExplanations").getJSONObject(0).put("content", " ") },
            JSONObject(backup).apply { getJSONArray("aiExplanations").getJSONObject(0).put("createdAt", "100") },
        )
        corrupt.forEach { bad ->
            assertTrue(runCatching { repo.restoreBackupJson(bad.toString()) }.isFailure)
            assertEquals("第一次虚构解析", db.aiExplanationDao().get(4)!!.content)
            assertEquals("原解析", db.questionDao().getById(4)!!.explanation)
        }
    }
}
