package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.PastPaper
import com.xjb.linti.domain.PastPaperItem
import com.xjb.linti.domain.PastPaperSource
import com.xjb.linti.domain.Question
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

internal val fictionalPapers = listOf(
    PastPaper(1, 1, 2023, "虚构旧卷", PastPaperSource.RECALLED, questionCount = 1),
    PastPaper(2, 1, 2025, "虚构B卷", PastPaperSource.OFFICIAL, questionCount = 2),
    PastPaper(3, 1, 2025, "虚构A卷", questionCount = 3),
)
internal val fictionalQuestions = listOf(
    Question(id = 11, bankId = 1, sourceNumber = "一", stem = "虚构题干甲"),
    Question(id = 12, bankId = 1, sourceNumber = "二", stem = "虚构题干乙"),
    Question(id = 13, bankId = 1, sourceNumber = "三", stem = "虚构题干丙"),
)
internal val fictionalItems = listOf(PastPaperItem(2, 11, 1, "一"), PastPaperItem(2, 12, 2, "二"))

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class PastPaperContentTest {
    @get:Rule val compose = createComposeRule()
    @Test fun groupedListShowsSourceCountAndOpensPaper() {
        var opened: Long? = null
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            PastPaperListContent(fictionalPapers, {}, {}, { opened = it })
        } } }
        compose.onNodeWithTag("paper-year-2025").assertExists()
        compose.onNodeWithTag("paper-year-2023").assertExists()
        compose.onNodeWithText("官方发布").assertExists()
        compose.onNodeWithText("未核实").assertExists()
        compose.onNodeWithText("3 题").assertExists()
        compose.onNodeWithTag("paper-3").performClick()
        compose.runOnIdle { assertEquals(3L, opened) }
    }
    @Test fun formDefaultsToUnverifiedAndRequiresYearAndName() {
        var saved: PastPaperSource? = null
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            PastPaperFormContent(null, {}) { _, _, source, _ -> saved = source }
        } } }
        compose.onNodeWithTag("paper-source-UNVERIFIED").assertIsSelected()
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithTag("paper-form-year").performTextInput("2025")
        compose.onNodeWithTag("paper-form-name").performTextInput("虚构官方卷")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(PastPaperSource.UNVERIFIED, saved) }
        compose.onNodeWithTag("paper-source-RECALLED").performClick().assertIsSelected()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(PastPaperSource.RECALLED, saved) }
    }
    @Test fun detailsDisplayNumbersValidateDuplicatesAndConfirmDelete() {
        var deleted = false
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            PastPaperDetailContent(fictionalPapers[1], fictionalItems, fictionalQuestions, {}, {}, { deleted = true }, {}, { _, _ -> }, {}, { _, _ -> }, {})
        } } }
        compose.onNodeWithText("一. 虚构题干甲").assertExists()
        compose.onNodeWithText("二. 虚构题干乙").assertExists()
        compose.onNodeWithContentDescription("编辑题号二").performClick()
        compose.onNodeWithTag("paper-original-number").performTextClearance()
        compose.onNodeWithTag("paper-original-number").performTextInput("一")
        compose.onNodeWithText("原卷题号已存在").assertExists()
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithContentDescription("删除卷").performClick()
        compose.onNodeWithText("删除 虚构B卷？").assertExists()
        compose.onNodeWithText("删除").performClick()
        compose.runOnIdle { assertTrue(deleted) }
    }
    @Test fun selectingQuestionsPreservesClickOrder() {
        var added = emptyList<Long>()
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            PastPaperQuestionSelectionContent(fictionalQuestions, {}, { added = it })
        } } }
        compose.onNodeWithTag("paper-select-13").performClick()
        compose.onNodeWithTag("paper-select-11").performClick()
        compose.onNodeWithTag("paper-select-12").performClick()
        compose.onNodeWithText("加入 (3)").performClick()
        compose.runOnIdle { assertEquals(listOf(13L, 11L, 12L), added) }
    }
}
