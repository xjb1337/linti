package com.xjb.linti.data.import

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiCompatibleAiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var clientTls: HandshakeCertificates

    @Before fun setup() {
        val cert = HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(cert).build()
        clientTls = HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server = MockWebServer().apply { useHttps(serverTls.sslSocketFactory(), false); start() }
    }

    @After fun teardown() {
        server.shutdown()
    }

    private fun testHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager)
        .addInterceptor { chain ->
            val original = chain.request()
            chain.proceed(original.newBuilder().url(server.url(original.url.encodedPath)).build())
        }
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    @Test fun rejectsUnsafeUrlsAndInvalidApiKey() {
        assertTrue(runCatching { OpenAiCompatibleAiClient("http://example.com", "sk-test") }.exceptionOrNull() is AiConfigurationException)
        assertTrue(runCatching { OpenAiCompatibleAiClient("https://user:pass@example.com", "sk-test") }.exceptionOrNull() is AiConfigurationException)
        assertTrue(runCatching { OpenAiCompatibleAiClient("https://example.com?foo=bar", "sk-test") }.exceptionOrNull() is AiConfigurationException)
        assertTrue(runCatching { OpenAiCompatibleAiClient("https://example.com", "sk-中文") }.exceptionOrNull() is AiConfigurationException)
    }

    @Test fun doesNotFollowRedirects() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/redirected").toString()))
        val client = OpenAiCompatibleAiClient("https://example.com/v1", "sk-test-key", httpClient = testHttpClient())

        val error = runCatching { client.parseQuestions("1. 虚构题干？A. 甲 B. 乙 答案：A") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(1, server.requestCount)
    }

    @Test fun officialDeepSeekSendsDisabledThinkingAndParsesDrafts() = runBlocking {
        val content = """[{"sourceNumber":"1","type":"SINGLE","stem":"虚构题干","options":[{"label":"A","text":"甲"},{"label":"B","text":"乙"}],"answer":"A","explanation":"虚构解析"}]"""
        val payload = JSONObject().put(
            "choices",
            org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(payload.toString()))
        val client = OpenAiCompatibleAiClient("https://api.deepseek.com", "sk-test-key", "deepseek-flash", testHttpClient())

        val drafts = client.parseQuestions("1. 虚构题干？A. 甲 B. 乙 答案：A")

        assertEquals(1, drafts.size)
        assertEquals("虚构题干", drafts.single().stem)
        val body = JSONObject(server.takeRequest().body.readUtf8())
        assertEquals("deepseek-flash", body.getString("model"))
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertEquals(0.1, body.getDouble("temperature"), 0.001)
    }

    @Test fun longInputSplitsIntoBatchesAndReportsProgress() = runBlocking {
        val q1 = """[{"sourceNumber":"1","type":"SINGLE","stem":"虚构长文第一题","options":[{"label":"A","text":"甲"},{"label":"B","text":"乙"}],"answer":"A","tags":["分类一","分类二"]}]"""
        val q2 = """{"questions":[{"sourceNumber":"2","type":"JUDGE","stem":"虚构长文第二题","options":[],"answer":"true"}]}"""
        server.enqueue(MockResponse().setBody(JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", q1)))).toString()))
        server.enqueue(MockResponse().setBody(JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", q2)))).toString()))
        val block = "1. 虚构题目内容" + "甲".repeat(2000) + "\n\n2. 虚构题目内容" + "乙".repeat(2000)
        val progresses = mutableListOf<String>()
        val client = OpenAiCompatibleAiClient("https://api.deepseek.com", "sk-test-key", "deepseek-flash", testHttpClient())

        val drafts = client.parseQuestions(block) { progresses += it }

        assertEquals(2, server.requestCount)
        assertEquals(2, drafts.size)
        assertEquals("分类一、分类二", drafts[0].tags)
        assertEquals("true", drafts[1].answer)
        assertTrue(progresses.any { "第 1 / 2 批" in it })
        assertTrue(progresses.any { "第 2 / 2 批" in it && "已识别 1 题" in it })
    }

    @Test fun rescuesCompleteObjectsWhenTruncatedAndThrowsOnUnrecoverable() = runBlocking {
        val truncated = """[{"sourceNumber":"1","type":"SINGLE","stem":"完整题目","options":[{"label":"A","text":"甲"},{"label":"B","text":"乙"}],"answer":"A"},{"sourceNumber":"2","stem":"被截断"""
        val payload = JSONObject().put(
            "choices",
            org.json.JSONArray().put(JSONObject().put("finish_reason", "length").put("message", JSONObject().put("content", truncated))),
        )
        server.enqueue(MockResponse().setBody(payload.toString()))
        val client = OpenAiCompatibleAiClient("https://example.com/v1", "sk-test-key", httpClient = testHttpClient())
        val rescued = client.parseQuestions("1. 完整题目")
        assertEquals(1, rescued.size)
        assertEquals("完整题目", rescued.single().stem)

        val brokenPayload = JSONObject().put(
            "choices",
            org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("content", "完全不是JSON"))),
        )
        server.enqueue(MockResponse().setBody(brokenPayload.toString()))
        val error = runCatching { client.parseQuestions("1. 坏格式") }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("未能解析出题目"))
    }

    @Test fun mapsHttpStatusAndTimeoutToClearMessagesWithoutLeakingKey() = runBlocking {
        val key = "sk-secret-key-999"
        val client = OpenAiCompatibleAiClient("https://example.com/v1", key, httpClient = testHttpClient())
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid $key"}}"""))
        val unauthorized = runCatching { client.parseQuestions("1. 题") }.exceptionOrNull()!!
        assertTrue(unauthorized.message!!.contains("认证失败"))
        assertTrue(!unauthorized.message!!.contains(key))

        server.enqueue(MockResponse().setResponseCode(429))
        val rateLimited = runCatching { client.parseQuestions("1. 题") }.exceptionOrNull()!!
        assertTrue(rateLimited.message!!.contains("429"))

        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val timedOut = runCatching { client.parseQuestions("1. 题") }.exceptionOrNull()!!
        assertTrue(timedOut.message!!.contains("超时"))
    }
}
