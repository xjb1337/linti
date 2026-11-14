package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.data.aigeneration.AiDraftFields
import com.xjb.linti.data.aigeneration.AiQuestionDraft
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
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class AiQuestionScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Before fun recordOnly() { assumeTrue(System.getProperty("roborazzi.test.record") == "true") }
    private val options = listOf(QuestionOption("A", "虚构的甲"), QuestionOption("B", "虚构的乙"))
    @Test fun form() {
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { Column {
            LintiPageHeader(title = "AI 出题", onBack = {})
            AiQuestionFormContent(true, "虚构植物的生长条件", emptySet(), QuestionType.SINGLE, 3, emptyList(), false, null,
                {}, {}, {}, {}, {}, {}, {})
        } } } }
        capture("ai_question_form")
    }
    @Test fun review() {
        val fields = AiDraftFields("SINGLE", "虚构植物在什么条件下生长？", options, "", "虚构解析")
        val drafts = listOf(AiQuestionDraft(1, 1, fields, listOf("待补答案"), 1), AiQuestionDraft(2, 1, fields.copy(answer = "A"), emptyList(), 2))
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { Column {
            LintiPageHeader(title = "待校对", onBack = {})
            AiDraftListContent(drafts, {}, {}, {}, {})
        } } } }
        capture("ai_question_review")
    }
    @Test fun originLabel() {
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { Column {
            LintiPageHeader(title = "题目详情", onBack = {})
            ReciteCard(Question(id = 1, stem = "虚构植物在什么条件下生长？", options = options, answer = "A", explanation = "虚构解析", origin = QuestionOrigin.AI_GENERATED))
        } } } }
        capture("ai_question_origin")
    }
    private fun capture(name: String) { compose.waitForIdle(); compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/$name.png") }
}
