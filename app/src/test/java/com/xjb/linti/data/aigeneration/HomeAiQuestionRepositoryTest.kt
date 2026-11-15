package com.xjb.linti.data.aigeneration

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.explanation.AiExplanationConfig
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.data.explanation.FakeExplanationSettings
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class HomeAiQuestionRepositoryTest {
    private lateinit var db: LintiDatabase
    private val valid = AiDraftFields("SINGLE", "虚构题", listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")), "A", "虚构解析")
    private var fail = false
    private var noMaterialResults = false
    private var waiting: CompletableDeferred<Unit>? = null
    private val started = CompletableDeferred<Unit>()
    private val fake = object : AiQuestionClient {
        override suspend fun generate(source: AiQuestionSource, type: QuestionType, count: Int, config: AiExplanationConfig): List<AiDraftFields> {
            started.complete(Unit)
            waiting?.await()
            if (fail) throw AiExplanationException(AiExplanationError.RESPONSE)
            return List(count) { valid }
        }
        override suspend fun generateMaterial(batch: MaterialBatch, config: AiExplanationConfig): List<AiDraftFields> {
            if (fail) throw AiExplanationException(AiExplanationError.RESPONSE)
            return if (noMaterialResults) emptyList() else listOf(valid.copy(material = true, materialText = batch.text, sourceExcerpt = batch.text))
        }
    }
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), LintiDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }

    @Test fun transactionRollsBackNewBankIfDraftWritePreparationFails() = runBlocking {
        val random = object : Random() { override fun nextBits(bitCount: Int): Int = error("虚构写入失败") }
        val ai = AiQuestionRepository(db, FakeExplanationSettings(), fake, random)
        assertTrue(runCatching { ai.generate(AiQuestionTarget.New("虚构题库"), "虚构主题", emptyList(), QuestionType.SINGLE, 1) }.isFailure)
        assertTrue(db.questionBankDao().getAll().isEmpty())
    }

    @Test fun failedEmptyAndCancelledRequestsNeverCreateBank() = runBlocking {
        val ai = AiQuestionRepository(db, FakeExplanationSettings(), fake)
        fail = true
        assertTrue(runCatching { ai.generate(AiQuestionTarget.New("虚构题库"), "虚构主题", emptyList(), QuestionType.SINGLE, 1) }.isFailure)
        val batch = MaterialBatch(1, "虚构材料", mapOf(QuestionType.SINGLE to 1))
        assertTrue(runCatching { ai.generateMaterialBatch(AiQuestionTarget.New("虚构题库"), batch) }.isFailure)
        fail = false; noMaterialResults = true
        assertTrue(runCatching { ai.generateMaterialBatch(AiQuestionTarget.New("虚构题库"), batch) }.isFailure)
        assertTrue(db.questionBankDao().getAll().isEmpty())
    }

    @Test fun cancellationBeforeResponseNeverCreatesBank() = runBlocking {
        val ai = AiQuestionRepository(db, FakeExplanationSettings(), fake)
        waiting = CompletableDeferred()
        val job = async { ai.generate(AiQuestionTarget.New("虚构题库"), "虚构主题", emptyList(), QuestionType.SINGLE, 1) }
        started.await()
        job.cancelAndJoin()
        assertTrue(db.questionBankDao().getAll().isEmpty())
    }

    @Test fun newBankCannotUseReferences() = runBlocking {
        val ai = AiQuestionRepository(db, FakeExplanationSettings(), fake)
        assertTrue(runCatching { ai.generate(AiQuestionTarget.New("虚构题库"), null, listOf(1), QuestionType.SINGLE, 1) }.isFailure)
        assertTrue(db.questionBankDao().getAll().isEmpty())
        assertFalse(started.isCompleted)
    }
}
