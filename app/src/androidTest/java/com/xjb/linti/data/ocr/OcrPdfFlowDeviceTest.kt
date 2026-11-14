package com.xjb.linti.data.ocr

import android.content.Context
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.import.ConfirmImportResult
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.data.import.LocalQuestionParser
import com.xjb.linti.data.import.OcrDraftPostProcessor
import com.xjb.linti.data.import.PdfOcrMergeMode
import com.xjb.linti.data.import.PdfOcrMerger
import com.xjb.linti.data.import.PdfOcrPlanner
import com.xjb.linti.data.import.PdfPageInspector
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.OcrPhase
import com.xjb.linti.ui.PdfImportDecision
import com.xjb.linti.ui.screens.ImportReviewScreen
import com.xjb.linti.ui.screens.PdfOcrScreen
import com.xjb.linti.ui.theme.LintiTheme
import java.io.File
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 混合 PDF 在线识别链路的设备级验收：
 * 合成「第 1 页文字层 + 第 2 页位图（扫描）」PDF → mock 成功响应 → 逐页合并 → 统一解析 →
 * 实际渲染第 2 页（校对页「查看原页」用的同一渲染入口），确认能渲染出内容，而不是只断言路径存在。
 */
@RunWith(AndroidJUnit4::class)
class OcrPdfFlowDeviceTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var context: Context
    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private lateinit var mixedPdf: File
    private var testContainer: AppContainer? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
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
        mixedPdf = createMixedPdf()
    }

    @After
    fun tearDown() {
        testContainer?.let {
            it.appScope.cancel()
            it.database.close()
        }
        server.shutdown()
        mixedPdf.delete()
    }

    /** 第 1 页：真实文字层；第 2 页：文字绘入位图（无文字层，模拟扫描页）+ 蓝色色块供渲染断言。 */
    private fun createMixedPdf(): File {
        val document = PdfDocument()
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
            isAntiAlias = true
        }

        val page1 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        page1.canvas.drawColor(Color.WHITE)
        page1.canvas.drawText("1. 合成文字页：下列关于虚构内容的说法哪项正确？", 40f, 80f, textPaint)
        page1.canvas.drawText("A. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁", 40f, 110f, textPaint)
        page1.canvas.drawText("答案：A", 40f, 140f, textPaint)
        document.finishPage(page1)

        val page2 = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 2).create())
        page2.canvas.drawColor(Color.WHITE)
        val scanBitmap = Bitmap.createBitmap(420, 140, Bitmap.Config.ARGB_8888)
        val bitmapCanvas = Canvas(scanBitmap)
        bitmapCanvas.drawColor(Color.WHITE)
        bitmapCanvas.drawText("2. 扫描页识别出的题干是否完整？", 10f, 40f, textPaint)
        bitmapCanvas.drawText("A. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁", 10f, 70f, textPaint)
        bitmapCanvas.drawText("答案：B", 10f, 100f, textPaint)
        page2.canvas.drawBitmap(scanBitmap, 40f, 80f, null)
        page2.canvas.drawRect(
            40f, 260f, 320f, 360f,
            Paint().apply { color = Color.rgb(30, 90, 180) },
        )
        document.finishPage(page2)

        val file = File(context.cacheDir, "ocr_device_mixed.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        scanBitmap.recycle()
        return file
    }

    private fun successJson(
        firstPage: String = "1. 合成文字页：下列关于虚构内容的说法哪项正确？\nA. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁\n答案：A",
    ): String {
        val pages = JSONArray()
            .put(
                JSONObject()
                    .put("index", 0)
                    .put("text", firstPage)
                    .put("finish_reason", "stop"),
            )
            .put(
                JSONObject()
                    .put("index", 1)
                    .put("text", "2. 扫描页识别出的题干是否完整？\nA. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁\n答案：B")
                    .put("finish_reason", "stop"),
            )
        return JSONObject()
            .put("id", "ocr-device-test")
            .put("object", "ocr.result")
            .put("created", 1762790000)
            .put("purpose", "text_recognition")
            .put("page_count", 2)
            .put("pages", pages)
            .toString()
    }

    @Test
    fun mixedPdfFlowInspectsMergesParsesAndRendersPage2() {
        // 1) 逐页检查：第 1 页有文字层可用，第 2 页为扫描页
        val inspection = PdfPageInspector().inspectFile(mixedPdf)
        assertEquals(2, inspection.pageCount)
        assertTrue(inspection.pages[0].usable)
        assertTrue("位图绘制的第 2 页不得提取出文字", !inspection.pages[1].usable)
        assertEquals(listOf(1), inspection.scanPages)
        val plan = PdfOcrPlanner.plan(inspection)
        assertTrue(plan is com.xjb.linti.data.import.PdfOcrPlan.NeedsOcr)

        // 2) mock 成功响应：整份上传 → ocr.result 两页 stop
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(successJson()),
        )
        val client = DeepSeekOcrClient(
            apiKey = "test-key",
            httpClient = httpClient,
            endpoint = server.url("/v1/tools/ocr").toString(),
        )
        val success = kotlinx.coroutines.runBlocking {
            client.recognizeFile(
                file = mixedPdf,
                fileName = OcrUploadNaming.pdfUploadFileName("mixed.pdf"),
                knownPageCount = inspection.pageCount,
            )
        }
        assertEquals(2, success.pageCount)

        // 3) 按页合并：第 1 页保留本地提取，第 2 页采用在线识别
        val merged = PdfOcrMerger.merge(inspection, success.pages, PdfOcrMergeMode.SCANS_ONLY)
        assertTrue(!merged.pages[0].fromOcr)
        assertTrue(merged.pages[1].fromOcr)

        // 4) 统一解析 + 严格后处理：两道题，页码对照 1/2
        val parse = LocalQuestionParser().parse(merged.joinedText)
        assertEquals(2, parse.questions.size)
        val post = OcrDraftPostProcessor.process(parse, merged.pages.map { it.text })
        assertEquals(1, post.sourcePages[0])
        assertEquals(2, post.sourcePages[1])
        assertEquals("A", post.drafts[0].answer)
        assertEquals("B", post.drafts[1].answer)

        // 5) 校对页「查看原页」用同一渲染入口：实际渲染第 2 页并确认画出了内容（蓝色色块）
        val rendered = OcrSourcePageRenderer.renderPage(mixedPdf.absolutePath, 1)
        assertNotNull("第 2 页必须能渲染", rendered)
        rendered!!
        assertTrue("渲染尺寸异常", rendered.width > 0 && rendered.height > 0)
        val rectCenterX = (40 + 320) / 2f / 595f * rendered.width
        val rectCenterY = (260 + 360) / 2f / 842f * rendered.height
        val pixel = rendered.getPixel(rectCenterX.toInt(), rectCenterY.toInt())
        assertTrue(
            "第 2 页渲染应包含绘制的内容（取到颜色 #${"%08X".format(pixel)}）",
            Color.blue(pixel) > 150 && Color.red(pixel) < 100,
        )
    }

    @Test
    fun renderPageRejectsOutOfRangeAndHandlesMissingFile() {
        val rendered = OcrSourcePageRenderer.renderPage(mixedPdf.absolutePath, 5)
        assertEquals(null, rendered)
        val missing = OcrSourcePageRenderer.renderPage(
            File(context.cacheDir, "no_such_ocr.pdf").absolutePath,
            0,
        )
        assertEquals(null, missing)
    }

    @Test
    fun cancelledPdfRetriesOnlyAfterConfirmationAndReviewKeepsPageUntilExit() {
        val vm = newViewModel()
        assertEquals(PdfImportDecision.NeedsDecision, onMain {
            vm.importPdfFromUri(Uri.fromFile(mixedPdf), ImportMethod.LOCAL_ONLY)
        })
        val copiedPdf = File(requireNotNull(vm.pdfOcrState.value.sourcePath))
        var route by mutableStateOf("pdf")
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f)) {
                LintiTheme {
                    when (route) {
                        "pdf" -> PdfOcrScreen(vm, { route = "home" }, { route = "review" }, {})
                        "review" -> ImportReviewScreen(vm, { route = "home" }, { route = "home" })
                        else -> Box(androidx.compose.ui.Modifier)
                    }
                }
            }
        }
        server.enqueue(MockResponse().setHeadersDelay(5, TimeUnit.SECONDS).setBody(successJson()))
        val scanButton = compose.onNodeWithText("识别扫描页并合并原文")
        scanButton.performClick()
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        compose.onNodeWithText("取消识别").performClick()
        scanButton.performClick()
        compose.onNodeWithText("确认重试？").assertIsDisplayed()
        compose.onNodeWithText("取消", substring = false).performClick()
        assertFalse(onMain { vm.submitPdfOcr(PdfOcrMergeMode.SCANS_ONLY) })
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setBody(successJson(firstPage = "此页 OCR 输出不能替代本地文字")))
        scanButton.performClick()
        compose.onNodeWithText("仍要重试").performClick()
        waitForText("校对并入库")
        assertEquals(2, server.requestCount)
        val drafts = vm.importState.value.drafts
        assertEquals(2, drafts.size)
        assertEquals("A", drafts.first().answer)
        assertEquals(2, vm.importState.value.sourcePages[1])
        assertTrue(copiedPdf.exists())
        // Expand the second question and scroll the lazy list to its source row.
        compose.onNodeWithText("扫描页识别出的题干是否完整？", substring = true).performClick()
        val list = compose.onNode(androidx.compose.ui.test.hasScrollToIndexAction())
        list.performScrollToNode(hasText("来源：第 2 页"))
        compose.onNodeWithText("查看原页").performClick()
        compose.onNodeWithText("原页对照 · 第 2 条（第 2 页）").assertIsDisplayed()
        compose.onNodeWithContentDescription("对照原页").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(5_000) { !copiedPdf.exists() }
        assertTrue(vm.importState.value.drafts.isEmpty())
    }

    @Test
    fun cancelledPreparationKeepsImageAndInvalidOcrAnswerMustBeEditedBeforeSaving() {
        val vm = newViewModel()
        val png = File.createTempFile("ocr-cancel-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888)
        try {
            png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            assertTrue(onMain { vm.prepareImageOcr(Uri.fromFile(png), DocumentKind.IMAGE) })
            val preview = requireNotNull(vm.imageOcrPreview.value)
            val crop = CropRect(0f, 0f, preview.width.toFloat(), preview.height.toFloat())
            assertFalse(onMain {
                val pending = async(start = CoroutineStart.UNDISPATCHED) {
                    vm.submitImageOcr(0, crop, crop.right, crop.bottom)
                }
                assertEquals(OcrPhase.PREPARING, vm.imageOcrState.value.phase)
                vm.cancelOcr()
                pending.await()
            })
            assertNull(vm.imageOcrState.value.error)
            assertNull(vm.message.value)
            assertFalse(vm.imageOcrState.value.pendingBillingConfirm)
            assertEquals(preview, vm.imageOcrPreview.value)
            assertEquals(0, server.requestCount)

            val result = JSONObject(successJson())
            result.put("page_count", 1)
            result.put("pages", JSONArray().put(
                JSONObject().put("index", 0).put("finish_reason", "stop")
                    .put("text", "1. 合成图题哪项正确？\nA. 甲\nB. 乙\nC. 丙\n答案：本题选B因为题干"),
            ))
            server.enqueue(MockResponse().setBody(result.toString()))
            assertTrue(onMain { vm.submitImageOcr(0, crop, crop.right, crop.bottom) })
            assertEquals(1, server.requestCount)
            val previewCopy = File(requireNotNull(vm.importState.value.originalPreviewPath))
            onMain { vm.onImageOcrScreenLeft() }
            assertTrue(previewCopy.exists())
            assertTrue(vm.importState.value.saveBlocked)
            assertTrue(onMain { vm.confirmImport() } is ConfirmImportResult.Failure)
            onMain {
                vm.updateImportDraft(0, vm.importState.value.drafts.single().copy(answer = "B"))
            }
            assertFalse(vm.importState.value.saveBlocked)
            assertTrue(onMain { vm.confirmImport() } is ConfirmImportResult.Success)
            onMain { vm.onReviewScreenLeft() }
            assertFalse(previewCopy.exists())
        } finally {
            bitmap.recycle()
            png.delete()
            onMain {
                vm.onImageOcrScreenLeft()
                vm.clearImport()
            }
        }
    }

    private fun newViewModel(): LintiViewModel {
        val endpoint = server.url("/v1/tools/ocr").toString()
        val container = AppContainer(
            context,
            Room.inMemoryDatabaseBuilder(context, LintiDatabase::class.java).build(),
        )
        testContainer = container
        return LintiViewModel(
            context.applicationContext as Application,
            container,
            ocrClientFactory = {
                DeepSeekOcrClient("test-key", httpClient, endpoint)
            },
            ocrKeySourceOverride = OcrKeySource.Dedicated,
        )
    }

    private fun <T> onMain(block: suspend kotlinx.coroutines.CoroutineScope.() -> T): T =
        runBlocking { withContext(Dispatchers.Main, block) }

    private fun waitForText(text: String) {
        compose.waitUntil(8_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
