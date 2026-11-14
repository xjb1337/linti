package com.xjb.linti.data.aigeneration

import com.xjb.linti.data.explanation.*
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
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

class AiQuestionClientTest {
    private val key = "fictional-secret-key"
    private lateinit var server: MockWebServer
    private lateinit var client: OpenAiQuestionClient
    private lateinit var settings: FakeExplanationSettings
    private val content = """{"questions":[{"type":"SINGLE","stem":"虚构题干","options":[{"label":"A","text":"甲"},{"label":"B","text":"乙"}],"answer":"A","explanation":"虚构解析"}]}"""
    private fun response(value: String = content) = JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", value).put("reasoning_content", "不得使用")))).toString()
    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        client = OpenAiQuestionClient(OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build()) }
            .readTimeout(200, TimeUnit.MILLISECONDS).callTimeout(1, TimeUnit.SECONDS).build())
        settings = FakeExplanationSettings(key, server.url("/v1").toString().trimEnd('/'))
    }
    @After fun close() = server.shutdown()

    @Test fun sendsOnlyTopicOrSelectedReferenceFields() = runBlocking {
        server.enqueue(MockResponse().setBody(response()))
        client.generate(AiQuestionSource.Topic("虚构植物"), QuestionType.SINGLE, 1, explanationConfig(settings))
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("Bearer $key", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(setOf("model", "messages"), body.keySet())
        val system = body.getJSONArray("messages").getJSONObject(0)
        assertEquals("system", system.getString("role"))
        assertEquals(OpenAiQuestionClient.SYSTEM_PROMPT, system.getString("content"))
        assertTrue(system.getString("content").contains("单选和多选的正确答案应在各选项位置中随机均匀分布，不要集中在同一位置。"))
        val input = JSONObject(body.getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals(setOf("type", "count", "topic"), input.keySet())
        assertEquals("虚构植物", input.getString("topic"))
        val question = Question(id = 12345, bankId = 6789, stem = "虚构参照题", options = listOf(QuestionOption("A", "甲")), answer = "A", explanation = "原解析", tags = "不得发送标签", rawSnippet = "不得发送原文")
        server.enqueue(MockResponse().setBody(response()))
        client.generate(AiQuestionSource.References(listOf(question)), QuestionType.SINGLE, 1, explanationConfig(settings))
        val text = server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8()
        val refInput = JSONObject(JSONObject(text).getJSONArray("messages").getJSONObject(1).getString("content"))
        assertEquals(setOf("type", "count", "references"), refInput.keySet())
        assertEquals(setOf("stem", "options", "answer", "explanation"), refInput.getJSONArray("references").getJSONObject(0).keySet())
        assertFalse(text.contains(key)); assertFalse(text.contains("不得发送")); assertFalse(text.contains("12345")); assertFalse(text.contains("6789"))
    }

    @Test fun officialDeepSeekHasReasoningAndJsonFormatOnlyForOfficialAddress() = runBlocking {
        settings.baseUrl = "https://api.deepseek.com/v1"
        server.enqueue(MockResponse().setBody(response()))
        client.generate(AiQuestionSource.Topic("虚构主题"), QuestionType.SINGLE, 1, explanationConfig(settings))
        val official = JSONObject(server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8())
        assertEquals("high", official.getString("reasoning_effort"))
        assertEquals("json_object", official.getJSONObject("response_format").getString("type"))
        settings.baseUrl = "https://api.deepseek.com.evil.test/v1"
        server.enqueue(MockResponse().setBody(response()))
        client.generate(AiQuestionSource.Topic("虚构主题"), QuestionType.SINGLE, 1, explanationConfig(settings))
        val other = JSONObject(server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8())
        assertFalse(other.has("reasoning_effort")); assertFalse(other.has("response_format"))
    }

    @Test fun escapedKeyIsRemovedAfterJsonDecoding() = runBlocking {
        settings.key = "fictional\"secret\\key"
        val generated = JSONObject(content)
        generated.getJSONArray("questions").getJSONObject(0).put("stem", "虚构题 ${settings.key}")
        server.enqueue(MockResponse().setBody(response(generated.toString())))
        val result = client.generate(AiQuestionSource.Topic("虚构"), QuestionType.SINGLE, 1, explanationConfig(settings))
        assertFalse(result.toString().contains(settings.key))
        assertEquals("虚构题 [已隐藏]", result.single().stem)
    }

    @Test fun failuresAndRedirectsDoNotLeakKeyOrRetry() = runBlocking {
        val logs = StringBuilder()
        val logger = Logger.getLogger("okhttp3.mockwebserver.MockWebServer")
        val handler = object : Handler() { override fun publish(record: LogRecord) { logs.append(record.message) }; override fun flush() = Unit; override fun close() = Unit }
        logger.addHandler(handler)
        try {
            for (status in listOf(401, 403, 500, 302)) {
                server.enqueue(MockResponse().setResponseCode(status).setHeader("Location", server.url("/other")).setBody(key))
                assertEquals(if (status == 401 || status == 403) AiExplanationError.UNAUTHORIZED else AiExplanationError.SERVER, failure { client.generate(AiQuestionSource.Topic("虚构"), QuestionType.SINGLE, 1, explanationConfig(settings)) })
            }
            for (body in listOf("不是 JSON $key", "{}", response("不是 JSON"), response("{\"questions\":[]}"))) {
                server.enqueue(MockResponse().setBody(body))
                assertEquals(AiExplanationError.RESPONSE, failure { client.generate(AiQuestionSource.Topic("虚构"), QuestionType.SINGLE, 1, explanationConfig(settings)) })
            }
            server.enqueue(MockResponse().setBody(response(content.replace("虚构题干", "虚构题干 $key"))))
            val redacted = client.generate(AiQuestionSource.Topic("虚构"), QuestionType.SINGLE, 1, explanationConfig(settings))
            assertFalse(redacted.toString().contains(key))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertEquals(AiExplanationError.TIMEOUT, failure { client.generate(AiQuestionSource.Topic("虚构"), QuestionType.SINGLE, 1, explanationConfig(settings)) })
            assertEquals(10, server.requestCount)
            val failing = OpenAiQuestionClient(OkHttpClient.Builder().addInterceptor { throw IOException("模拟 $key") }.build())
            assertEquals(AiExplanationError.NETWORK, failure { failing.generate(AiQuestionSource.Topic("虚构"), QuestionType.SINGLE, 1, explanationConfig(settings)) })
            settings.baseUrl = "http://localhost/v1"
            assertEquals(AiExplanationError.HTTPS_REQUIRED, failure { explanationConfig(settings) })
            settings.key = ""
            assertEquals(AiExplanationError.NOT_CONFIGURED, failure { explanationConfig(settings) })
            assertFalse(logs.toString().contains(key))
        } finally { logger.removeHandler(handler) }
    }
    private suspend fun failure(block: suspend () -> Any): AiExplanationError {
        val e = try { block(); throw AssertionError("expected failure") } catch (e: AiExplanationException) { e }
        assertFalse(e.toString().contains(key)); assertFalse(e.message.orEmpty().contains(key)); assertNull(e.cause)
        return e.reason
    }
}
