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
}
