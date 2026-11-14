package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.data.aigeneration.*
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.domain.*
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h1200dp-xxhdpi", application = android.app.Application::class)
class MaterialQuestionScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Before fun recordOnly() { assumeTrue(System.getProperty("roborazzi.test.record") == "true") }
    private val batch = MaterialBatch(1, "虚构材料：植物喜光。", mapOf(QuestionType.ESSAY to 1))
    private fun form(batches: List<MaterialBatchStatus> = emptyList()) {
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { Column {
            LintiPageHeader(title = "AI 出题", onBack = {})
            MaterialQuestionFormContent(batch.text, QuestionType.entries.associateWith { 1 }, false, batches, null, null,
                {}, { _, _ -> }, {}, {}, {}, {}, {})
        } } } }
    }
    @Test fun materialForm() { form(); capture("material_form") }
    @Test fun confirmation() {
        compose.setContent { LintiTheme { MaterialSendConfirmation(42, 3, "api.deepseek.com", {}, {}) } }
        capture("material_confirmation")
    }
    @Test fun progress() { form(listOf(MaterialBatchStatus(batch, "已完成"), MaterialBatchStatus(batch.copy(index = 2), "生成中"), MaterialBatchStatus(batch.copy(index = 3), "失败", AiExplanationError.RESPONSE))); capture("material_progress") }
    @Test fun draftSource() {
        val field = AiDraftFields(type = "ESSAY", stem = "虚构植物喜欢什么？", answer = "光照", explanation = "虚构解析", sourceExcerpt = "植物喜光", materialText = batch.text, material = true)
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { AiDraftListContent(listOf(AiQuestionDraft(1, 1, field, emptyList(), 1)), {}, {}, {}, {}) } } }
        capture("material_draft_source")
    }
    @Test fun essayBefore() { essay(false); capture("essay_before") }
    @Test fun essayAfter() { essay(true); capture("essay_after") }
    private fun essay(shown: Boolean) { compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { Column {
        LintiPageHeader(title = "问答", onBack = {})
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("虚构植物需要什么生长条件？")
            EssayAnswerContent("", "充足的光照与水分。", shown, false, false, onAnswer = {}, onReveal = {}, onAssess = {})
        }
    } } } } }
    @Test fun detailSource() {
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { Column {
            LintiPageHeader(title = "题目详情", onBack = {})
            QuestionDetailContent(Question(type = QuestionType.ESSAY, stem = "虚构问题", answer = "光照", sourceExcerpt = "植物喜光", origin = QuestionOrigin.AI_GENERATED))
        } } } }
        capture("material_detail_source")
    }
    private fun capture(name: String) { compose.waitForIdle(); compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/$name.png") }
}
