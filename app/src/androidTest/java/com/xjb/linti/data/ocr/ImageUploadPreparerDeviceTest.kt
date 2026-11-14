package com.xjb.linti.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.ByteString.Companion.decodeHex
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PNG 输入 → 上传副本的 multipart 一致性回归（设备级）：
 * 内容（JPEG 文件头）、文件名（.jpg）、MIME（image/jpeg）三者必须统一，不得出现 .png / image/png。
 */
@RunWith(AndroidJUnit4::class)
class ImageUploadPreparerDeviceTest {

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private lateinit var pngInput: File

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        // 设备上明文 HTTP 被网络安全策略拦截：mock 服务用自签 TLS（SAN=localhost），客户端只信任该证书
        val serverCertificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(serverCertificate)
            .build()
        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        server.start()
        val trust = HandshakeCertificates.Builder()
            .addTrustedCertificate(serverCertificate.certificate)
            .build()
        httpClient = OkHttpClient.Builder()
            .sslSocketFactory(trust.sslSocketFactory(), trust.trustManager)
            .build()
        pngInput = createPngInput()
    }

    @After
    fun tearDown() {
        server.shutdown()
        pngInput.delete()
    }

    private fun createPngInput(): File {
        val bitmap = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            isAntiAlias = true
        }
        canvas.drawText("1. PNG 输入合成题图：题干是否完整？", 24f, 80f, paint)
        canvas.drawText("A. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁", 24f, 130f, paint)
        canvas.drawText("答案：A", 24f, 180f, paint)
        val file = File(context.cacheDir, "ocr_device_input.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test
    fun pngInputProducesJpegUploadCopyWithConsistentNameMimeAndHeader() {
        val upload = ImageOcrBitmaps.prepareUploadCopy(
            sourcePath = pngInput.absolutePath,
            rotationDegrees = 0,
            previewCropRect = CropRect(0f, 0f, 480f, 640f),
            previewWidth = 480f,
            previewHeight = 640f,
            outputDir = context.cacheDir,
        )
        try {
            // 内容：JPEG 文件头（VM 对上传副本一律 JPEG 编码）
            val header = upload.readBytes().take(3).joinToString("") { "%02X".format(it) }
            assertEquals("上传副本必须是 JPEG 内容", "FFD8FF", header)
            // 文件名：临时副本本身即 .jpg；displayName 为 PNG 时命名也强制 .jpg
            assertTrue("副本文件名应为 .jpg", upload.name.endsWith(".jpg"))
            val uploadName = OcrUploadNaming.imageUploadFileName("screenshot.png")
            assertTrue(uploadName.endsWith(".jpg"))

            // multipart 边界：文件名 .jpg、MIME image/jpeg、JPEG 头字节原样上传
            val pages = JSONArray().put(
                JSONObject()
                    .put("index", 0)
                    .put("text", "1. PNG 输入合成题图：题干是否完整？\nA. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁\n答案：A")
                    .put("finish_reason", "stop"),
            )
            val body = JSONObject()
                .put("id", "ocr-device-image")
                .put("object", "ocr.result")
                .put("created", 1762790000)
                .put("purpose", "text_recognition")
                .put("page_count", 1)
                .put("pages", pages)
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(body.toString()),
            )
            val client = DeepSeekOcrClient(
                apiKey = "test-key",
                httpClient = httpClient,
                endpoint = server.url("/v1/tools/ocr").toString(),
            )
            val success = kotlinx.coroutines.runBlocking {
                client.recognizeFile(file = upload, fileName = uploadName, knownPageCount = 1)
            }
            assertEquals(1, success.pageCount)

            val recorded = server.takeRequest()
            val contentType = recorded.getHeader("Content-Type").orEmpty()
            assertTrue("multipart/form-data", contentType.startsWith("multipart/form-data"))
            val requestBody = recorded.body.readByteString()
            val requestText = requestBody.utf8()
            assertTrue("文件名必须带 .jpg", requestText.contains("filename=\"$uploadName\""))
            assertTrue("MIME 必须是 image/jpeg", requestText.contains("Content-Type: image/jpeg"))
            assertTrue("不得出现 png 文件名或 image/png MIME", !requestText.contains(".png") && !requestText.contains("image/png"))
            assertTrue(
                "JPEG 文件头字节必须原样出现在 multipart 正文中",
                requestBody.indexOf("FFD8FF".decodeHex()) >= 0,
            )
        } finally {
            upload.delete()
        }
    }

    @Test
    fun rotatedPngInputProducesUprightJpegCopy() {
        // 用户旋转 90°：副本尺寸应交换（宽=原高），且仍是 JPEG
        val upload = ImageOcrBitmaps.prepareUploadCopy(
            sourcePath = pngInput.absolutePath,
            rotationDegrees = 90,
            previewCropRect = CropRect(0f, 0f, 640f, 480f),
            previewWidth = 640f,
            previewHeight = 480f,
            outputDir = context.cacheDir,
        )
        try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(upload.absolutePath, bounds)
            assertEquals("旋转后宽应等于原高", 640, bounds.outWidth)
            assertEquals("旋转后高应等于原宽", 480, bounds.outHeight)
        } finally {
            upload.delete()
        }
    }
}
