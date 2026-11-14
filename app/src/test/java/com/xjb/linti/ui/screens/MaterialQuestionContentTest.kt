package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.aigeneration.*
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.domain.*
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class MaterialQuestionContentTest {
    @get:Rule val compose = createComposeRule()
    @Test fun formShowsFiveTypesAndRetry() {
        val batch = MaterialBatch(2, "虚构材料", mapOf(QuestionType.ESSAY to 1))
        var retry = -1
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) {
            MaterialQuestionFormContent("虚构材料", QuestionType.entries.associateWith { 1 }, false,
                listOf(MaterialBatchStatus(batch, "失败", AiExplanationError.RESPONSE)), null, null,
                {}, { _, _ -> }, {}, {}, {}, { retry = it }, {})
        } } }
        QuestionType.entries.forEach { compose.onNodeWithText(it.label).performScrollTo().assertExists() }
        compose.onNodeWithText("返回格式错误").performScrollTo().assertExists()
        compose.onNodeWithText("重试第 2 批").performScrollTo().performClick(); assertEquals(0, retry)
    }
    @Test fun essayRevealsThenAllowsSelfAssessmentWithoutText() {
        var shown by mutableStateOf(false); var assessed by mutableStateOf<Boolean?>(null)
        compose.setContent { LintiTheme { Surface { Column {
            EssayAnswerContent("", "虚构参考要点", shown, assessed != null, false, onAnswer = {}, onReveal = { shown = true }, onAssess = { assessed = it })
        } } } }
        compose.onNodeWithText("虚构参考要点", substring = true).assertDoesNotExist()
        compose.onNodeWithText("看参考答案").performClick()
        compose.onNodeWithText("虚构参考要点", substring = true).assertExists()
        compose.onNodeWithText("没答对").performClick(); assertEquals(false, assessed)
    }
    @Test fun reciteShowsReferenceImmediatelyAndDraftShowsSource() {
        val fields = AiDraftFields(type = "ESSAY", stem = "虚构问题", answer = "虚构要点", explanation = "解析", material = true, materialText = "虚构出处", sourceExcerpt = "虚构出处")
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { Column {
            EssayAnswerContent("", "虚构参考要点", true, false, true, onAnswer = {}, onReveal = {}, onAssess = {})
            AiDraftListContent(listOf(AiQuestionDraft(1, 1, fields, emptyList(), 1)), {}, {}, {}, {})
        } } } }
        compose.onNodeWithText("参考答案：虚构参考要点").assertExists()
        compose.onNodeWithText("看参考答案").assertDoesNotExist()
        compose.onNodeWithText("出处：虚构出处").assertExists()
    }
    @Test fun confirmationAndDetailShowHostAndSource() {
        compose.setContent { LintiTheme { Surface { Column {
            MaterialSendConfirmation(42, 3, "api.example.test", {}, {})
            QuestionDetailContent(Question(stem = "虚构题", answer = "甲", sourceExcerpt = "虚构原文"))
        } } } }
        compose.onNodeWithText("api.example.test", substring = true).assertExists()
        compose.onNodeWithText("出处：虚构原文").assertExists()
    }
}
