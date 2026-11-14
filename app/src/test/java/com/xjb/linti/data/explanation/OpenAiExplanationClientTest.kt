package com.xjb.linti.data.explanation

import com.xjb.linti.data.prefs.AiExplanationReasoningEffort
import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
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
import java.util.concurrent.TimeUnit
import java.io.IOException
import java.util.logging.Handler
import java.util.logging.LogRecord
import java.util.logging.Logger

class OpenAiExplanationClientTest {
    private lateinit var server: MockWebServer
    private lateinit var settings: FakeExplanationSettings
    private lateinit var client: OpenAiExplanationClient
    private val key = "fictional-secret-key"
    private val question = Question(id = 42, bankId = 11, type = QuestionType.SINGLE, stem = "虚构：哪一个是甲？",
        options = listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")), answer = "A",
        explanation = "虚构原解析", tags = "禁止发送的标签", rawSnippet = "禁止发送的原文", updatedAt = 123,
        images = listOf(com.xjb.linti.domain.QuestionImage("a".repeat(64) + ".png", 20, 20)))

    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
        settings = FakeExplanationSettings(key, server.url("/v1").toString().trimEnd('/'))
        client = OpenAiExplanationClient(OkHttpClient.Builder()
            .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
            // 始终把测试请求路由到本地 HTTPS 服务，官方域名也不会访问外网。
            .addInterceptor { chain ->
                val original = chain.request()
                chain.proceed(original.newBuilder().url(server.url(original.url.encodedPath)).build())
            }
            .readTimeout(300, TimeUnit.MILLISECONDS).callTimeout(2, TimeUnit.SECONDS).build())
    }

    @After fun close() = server.shutdown()

    @Test fun sendsOnlyCurrentQuestionAndAuthorization() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"虚构解析"}}]}"""))
        assertEquals("虚构解析", client.explain(question, explanationConfig(settings)))
        val recorded = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer $key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        val request = JSONObject(body)
        assertEquals(setOf("model", "messages"), request.keySet())
        assertEquals("fictional-model", request.getString("model"))
        val messages = request.getJSONArray("messages")
        assertEquals(2, messages.length())
        val instruction = messages.getJSONObject(0).getString("content")
        listOf("标准答案", "不要更改答案", "正确答案为什么成立", "其他选项为什么不对",
            "没有选项时解释关键知识点", "中文", "3–6 句", "可以使用选项内容").forEach {
            assertTrue("提示词缺少：$it", instruction.contains(it))
        }
        val sent = JSONObject(messages.getJSONObject(1).getString("content"))
        assertEquals(setOf("type", "stem", "options", "answer", "explanation"), sent.keySet())
        assertEquals(question.stem, sent.getString("stem"))
        assertEquals("A", sent.getString("answer"))
        assertEquals(2, sent.getJSONArray("options").length())
        assertEquals("虚构原解析", sent.getString("explanation"))
        assertFalse(body.contains(key)); assertFalse(body.contains("禁止发送")); assertFalse(body.contains("42"))
        assertFalse(body.contains("images")); assertFalse(body.contains("a".repeat(64)))
    }

    @Test fun reasoningEffortAndTemperatureFollowSettingsForOfficialDeepSeek() = runBlocking {
        settings.provider = AiProvider.DEEPSEEK
        assertEquals(AiExplanationReasoningEffort.HIGH, explanationConfig(settings).reasoningEffort)
        for (base in listOf("https://api.deepseek.com", "https://api.deepseek.com/", "https://api.deepseek.com/v1", "https://api.deepseek.com/v1/", "HTTPS://API.DEEPSEEK.COM:443/v1")) {
            settings.baseUrl = base
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"虚构解析"}}]}"""))
            client.explain(question, explanationConfig(settings))
            val defaultRequest = JSONObject(server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8())
            assertEquals("high", defaultRequest.getString("reasoning_effort"))
            assertFalse(defaultRequest.has("temperature"))
        }
        for (effort in AiExplanationReasoningEffort.entries) {
            settings.setAiExplanationReasoningEffort(effort)
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"虚构解析"}}]}"""))
            assertEquals("虚构解析", client.explain(question, explanationConfig(settings)))
            val request = JSONObject(server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8())
            assertEquals(effort.apiValue, request.getString("reasoning_effort"))
            assertEquals(effort == AiExplanationReasoningEffort.NONE, request.has("temperature"))
            if (effort == AiExplanationReasoningEffort.NONE) assertEquals(0.1, request.getDouble("temperature"), 0.0)
        }
        // 判断依据是地址，而不是服务商名称。
        settings.provider = AiProvider.CUSTOM
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"虚构解析"}}]}"""))
        client.explain(question, explanationConfig(settings))
        assertEquals("max", JSONObject(server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8()).getString("reasoning_effort"))
    }

    @Test fun nonOfficialServersNeverReceiveReasoningEffort() = runBlocking {
        settings.provider = AiProvider.DEEPSEEK
        for (base in listOf(server.url("/v1").toString(), "https://api.deepseek.com.evil.test/v1", "https://api.deepseek.com/v1//")) {
            settings.baseUrl = base
            for (effort in AiExplanationReasoningEffort.entries) {
                settings.setAiExplanationReasoningEffort(effort)
                server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"虚构解析"}}]}"""))
                client.explain(question, explanationConfig(settings))
                val request = JSONObject(server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8())
                assertFalse(request.has("reasoning_effort"))
                assertEquals(effort == AiExplanationReasoningEffort.NONE, request.has("temperature"))
            }
        }
    }

    @Test fun onlyFinalContentIsReadEvenWhenReasoningContentExists() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"reasoning_content":"私有思考内容","content":"最终解析"}}]}"""))
        assertEquals("最终解析", client.explain(question, explanationConfig(settings)))
        for (message in listOf(
            """{"reasoning_content":"私有思考内容"}""",
            """{"reasoning_content":"私有思考内容","content":null}""",
            """{"reasoning_content":"私有思考内容","content":"  "}""",
        )) {
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":$message}]}"""))
            assertEquals(AiExplanationError.RESPONSE, failure { client.explain(question, explanationConfig(settings)) })
        }
    }

    @Test fun defaultsToCurrentProviderModelWhenModelIsBlank() {
        settings.model = "  "
        settings.provider = AiProvider.DEEPSEEK
        assertEquals("deepseek-flash", explanationConfig(settings).model)
        settings.provider = AiProvider.OPENAI_COMPATIBLE
        assertEquals("gpt-4o-mini", explanationConfig(settings).model)
        settings.model = "custom-model"
        assertEquals("custom-model", explanationConfig(settings).model)
    }

    @Test fun missingOrInsecureConfigurationNeverSends() = runBlocking {
        settings.key = ""
        assertEquals(AiExplanationError.NOT_CONFIGURED, failure { explanationConfig(settings) })
        settings.key = key
        settings.baseUrl = server.url("/v1").toString().replace("https://", "http://")
        assertEquals(AiExplanationError.HTTPS_REQUIRED, failure { explanationConfig(settings) })
        assertEquals(AiExplanationError.HTTPS_REQUIRED, failure { client.explain(question, AiExplanationConfig(settings.baseUrl, key, "m", "p")) })
        settings.baseUrl = "https://user:password@localhost/v1"
        assertEquals(AiExplanationError.INVALID_CONFIGURATION, failure { explanationConfig(settings) })
        settings.key = "test"
        settings.baseUrl = "https://test.invalid/v1"
        assertEquals("https://test.invalid/v1/chat/completions", explanationConfig(settings).endpoint)
        assertEquals(0, server.requestCount)
    }

    @Test fun authenticationBadJsonEmptyChoicesAndNetworkErrorsAreSafe() = runBlocking {
        val logs = StringBuilder()
        val logger = Logger.getLogger("okhttp3.mockwebserver.MockWebServer")
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { logs.append(record.message) }
            override fun flush() = Unit
            override fun close() = Unit
        }
        logger.addHandler(handler)
        try {
            for (status in listOf(401, 403)) {
                server.enqueue(MockResponse().setResponseCode(status).setBody(key))
                assertEquals(AiExplanationError.UNAUTHORIZED, failure { client.explain(question, explanationConfig(settings)) })
            }
            for (body in listOf("不是 JSON $key", "{\"choices\":[]}", "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}")) {
                server.enqueue(MockResponse().setBody(body))
                assertEquals(AiExplanationError.RESPONSE, failure { client.explain(question, explanationConfig(settings)) })
            }
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertEquals(AiExplanationError.TIMEOUT, failure { client.explain(question, explanationConfig(settings)) })
            server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"密钥 fictional-secret-key"}}]}"""))
            assertFalse(client.explain(question, explanationConfig(settings)).contains(key))
            assertFalse(logs.toString().contains(key))
        } finally { logger.removeHandler(handler) }
    }

    @Test fun networkFailureDoesNotExposeExceptionDetails() = runBlocking {
        val failing = OpenAiExplanationClient(OkHttpClient.Builder()
            .addInterceptor { throw IOException("模拟网络错误 $key") }.build())
        assertEquals(AiExplanationError.NETWORK, failure { failing.explain(question, explanationConfig(settings)) })
        assertEquals(0, server.requestCount)
    }

    private suspend fun failure(block: suspend () -> Any): AiExplanationError {
        val e = try { block(); throw AssertionError("expected error") } catch (e: AiExplanationException) { e }
        assertFalse(e.toString().contains(key)); assertFalse(e.message.orEmpty().contains(key)); assertNull(e.cause)
        return e.reason
    }
}
