package com.xjb.linti.ui.screens

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
class AiQuestionContentTest {
    @get:Rule val compose = createComposeRule()
    private val valid = AiDraftFields("SINGLE", "虚构题干：哪项是甲？", listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")), "A", "虚构解析")
    @Test fun formOnlyCallsGenerateWhenTappedAndShowsSettingsError() {
        var topic by mutableStateOf("")
        var calls = 0
        var settings = 0
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) {
            AiQuestionFormContent(true, topic, emptySet(), QuestionType.SINGLE, 1, emptyList(), false, AiExplanationError.NOT_CONFIGURED,
                {}, { topic = it }, {}, {}, {}, { settings++ }, { calls++ })
        } } }
        assertEquals(0, calls)
        compose.onNodeWithTag("ai-generate").performScrollTo().assertIsNotEnabled()
        compose.onAllNodesWithText("知识点 / 主题").assertCountEquals(2)
        compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput("虚构植物")
        compose.onNodeWithText("前往设置").performScrollTo().performClick()
        assertEquals(1, settings); assertEquals(0, calls)
        compose.onNodeWithTag("ai-generate").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, calls)
        compose.onNodeWithText("填空").assertDoesNotExist()
    }
    @Test fun reviewBlocksProblemDraftAndOffersEditAndBatch() {
        var edited = 0L; var approved = 0L; var batches = 0
        val drafts = listOf(AiQuestionDraft(1, 7, valid, emptyList(), 1),
            AiQuestionDraft(2, 7, valid.copy(answer = ""), listOf("待补答案"), 2))
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) {
            AiDraftListContent(drafts, { edited = it.id }, { approved = it.id }, {}, { batches++ })
        } } }
        compose.onAllNodesWithText("待补答案")[0].performScrollTo().assertExists()
        compose.onAllNodesWithText("通过")[1].assertIsNotEnabled()
        compose.onAllNodesWithText("编辑")[1].performClick(); assertEquals(2L, edited)
        compose.onAllNodesWithText("通过")[0].performScrollTo().performClick(); assertEquals(1L, approved)
        compose.onNodeWithText("通过全部可入库题目").performScrollTo().performClick(); assertEquals(1, batches)
    }
    @Test fun aiLabelAppearsOnQuestionAndAiQuestionCannotBeSelectedForPaper() {
        val question = Question(id = 1, stem = "虚构生成题", origin = QuestionOrigin.AI_GENERATED)
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { ReciteCard(question) } } }
        compose.onNodeWithText("AI 出题").assertExists()
    }
    @Test fun paperSelectionExcludesAiQuestions() {
        val questions = listOf(Question(id = 1, stem = "虚构导入题"), Question(id = 2, stem = "虚构生成题", origin = QuestionOrigin.AI_GENERATED))
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { PastPaperQuestionSelectionContent(questions, {}, {}) } } }
        compose.onNodeWithText("虚构导入题").assertExists()
        compose.onNodeWithText("虚构生成题").assertDoesNotExist()
    }
}
