package com.xjb.linti.data.aigeneration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.data.explanation.FakeExplanationSettings
import com.xjb.linti.data.explanation.explanationConfig
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class MaterialGenerationTest {
    private lateinit var server: MockWebServer
    private lateinit var db: LintiDatabase
    private lateinit var client: OpenAiQuestionClient
    private val settings = FakeExplanationSettings("fictional-key", "https://api.deepseek.com/v1")
    private val batch = MaterialBatch(1, "虚构材料：植物喜光。", mapOf(QuestionType.ESSAY to 1))
    private val question = JSONObject().put("type", "ESSAY").put("stem", "虚构植物喜欢什么？")
        .put("options", JSONArray()).put("answer", "光照").put("acceptedAnswers", JSONArray())
        .put("explanation", "虚构解析").put("sourceExcerpt", "植物喜光")
    private fun response(content: String) = JSONObject().put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))).toString()
    private fun content() = JSONObject().put("questions", JSONArray().put(question)).toString()
    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer().apply { useHttps(tls.sslSocketFactory(), false); start() }
        client = OpenAiQuestionClient(OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build()) }
            .callTimeout(2, TimeUnit.SECONDS).build())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LintiDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun cleanup() { db.close(); server.shutdown() }

    @Test fun onlyMaterialAndCountsAreSentAndOfficialReasoningIsRestricted() = runBlocking {
        server.enqueue(MockResponse().setBody(response(content())))
        val result = client.generateMaterial(batch, explanationConfig(settings))
        val req = server.takeRequest(1, TimeUnit.SECONDS)!!
        val body = JSONObject(req.body.readUtf8())
        assertEquals("high", body.getString("reasoning_effort"))
        assertEquals("json_object", body.getJSONObject("response_format").getString("type"))
        val input = JSONObject(body.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals(setOf("material", "counts"), input.keySet())
        assertEquals(batch.text, input.getString("material")); assertEquals(1, input.getJSONObject("counts").getInt("ESSAY"))
        assertFalse(body.toString().contains("fictional-key"))
        assertFalse(body.toString().contains("image_url")); assertFalse(input.has("images"))
        assertTrue(AiDraftValidator.validate(result.single()).isEmpty())
        settings.baseUrl = "https://other.example/v1"
        server.enqueue(MockResponse().setBody(response(content())))
        client.generateMaterial(batch, explanationConfig(settings))
        assertFalse(JSONObject(server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8()).has("reasoning_effort"))
    }

    @Test fun badBatchDoesNotWriteAndOtherBatchAndApprovalPersistSource() = runBlocking {
        val repo = LintiRepository(db)
        val bank = repo.createBank("虚构题库")
        val ai = AiQuestionRepository(db, settings, client)
        server.enqueue(MockResponse().setBody(response("不是 JSON fictional-key")))
        val error = runCatching { ai.generateMaterialBatch(bank, batch) }.exceptionOrNull() as AiExplanationException
        assertEquals(AiExplanationError.RESPONSE, error.reason); assertFalse(error.toString().contains("fictional-key"))
        assertTrue(db.aiQuestionDraftDao().getByBank(bank).isEmpty())
        server.enqueue(MockResponse().setBody(response(content())))
        ai.generateMaterialBatch(bank, batch.copy(index = 2))
        val draft = db.aiQuestionDraftDao().getByBank(bank).single().toDraft()
        assertEquals("植物喜光", draft.fields.sourceExcerpt); assertTrue(draft.problems.isEmpty())
        ai.approve(draft.id)
        val saved = db.questionDao().getByBank(bank).single()
        assertEquals("植物喜光", saved.sourceExcerpt); assertEquals("AI_GENERATED", saved.origin); assertEquals("ESSAY", saved.type)
        val backup = repo.exportBackupJson()
        val bad = JSONObject(backup); bad.getJSONArray("questions").getJSONObject(0).put("acceptedAnswersJson", "[")
        assertTrue(runCatching { repo.restoreBackupJson(bad.toString()) }.isFailure)
        assertEquals(saved, db.questionDao().getByBank(bank).single())
        repo.restoreBackupJson(backup)
        assertEquals(saved, db.questionDao().getByBank(bank).single())
    }

    @Test fun failuresAndRedirectsAreSafeAndDoNotExposeKey() = runBlocking {
        val logs = StringBuilder()
        val logger = Logger.getLogger("okhttp3.mockwebserver.MockWebServer")
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { logs.append(record.message) }
            override fun flush() = Unit
            override fun close() = Unit
        }
        logger.addHandler(handler)
        try {
            for (status in listOf(401, 403, 302)) {
                server.enqueue(MockResponse().setResponseCode(status).setHeader("Location", server.url("/other")).setBody("fictional-key"))
                val error = runCatching { client.generateMaterial(batch, explanationConfig(settings)) }.exceptionOrNull() as AiExplanationException
                assertEquals(if (status == 302) AiExplanationError.SERVER else AiExplanationError.UNAUTHORIZED, error.reason)
                assertFalse(error.toString().contains("fictional-key")); assertNull(error.cause)
            }
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val timedOut = runCatching { client.generateMaterial(batch, explanationConfig(settings)) }.exceptionOrNull() as AiExplanationException
            assertEquals(AiExplanationError.TIMEOUT, timedOut.reason)
            val failing = OpenAiQuestionClient(OkHttpClient.Builder().addInterceptor { throw IOException("fictional-key") }.build())
            val network = runCatching { failing.generateMaterial(batch, explanationConfig(settings)) }.exceptionOrNull() as AiExplanationException
            assertEquals(AiExplanationError.NETWORK, network.reason); assertFalse(network.toString().contains("fictional-key")); assertNull(network.cause)
            settings.baseUrl = "http://localhost/v1"
            assertEquals(AiExplanationError.HTTPS_REQUIRED, (runCatching { explanationConfig(settings) }.exceptionOrNull() as AiExplanationException).reason)
            assertEquals(4, server.requestCount); assertFalse(logs.toString().contains("fictional-key"))
        } finally { logger.removeHandler(handler) }
    }

    @Test fun backupElevenRetainsFillAlternativesAndTenRestoresLegacyDefaults() = runBlocking {
        val repo = LintiRepository(db)
        val bank = repo.createBank("虚构备份题库")
        repo.saveQuestion(com.xjb.linti.domain.Question(bankId = bank, type = QuestionType.FILL, stem = "____", answer = "甲", explanation = "解析", sourceFileName = "虚构", sourceExcerpt = "虚构出处", acceptedAnswers = listOf(listOf("甲", "同义甲"))))
        repo.saveQuestion(com.xjb.linti.domain.Question(bankId = bank, type = QuestionType.ESSAY, stem = "虚构问答", answer = "虚构要点", sourceFileName = "虚构"))
        val backup = repo.exportBackupJson()
        assertEquals(12, JSONObject(backup).getInt("version"))
        repo.restoreBackupJson(backup)
        val values = repo.getQuestions(bank)
        assertEquals(listOf(listOf("甲", "同义甲")), values.first { it.type == QuestionType.FILL }.acceptedAnswers)
        assertEquals("虚构出处", values.first { it.type == QuestionType.FILL }.sourceExcerpt)
        assertEquals("虚构要点", values.first { it.type == QuestionType.ESSAY }.answer)
        val broken = JSONObject(backup).also { it.getJSONArray("questions").getJSONObject(0).remove("sourceExcerpt") }
        assertTrue(runCatching { repo.restoreBackupJson(broken.toString()) }.isFailure)
        assertEquals(2, repo.getQuestions(bank).size)
        val badType = JSONObject(backup).also { it.getJSONArray("questions").getJSONObject(0).put("type", "INVALID") }
        assertTrue(runCatching { repo.restoreBackupJson(badType.toString()) }.isFailure)
        assertEquals(2, repo.getQuestions(bank).size)
        repo.restoreBackupJson(JSONObject(backup).put("version", 10).toString())
        assertTrue(repo.getQuestions(bank).all { it.acceptedAnswers.isEmpty() && it.sourceExcerpt == null })
    }

    @Test fun shortageProducesProblemAndSecretsInResponsesAreRedacted() = runBlocking {
        val payload = JSONObject().put("questions", JSONArray().put(JSONObject(question.toString()).put("stem", "虚构 fictional-key")))
        server.enqueue(MockResponse().setBody(response(payload.toString())))
        val drafts = client.generateMaterial(batch.copy(counts = mapOf(QuestionType.ESSAY to 2)), explanationConfig(settings))
        assertEquals(2, drafts.size); assertFalse(drafts.toString().contains("fictional-key"))
        assertTrue(AiDraftValidator.validate(drafts.last()).contains("返回数量不足"))
    }
}
