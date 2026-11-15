package com.xjb.linti.ui.screens

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.aigeneration.*
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.explanation.AiExplanationConfig
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.data.explanation.FakeExplanationSettings
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.LintiRoot
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.theme.LintiTheme
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = Application::class)
class HomeAiQuestionIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var db: LintiDatabase
    private lateinit var container: AppContainer
    private lateinit var viewModel: LintiViewModel
    private val fake = FakeQuestionClient()
    private var rendering by mutableStateOf(true)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(app, LintiDatabase::class.java).allowMainThreadQueries().build()
        container = AppContainer(app, db)
        container.quizPreferences.setAutoCheckUpdates(false)
        val ai = AiQuestionRepository(db, FakeExplanationSettings(), fake)
        viewModel = LintiViewModel(app, container, aiQuestionRepository = ai)
    }
    @After fun close() {
        compose.runOnIdle { rendering = false }
        compose.waitForIdle()
        viewModel.viewModelScope.cancel()
        container.appScope.cancel()
        db.close()
    }

    private fun show(uri: Uri? = null) {
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I : Any?, O : Any?> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    checkNotNull(uri)
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(uri))
                }
            }
        }
        compose.setContent {
            if (rendering) CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                LintiTheme { LintiRoot(viewModel) }
            }
        }
    }
    private fun waitText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun waitDb(condition: suspend () -> Boolean) {
        compose.waitUntil(15_000) { runBlocking { condition() } }
    }
    private fun documentUri(file: File): Uri {
        val authority = "com.xjb.linti.test.${UUID.randomUUID()}"
        val provider = FixtureDocumentProvider(file)
        provider.attachInfo(app, ProviderInfo().apply { this.authority = authority; exported = true })
        ShadowContentResolver.registerProviderInternal(authority, provider)
        return Uri.parse("content://$authority/document").also { uri ->
            requireNotNull(app.contentResolver.openInputStream(uri)).close()
        }
    }
    private fun enterFromHome() {
        waitText("AI 出题")
        compose.onNodeWithText("AI 出题").performClick()
        compose.onNodeWithTag("ai-destination").assertExists()
    }
    private fun approveAll(expected: Int, existingQuestions: Int = 0) {
        waitText("待校对 $expected 题")
        compose.onNodeWithText("通过全部可入库题目").performClick()
        waitDb { db.questionDao().getByBank(db.questionBankDao().getAll().single().id).size == expected + existingQuestions }
    }

    @Test fun homeTopicToReviewAndBankList() {
        show(); enterFromHome()
        compose.onNodeWithText("参考已有题目").assertDoesNotExist()
        compose.onNodeWithTag("ai-topic").performTextInput("虚构植物的生长条件")
        compose.onNodeWithTag("ai-bank-name").assertTextContains("虚构植物的生长条件")
        assertTrue(runBlocking { db.questionBankDao().getAll().isEmpty() })
        compose.onNodeWithTag("ai-generate").performScrollTo().performClick()
        approveAll(5)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("题库").performClick()
        waitText("虚构植物的生长条件")
        compose.onNodeWithText("1 个题库").assertExists()
        compose.onNodeWithText(" · 5 题").assertExists()
        assertEquals(1, fake.topicCalls)
    }

    @Test fun editedNewBankNameIsPreservedWhenTopicChanges() {
        show(); enterFromHome()
        compose.onNodeWithTag("ai-bank-name").performTextClearance()
        compose.onNodeWithTag("ai-bank-name").performTextInput("虚构自定义题库")
        compose.onNodeWithTag("ai-topic").performTextInput("虚构主题")
        compose.onNodeWithTag("ai-bank-name").assertTextContains("虚构自定义题库")
        compose.onNodeWithTag("ai-generate").performScrollTo().performClick()
        approveAll(5)
        assertEquals("虚构自定义题库", runBlocking { db.questionBankDao().getAll().single().name })
    }

    @Test fun pastedMaterialRetriesAndLaterBatchesUseOneBank() {
        fake.failFirstMaterialBatch = true
        show(); enterFromHome()
        compose.onNodeWithText("从材料出题").performScrollTo().performClick()
        val material = "# 虚构植物复习\n" + "虚构植物喜光。".repeat(870)
        compose.onNodeWithTag("material-text").performTextInput(material)
        compose.onNodeWithTag("ai-bank-name").assertTextContains("虚构植物复习")
        compose.onNodeWithTag("material-generate").performScrollTo().performClick()
        compose.onNodeWithText("确认生成").performSemanticsAction(SemanticsActions.OnClick)
        waitDb { db.questionBankDao().getAll().size == 1 && fake.materialCalls >= 3 }
        compose.onNodeWithTag("material-form").performScrollToNode(hasText("重试第 1 批"))
        compose.onNodeWithText("重试第 1 批").performClick()
        waitDb { db.aiQuestionDraftDao().getByBank(db.questionBankDao().getAll().single().id).size == 5 }
        waitText("待校对 (5)")
        compose.onNodeWithText("待校对 (5)").performClick()
        approveAll(5)
        assertEquals("虚构植物复习", runBlocking { db.questionBankDao().getAll().single().name })
        assertEquals(4, fake.materialCalls)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("material-generate").performScrollTo().performClick()
        compose.onNodeWithText("确认生成").performSemanticsAction(SemanticsActions.OnClick)
        waitDb { db.questionBankDao().getAll().size == 1 && db.aiQuestionDraftDao().getByBank(db.questionBankDao().getAll().single().id).size == 5 }
        waitText("待校对 (5)")
        compose.onNodeWithText("待校对 (5)").performClick()
        approveAll(5, existingQuestions = 5)
        assertEquals(7, fake.materialCalls)
    }

    @Test fun docxExtractionUsesFileNameForNewBank() {
        val dir = File(app.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "虚构复习资料.docx")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>虚构植物喜光。</w:t></w:r></w:p></w:body></w:document>""".toByteArray())
            zip.closeEntry()
        }
        val uri = documentUri(file)
        show(uri); enterFromHome()
        compose.onNodeWithText("从材料出题").performScrollTo().performClick()
        compose.onNodeWithText("选择文件").performClick()
        waitText("虚构植物喜光")
        compose.onNodeWithTag("ai-bank-name").assertTextContains("虚构复习资料.docx")
        compose.onNodeWithTag("material-generate").performScrollTo().performClick()
        compose.onNodeWithText("确认生成").performSemanticsAction(SemanticsActions.OnClick)
        waitDb { db.questionBankDao().getAll().size == 1 && db.aiQuestionDraftDao().getByBank(db.questionBankDao().getAll().single().id).size == 5 }
        waitText("待校对 (5)")
        compose.onNodeWithText("待校对 (5)").performClick()
        approveAll(5)
        assertEquals("虚构复习资料.docx", runBlocking { db.questionBankDao().getAll().single().name })
        file.delete()
    }

    @Test fun pdfTextExtractionUsesFileNameForNewBank() {
        PDFBoxResourceLoader.init(app)
        val dir = File(app.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "虚构复习资料.pdf")
        PDDocument().use { document ->
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 12f)
                stream.newLineAtOffset(50f, 700f)
                stream.showText("Fictional plants need light and water.")
                stream.endText()
            }
            document.save(file)
        }
        val uri = documentUri(file)
        show(uri); enterFromHome()
        compose.onNodeWithText("从材料出题").performScrollTo().performClick()
        compose.onNodeWithText("选择文件").performClick()
        waitText("Fictional plants")
        compose.onNodeWithTag("ai-bank-name").assertTextContains("虚构复习资料.pdf")
        compose.onNodeWithTag("material-generate").performScrollTo().performClick()
        compose.onNodeWithText("确认生成").performSemanticsAction(SemanticsActions.OnClick)
        waitDb { db.questionBankDao().getAll().size == 1 && db.aiQuestionDraftDao().getByBank(db.questionBankDao().getAll().single().id).size == 5 }
        waitText("待校对 (5)")
        compose.onNodeWithText("待校对 (5)").performClick()
        approveAll(5)
        assertEquals("虚构复习资料.pdf", runBlocking { db.questionBankDao().getAll().single().name })
        file.delete()
    }

    @Test fun failureAndCancellationAndExitLeaveNoBank() {
        show(); enterFromHome()
        compose.onNodeWithContentDescription("返回").performClick()
        assertTrue(runBlocking { db.questionBankDao().getAll().isEmpty() })
        enterFromHome()
        compose.onNodeWithTag("ai-topic").performTextInput("虚构主题")
        fake.failTopic = true
        compose.onNodeWithTag("ai-generate").performScrollTo().performClick()
        waitText("返回格式错误")
        assertTrue(runBlocking { db.questionBankDao().getAll().isEmpty() })
        fake.failTopic = false
        fake.block = CompletableDeferred()
        compose.onNodeWithTag("ai-generate").performScrollTo().performClick()
        compose.waitUntil(10_000) { fake.topicCalls >= 2 }
        compose.onNodeWithContentDescription("返回").performClick()
        fake.block?.complete(Unit)
        compose.waitForIdle()
        assertTrue(runBlocking { db.questionBankDao().getAll().isEmpty() })
    }

    @Test fun existingBankReferencesAndLegacyEntryStayLocked() {
        val bank = runBlocking {
            val id = container.repository.createBank("虚构已有题库")
            container.repository.saveQuestion(Question(bankId = id, stem = "虚构参考题", sourceFileName = "虚构"))
            id
        }
        show(); enterFromHome()
        compose.waitUntil(10_000) { viewModel.banks.value.isNotEmpty() }
        compose.onNodeWithTag("ai-destination").performClick()
        compose.onNodeWithText("虚构已有题库").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("参考已有题目").performClick()
        waitText("虚构参考题")
        compose.onNodeWithText("虚构参考题").performClick()
        compose.onNodeWithTag("ai-generate").performScrollTo().performClick()
        approveAll(5, existingQuestions = 1)
        assertEquals(bank, runBlocking { db.questionBankDao().getAll().single().id })
        assertTrue(fake.source is AiQuestionSource.References)
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("题库").performClick()
        waitText("虚构已有题库")
        compose.onNodeWithText("虚构已有题库").performClick()
        waitText("AI 出题")
        compose.onNodeWithText("AI 出题").performClick()
        compose.onNodeWithTag("ai-destination").assertDoesNotExist()
        compose.onNodeWithText("虚构已有题库").assertExists()
        compose.onNodeWithText("参照题目").assertExists()
    }

    private class FakeQuestionClient : AiQuestionClient {
        @Volatile var topicCalls = 0
        @Volatile var materialCalls = 0
        @Volatile var failTopic = false
        @Volatile var failFirstMaterialBatch = false
        @Volatile var block: CompletableDeferred<Unit>? = null
        @Volatile var source: AiQuestionSource? = null
        private fun field() = AiDraftFields("SINGLE", "虚构生成题", listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")), "A", "虚构解析")
        override suspend fun generate(source: AiQuestionSource, type: QuestionType, count: Int, config: AiExplanationConfig): List<AiDraftFields> {
            topicCalls++; this.source = source
            if (failTopic) throw AiExplanationException(AiExplanationError.RESPONSE)
            block?.await()
            return List(count) { field() }
        }
        override suspend fun generateMaterial(batch: MaterialBatch, config: AiExplanationConfig): List<AiDraftFields> {
            materialCalls++
            if (batch.index == 1 && failFirstMaterialBatch) { failFirstMaterialBatch = false; throw AiExplanationException(AiExplanationError.RESPONSE) }
            return List(batch.counts.values.sum()) { field().copy(material = true, materialText = batch.text, sourceExcerpt = batch.text.take(4)) }
        }
    }

    private class FixtureDocumentProvider(private val file: File) : ContentProvider() {
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf(file.name)) }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }
}
