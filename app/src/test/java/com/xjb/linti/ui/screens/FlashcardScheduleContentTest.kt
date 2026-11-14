package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class FlashcardScheduleContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun frontBackRatingAndCompletion() {
        var rated: Boolean? = null
        compose.setContent {
            var show by remember { mutableStateOf(false) }
            var finished by remember { mutableStateOf(false) }
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(Modifier.fillMaxSize()) {
                    FlashcardStudyContent(
                        questions = if (finished) emptyList() else listOf(fakeQuestion()), index = 0,
                        dueCount = if (finished) 0 else 1, all = false, loading = false,
                        nextDueText = "2026-06-02 10:00", showAnswer = show, rememberedDays = 4,
                        saving = false, onBack = {}, onModeChange = {}, onToggleAnswer = { show = !show },
                        onPrevious = {}, onNext = {}, onReview = { rated = it; show = false; finished = true },
                    )
                }
            }
        }
        compose.onNodeWithText("待复习 1").assertExists()
        compose.onNodeWithText("虚构题面").assertExists()
        compose.onNodeWithText("忘记 · 10 分钟后").assertDoesNotExist()
        compose.onNodeWithTag("flashcard-flip-button").performClick()
        compose.onNodeWithText("忘记 · 10 分钟后").assertExists()
        compose.onNodeWithText("记得 · 4 天后").assertExists()
        compose.onNodeWithTag("flashcard-remembered").performClick()
        assertEquals(true, rated)
        compose.onNodeWithText("今天复习完了").assertExists()
        compose.onNodeWithText("下次到期：2026-06-02 10:00").assertExists()
    }

    @Test fun allModeKeepsNavigationAndForgetAction() {
        var rated: Boolean? = null
        compose.setContent {
            var all by remember { mutableStateOf(false) }
            var index by remember { mutableIntStateOf(0) }
            var show by remember { mutableStateOf(false) }
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(Modifier.fillMaxSize()) {
                    FlashcardStudyContent(
                        questions = listOf(fakeQuestion(), fakeQuestion().copy(id = 2, stem = "第二道虚构题")),
                        index = index, dueCount = 2, all = all, loading = false, nextDueText = null,
                        showAnswer = show, rememberedDays = 1, saving = false, onBack = {},
                        onModeChange = { all = it; index = 0; show = false }, onToggleAnswer = { show = !show },
                        onPrevious = { index = 0; show = false }, onNext = { index = 1; show = false },
                        onReview = { rated = it; index = 0; show = false },
                    )
                }
            }
        }
        compose.onNodeWithText("全部").performClick()
        compose.onNodeWithTag("flashcard-next").performClick()
        compose.onNodeWithText("第二道虚构题").assertExists()
        compose.onNodeWithTag("flashcard-flip-button").performClick()
        compose.onNodeWithTag("flashcard-forgot").performClick()
        assertEquals(false, rated)
        compose.onNodeWithText("虚构题面").assertExists()
        compose.onNodeWithText("看答案").assertExists()
    }

    @Test fun completionWithoutDueTimeAndDailySetting() {
        var selected: Int? = 20
        compose.setContent {
            var limit by remember { mutableStateOf<Int?>(20) }
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                androidx.compose.foundation.layout.Column {
                    DailyNewCardsSettingsContent(limit) { limit = it; selected = it }
                    FlashcardStudyContent(emptyList(), 0, 0, false, false, null, false, 1, false,
                        {}, {}, {}, {}, {}, {})
                }
            }
        }
        compose.onNodeWithText("每日新卡").assertExists()
        listOf("10", "20", "50", "不限").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("不限").performClick()
        assertNull(selected)
        compose.onNodeWithText("今天复习完了").assertExists()
        compose.onAllNodesWithText("下次到期", substring = true).assertCountEquals(0)
    }
}

internal fun fakeQuestion() = Question(id = 1, bankId = 1, stem = "虚构题面", options = listOf(QuestionOption("A", "虚构选项甲"), QuestionOption("B", "虚构选项乙")), answer = "A", explanation = "虚构解析")
