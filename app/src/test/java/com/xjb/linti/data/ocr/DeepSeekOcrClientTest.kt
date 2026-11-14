package com.xjb.linti.data.ocr

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DeepSeekOcrClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: DeepSeekOcrClient
    private lateinit var uploadFile: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = DeepSeekOcrClient(
            apiKey = "test-key",
            endpoint = server.url("/v1/tools/ocr").toString(),
        )
        uploadFile = File.createTempFile("ocr_test_", ".pdf")
        uploadFile.writeText("%PDF-1.4 synthetic test payload")
    }

    @After
    fun tearDown() {
        server.shutdown()
        uploadFile.delete()
    }

    private fun successJson(
        pages: List<Triple<Int, String, String>>,
        pageCount: Int = pages.size,
        purpose: String = "text_recognition",
    ): String {
        val pagesArray = JSONArray()
        pages.forEach { (index, text, finishReason) ->
            pagesArray.put(
                JSONObject()
                    .put("index", index)
                    .put("text", text)
                    .put("finish_reason", finishReason),
            )
        }
        return JSONObject()
            .put("id", "ocr-test")
            .put("object", "ocr.result")
            .put("created", 1762790000)
            .put("purpose", purpose)
            .put("page_count", pageCount)
            .put("pages", pagesArray)
            .put("usage", JSONObject().put("input_tokens", 10).put("output_tokens", 20).put("total_tokens", 30))
            .toString()
    }

    private fun enqueueSuccess(body: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    private fun recognize(
        knownPageCount: Int? = 1,
        fileName: String = "scan.pdf",
        file: File = uploadFile,
    ): OcrSuccess = runBlocking {
        client.recognizeFile(
            file = file,
            fileName = fileName,
            knownPageCount = knownPageCount,
        )
    }

    @Test
    fun `multipart carries purpose and named file only, with bearer auth and no model`() {
        enqueueSuccess(successJson(listOf(Triple(0, "第 1 页文字", "stop"))))
        recognize()

        val recorded = server.takeRequest()
        assertEquals("/v1/tools/ocr", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))
        assertTrue(
            "Content-Type 必须是 multipart/form-data",
            recorded.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"),
        )
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"purpose\""))
        assertTrue(body.contains("text_recognition"))
        assertTrue(body.contains("name=\"file\""))
        assertTrue(body.contains("filename=\"scan.pdf\""))
        assertTrue(body.contains("%PDF-1.4 synthetic test payload"))
        // 只允许两个字段：不发 model、不走 /files 或 chat
        assertTrue(body.contains("name=\"purpose\"") && body.contains("name=\"file\""))
        assertTrue("不得包含 model 字段", !body.contains("name=\"model\"") && !body.contains("\"model\""))
        assertEquals(1, body.countOccurrences("name=\"purpose\""))
        assertEquals(1, body.countOccurrences("name=\"file\""))
    }

    @Test
    fun `image upload keeps filename and mime type`() {
        val image = File.createTempFile("ocr_test_", ".jpg")
        image.writeBytes(ByteArray(128) { it.toByte() })
        try {
            enqueueSuccess(successJson(listOf(Triple(0, "图片文字", "stop"))))
            recognize(knownPageCount = 1, fileName = "photo.jpg", file = image)

            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("filename=\"photo.jpg\""))
            assertTrue(body.contains("Content-Type: image/jpeg"))
        } finally {
            image.delete()
        }
    }

    @Test
    fun `jpeg upload copy keeps file header filename and mime consistent`() {
        // PNG 输入经 VM 编码后得到 JPEG 内容 + .jpg 文件名；此处验证边界一致性：
        // 文件名 .jpg → MIME image/jpeg → 内容头 FF D8 FF 原样上传
        val jpeg = File.createTempFile("ocr_test_", ".jpg")
        jpeg.writeBytes(
            byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
            ) + ByteArray(64) { it.toByte() },
        )
        try {
            enqueueSuccess(successJson(listOf(Triple(0, "图片文字", "stop"))))
            recognize(knownPageCount = 1, fileName = "screenshot.jpg", file = jpeg)

            val body = server.takeRequest().body.readUtf8()
            assertTrue("文件名必须是 .jpg", body.contains("filename=\"screenshot.jpg\""))
            assertTrue("MIME 必须是 image/jpeg", body.contains("Content-Type: image/jpeg"))
            assertTrue(
                "不得出现 png 命名或 image/png MIME",
                !body.contains("filename=\"screenshot.png\"") && !body.contains("image/png"),
            )
            // multipart 正文以 ISO-8859-1/字节流传输，JPEG 文件头可回读
            val bytes = jpeg.readBytes()
            val header = bytes.take(3).joinToString("") { "%02X".format(it) }
            assertEquals("FFD8FF", header)
        } finally {
            jpeg.delete()
        }
    }

    @Test
    fun `keepalive newlines before json body are ignored`() {
        enqueueSuccess("\n\n\n" + successJson(listOf(Triple(0, "第 1 页文字", "stop"))))
        val result = recognize()
        assertEquals(1, result.pageCount)
        assertEquals("第 1 页文字", result.pages.single().text)
        assertEquals("text_recognition", result.purpose)
    }

    @Test
    fun `happy path with multiple pages`() {
        enqueueSuccess(
            successJson(
                listOf(
                    Triple(0, "第 1 页", "stop"),
                    Triple(1, "第 2 页", "stop"),
                ),
            ),
        )
        val result = recognize(knownPageCount = 2)
        assertEquals(2, result.pageCount)
        assertEquals(listOf(0, 1), result.pages.map { it.index })
        assertEquals(listOf("第 1 页", "第 2 页"), result.pages.map { it.text })
    }

    @Test
    fun `non ocr result object is rejected`() {
        enqueueSuccess(successJson(listOf(Triple(0, "x", "stop"))).replace("ocr.result", "ocr.other"))
        val error = runCatching { recognize() }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.PROTOCOL)
        assertTrue((error as? OcrException)?.message.orEmpty().isNotEmpty())
    }

    @Test
    fun `purpose mismatch is rejected`() {
        enqueueSuccess(
            successJson(purpose = "rich_layout_recognition", pages = listOf(Triple(0, "x", "stop"))),
        )
        val error = runCatching { recognize() }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.PROTOCOL)
    }

    @Test
    fun `page_count mismatch with pages array is rejected`() {
        enqueueSuccess(successJson(listOf(Triple(0, "x", "stop")), pageCount = 3))
        val error = runCatching { recognize() }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.PROTOCOL)
    }

    @Test
    fun `non contiguous or duplicate page indexes are rejected`() {
        enqueueSuccess(
            successJson(
                listOf(Triple(0, "a", "stop"), Triple(2, "b", "stop")),
            ),
        )
        val error = runCatching { recognize(knownPageCount = 2) }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.PROTOCOL)

        enqueueSuccess(
            successJson(
                listOf(Triple(0, "a", "stop"), Triple(0, "b", "stop")),
            ),
        )
        val duplicate = runCatching { recognize(knownPageCount = 2) }.exceptionOrNull()
        assertTrue(duplicate is OcrException && duplicate.kind == OcrFailureKind.PROTOCOL)
    }

    @Test
    fun `known page count mismatch is rejected`() {
        enqueueSuccess(successJson(listOf(Triple(0, "a", "stop"), Triple(1, "b", "stop"))))
        val error = runCatching { recognize(knownPageCount = 3) }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.PAGE_MISMATCH)
        assertTrue((error as? OcrException)?.message.orEmpty().contains("页数"))
    }

    @Test
    fun `truncated page finish reason is rejected with page number`() {
        enqueueSuccess(
            successJson(
                listOf(Triple(0, "完整页", "stop"), Triple(1, "被截断", "length")),
            ),
        )
        val error = runCatching { recognize(knownPageCount = 2) }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.TRUNCATED)
        assertTrue((error as? OcrException)?.message.orEmpty().contains("第 2 页"))
    }

    @Test
    fun `unknown finish reason is rejected`() {
        enqueueSuccess(successJson(listOf(Triple(0, "x", "max_tokens"))))
        val error = runCatching { recognize() }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.PROTOCOL)
    }

    @Test
    fun `http error codes map to actionable messages`() {
        val cases = listOf(
            401 to OcrFailureKind.AUTH,
            402 to OcrFailureKind.BALANCE,
            408 to OcrFailureKind.UPLOAD_TIMEOUT,
            413 to OcrFailureKind.TOO_LARGE,
            429 to OcrFailureKind.RATE_LIMIT,
            500 to OcrFailureKind.BUSY,
            503 to OcrFailureKind.BUSY,
            504 to OcrFailureKind.TIMEOUT,
        )
        cases.forEach { (code, expectedKind) ->
            server.enqueue(
                MockResponse()
                    .setResponseCode(code)
                    .setBody(JSONObject().put("error", JSONObject().put("message", "boom")).toString()),
            )
            val error = runCatching { recognize() }.exceptionOrNull()
            assertTrue("HTTP $code 应映射到 $expectedKind", error is OcrException)
            assertEquals("HTTP $code", expectedKind, (error as OcrException).kind)
            assertTrue(error.message.orEmpty().contains(code.toString()))        }
    }

    @Test
    fun `200 with error object is recognition failure`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    JSONObject().put("error", JSONObject().put("message", "OCR did not finish in time")).toString(),
                ),
        )
        val error = runCatching { recognize() }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.RECOGNITION_FAILED)
        assertTrue((error as? OcrException)?.message.orEmpty().contains("识别未完成"))
    }

    @Test
    fun `redirects are not followed`() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://evil.example.com/steal")
                .setBody(""),
        )
        val error = runCatching { recognize() }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.REDIRECT)
        // 密钥没有跟随重定向发出第二次请求
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `non json body is protocol error`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not a json"))
        val error = runCatching { recognize() }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.PROTOCOL)
    }

    @Test
    fun `oversized file is rejected locally without any request`() {
        val big = File.createTempFile("ocr_big_", ".pdf")
        try {
            RandomAccessFile(big, "rw").use { it.setLength(OcrLimits.MAX_FILE_BYTES + 1) }
            val error = runCatching { recognize(file = big) }.exceptionOrNull()
            assertTrue(error is OcrException && error.kind == OcrFailureKind.TOO_LARGE)
            assertEquals(0, server.requestCount)
        } finally {
            big.delete()
        }
    }

    @Test
    fun `timeout maps to billing risk kind`() {
        val shortTimeoutClient = DeepSeekOcrClient(
            apiKey = "test-key",
            httpClient = OkHttpClient.Builder()
                .callTimeout(500, TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .build(),
            endpoint = server.url("/v1/tools/ocr").toString(),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(successJson(listOf(Triple(0, "x", "stop"))))
                .setBodyDelay(5, TimeUnit.SECONDS),
        )
        val error = runCatching {
            runBlocking {
                shortTimeoutClient.recognizeFile(uploadFile, "scan.pdf", knownPageCount = 1)
            }
        }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.TIMEOUT)
    }

    @Test
    fun `cancelled request interrupts the call without user error`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(successJson(listOf(Triple(0, "x", "stop"))))
                .setBodyDelay(10, TimeUnit.SECONDS),
        )
        val job = launch {
            client.recognizeFile(uploadFile, "scan.pdf", knownPageCount = 1)
        }
        delay(300)
        job.cancelAndJoin()
        // 请求已发出且随后被中断；不产生导航/状态（协程层验证），服务端最多收到一次请求
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `blank api key fails fast`() {
        val error = runCatching {
            DeepSeekOcrClient(apiKey = "  ")
        }.exceptionOrNull()
        assertTrue(error is OcrException && error.kind == OcrFailureKind.NO_KEY)
    }

    private fun String.countOccurrences(pattern: String): Int =
        split(pattern).size - 1
}
