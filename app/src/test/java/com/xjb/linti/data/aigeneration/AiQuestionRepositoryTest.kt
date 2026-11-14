package com.xjb.linti.data.aigeneration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.explanation.*
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.PracticeCommit
import com.xjb.linti.domain.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class AiQuestionRepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "ai-question-repository-test.db"
    private lateinit var db: LintiDatabase
    private lateinit var repo: LintiRepository
    private lateinit var ai: AiQuestionRepository
    private var bank = 0L
    private val settings = FakeExplanationSettings()
    private val valid = AiDraftFields("SINGLE", "虚构题干", listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")), "A", "虚构解析")
    private var results = listOf(valid)
    private var calls = 0
    private var sourceSeen: AiQuestionSource? = null
    private val fake = object : AiQuestionClient {
        override suspend fun generate(source: AiQuestionSource, type: QuestionType, count: Int, config: AiExplanationConfig): List<AiDraftFields> {
            calls++; sourceSeen = source; return results
        }
    }
    private fun open() {
        db = Room.databaseBuilder(context, LintiDatabase::class.java, dbName).allowMainThreadQueries().build()
        repo = LintiRepository(db); ai = AiQuestionRepository(db, settings, fake) { 123L }
    }
    @Before fun setup() = runBlocking { context.deleteDatabase(dbName); open(); bank = repo.createBank("虚构题库") }
    @After fun close() { db.close(); context.deleteDatabase(dbName) }
    private suspend fun generate() = ai.generate(bank, "虚构主题", emptyList(), QuestionType.SINGLE, results.size)

    @Test fun generatedDraftsShuffleOnlyAfterValidationAndKeepCorrectTextOnApproval() = runBlocking {
        var draws = 0
        val random = object : Random() {
            override fun nextBits(bitCount: Int): Int = error("不应请求原始随机位")
            override fun nextInt(until: Int): Int { draws++; return 0 }
        }
        ai = AiQuestionRepository(db, settings, fake, random) { 123L }
        val missingAnswer = valid.copy(answer = "")
        val referenced = valid.copy(explanation = "选 A，因为甲符合条件")
        results = listOf(missingAnswer, valid, referenced)
        generate()
        val saved = db.aiQuestionDraftDao().getByBank(bank).map { it.toDraft() }
        assertEquals(missingAnswer.copy(expectedType = "SINGLE"), saved[0].fields)
        assertTrue(saved[0].problems.contains("待补答案"))
        assertEquals(listOf("乙", "甲"), saved[1].fields.options.map { it.text })
        assertEquals(listOf("A", "B"), saved[1].fields.options.map { it.label })
        assertEquals("B", saved[1].fields.answer)
        assertTrue(saved[1].problems.isEmpty())
        assertEquals(referenced.copy(expectedType = "SINGLE"), saved[2].fields)
        assertEquals(1, draws)
        ai.approve(saved[1].id)
        val question = repo.getQuestions(bank).single()
        assertEquals("B", question.answer)
        assertEquals("甲", question.options.single { it.label == question.answer }.text)
    }

    @Test fun persistenceValidationAtomicApprovalDiscardAndCascade() = runBlocking {
        results = listOf(valid, valid.copy(answer = ""))
        generate()
        db.close(); open()
        val saved = db.aiQuestionDraftDao().getByBank(bank).map { it.toDraft() }
        assertEquals(2, saved.size); assertEquals("", saved[1].fields.answer); assertTrue(saved[1].problems.contains("待补答案"))
        assertTrue(runCatching { ai.approveAll(saved.map { it.id }) }.isFailure)
        assertTrue(db.questionDao().getByBank(bank).isEmpty()); assertEquals(2, db.aiQuestionDraftDao().getByBank(bank).size)
        ai.update(saved[1].id, saved[1].fields.copy(answer = "B"))
        assertTrue(db.aiQuestionDraftDao().get(saved[1].id)!!.toDraft().problems.isEmpty())
        ai.approveAll(saved.map { it.id })
        assertTrue(db.aiQuestionDraftDao().getByBank(bank).isEmpty())
        assertEquals(listOf("AI_GENERATED", "AI_GENERATED"), db.questionDao().getByBank(bank).map { it.origin })
        val aiQuestion = repo.getQuestions(bank).first()
        val exam = ExamBuilder().build(ExamConfig(singleCount = 1, passScore = 0.0), listOf(aiQuestion))
        assertNull(exam.error)
        assertEquals(QuestionOrigin.AI_GENERATED, exam.questions.single().origin)
        val wrongAnswer = aiQuestion.options.first { it.label != aiQuestion.answer }.label
        repo.commitPracticeAnswer(PracticeCommit(bank, PracticeMode.SEQUENCE, aiQuestion.id, bank, wrongAnswer, false, 0, 100, 0))
        assertNotNull(db.wrongItemDao().getByQuestion(aiQuestion.id))
        val paper = repo.createPastPaper(bank, 2024, "虚构卷")
        assertTrue(runCatching { repo.addQuestionsToPastPaper(paper, listOf(db.questionDao().getByBank(bank).first().id)) }.isFailure)
        assertTrue(repo.getPastPaperItems(paper).isEmpty())
        results = listOf(valid, valid)
        generate()
        val again = db.aiQuestionDraftDao().getByBank(bank)
        ai.discard(again[0].id)
        assertEquals(1, db.aiQuestionDraftDao().getByBank(bank).size)
        db.openHelper.writableDatabase.execSQL("DELETE FROM question_banks WHERE id = $bank")
        assertTrue(db.aiQuestionDraftDao().getByBank(bank).isEmpty())
    }

    @Test fun wrongResultCountWritesNothingAndWrongTypeNeedsCorrection() = runBlocking {
        results = emptyList()
        val error = runCatching { ai.generate(bank, "虚构主题", emptyList(), QuestionType.SINGLE, 1) }.exceptionOrNull()
        assertEquals(AiExplanationError.RESPONSE, (error as AiExplanationException).reason)
        assertTrue(db.aiQuestionDraftDao().getByBank(bank).isEmpty())
        results = listOf(valid.copy(type = "MULTI", answer = "AB"))
        ai.generate(bank, "虚构主题", emptyList(), QuestionType.SINGLE, 1)
        val draft = db.aiQuestionDraftDao().getByBank(bank).single().toDraft()
        assertTrue(draft.problems.any { it.contains("不一致") })
        assertEquals(results.single().copy(expectedType = "SINGLE"), draft.fields)
        assertTrue(runCatching { ai.approve(draft.id) }.isFailure)
    }

    @Test fun referencesMustBelongToBankAndAreNotSentBeforeGenerate() = runBlocking {
        val other = repo.createBank("另一虚构题库")
        val first = repo.saveQuestion(Question(bankId = bank, stem = "虚构参照一", sourceFileName = "虚构"))
        val second = repo.saveQuestion(Question(bankId = bank, stem = "虚构参照二", sourceFileName = "虚构"))
        val foreign = repo.saveQuestion(Question(bankId = other, stem = "外题库", sourceFileName = "虚构"))
        assertEquals(0, calls)
        assertTrue(runCatching { ai.generate(bank, null, listOf(first, foreign), QuestionType.SINGLE, 1) }.isFailure)
        assertTrue(runCatching { ai.generate(bank, "主题", listOf(first), QuestionType.SINGLE, 1) }.isFailure)
        assertTrue(runCatching { ai.generate(bank, null, emptyList(), QuestionType.SINGLE, 1) }.isFailure)
        assertEquals(0, calls)
        ai.generate(bank, null, listOf(second, first), QuestionType.SINGLE, 1)
        assertEquals(1, calls)
        assertEquals(listOf(second, first), (sourceSeen as AiQuestionSource.References).questions.map { it.id })
    }

    @Test fun backupTenKeepsOriginOldBackupsResetOriginAndDraftsExcluded() = runBlocking {
        generate(); val id = db.aiQuestionDraftDao().getByBank(bank).single().id; ai.approve(id)
        generate()
        val backup = repo.exportBackupJson()
        val json = JSONObject(backup)
        assertEquals(12, json.getInt("version")); assertFalse(json.has("aiQuestionDrafts")); assertFalse(backup.contains("problems_json"))
        assertEquals("AI_GENERATED", json.getJSONArray("questions").getJSONObject(0).getString("origin"))
        val bad = JSONObject(backup); bad.getJSONArray("questions").getJSONObject(0).put("origin", "坏值")
        assertTrue(runCatching { repo.restoreBackupJson(bad.toString()) }.isFailure)
        assertEquals("AI_GENERATED", db.questionDao().getByBank(bank).single().origin)
        assertEquals(1, db.aiQuestionDraftDao().getByBank(bank).size)
        val missing = JSONObject(backup); missing.getJSONArray("questions").getJSONObject(0).remove("origin")
        assertTrue(runCatching { repo.restoreBackupJson(missing.toString()) }.isFailure)
        assertEquals(1, db.aiQuestionDraftDao().getByBank(bank).size)
        repo.restoreBackupJson(backup)
        assertEquals("AI_GENERATED", db.questionDao().getByBank(bank).single().origin)
        assertTrue(db.aiQuestionDraftDao().getByBank(bank).isEmpty())
        for (version in 1..9) {
            val legacy = JSONObject(backup).put("version", version)
            repo.restoreBackupJson(legacy.toString())
            assertEquals("version $version", "IMPORTED", db.questionDao().getByBank(bank).single().origin)
        }
    }

    @Test fun networkFailuresNeverInsertDrafts() = runBlocking {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val server = MockWebServer().apply { useHttps(tls.sslSocketFactory(), false); start() }
        try {
            settings.baseUrl = server.url("/v1").toString().trimEnd('/')
            ai = AiQuestionRepository(db, settings, OpenAiQuestionClient(OkHttpClient.Builder()
                .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
                .readTimeout(200, TimeUnit.MILLISECONDS).callTimeout(1, TimeUnit.SECONDS).build()))
            val responses = listOf(MockResponse().setResponseCode(401), MockResponse().setResponseCode(403),
                MockResponse().setResponseCode(500), MockResponse().setBody("坏 JSON"),
                MockResponse().setBody("""{"choices":[{"message":{"content":"坏 JSON"}}]}"""),
                MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            responses.forEach { response ->
                server.enqueue(response)
                assertTrue(runCatching { generate() }.isFailure)
                assertTrue(db.aiQuestionDraftDao().getByBank(bank).isEmpty())
            }
        } finally { server.shutdown() }
    }
}
