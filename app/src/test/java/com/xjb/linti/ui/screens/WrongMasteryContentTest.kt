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
import com.xjb.linti.domain.*
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

internal fun masteryBank() = BankSummary(1, "虚构题库", null, 1, 1, 2, 2, 1, 1, 0, 0, 0, 0, null)

internal fun masteryItems() = listOf(
    WrongQuestionItem(WrongItemState(1, 1, 3, 100, false, true, 1, null), "虚构题库", "1", QuestionType.SINGLE, "虚构待复习题"),
    WrongQuestionItem(WrongItemState(2, 1, 2, 200, true, false, 2, 300), "虚构题库", "2", QuestionType.MULTI, "虚构已掌握题"),
)

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class WrongMasteryContentTest {
    @get:Rule val compose = createComposeRule()

    @Test fun groupsProgressPracticeAndManualRemoval() {
        var practiced: Set<Long>? = null
        var removed: Long? = null
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            WrongBookContent(masteryItems(), listOf(masteryBank()), null, 2, {}, { practiced = it }, { removed = it }, now = 1000)
        } } }
        compose.onNodeWithText("待复习 1").assertIsSelected()
        compose.onNodeWithText("已掌握 1").assertIsNotSelected()
        compose.onNodeWithText("连对 1/2").assertExists()
        compose.onNodeWithText("1. 虚构待复习题").assertExists()
        compose.onNodeWithText("2. 虚构已掌握题").assertDoesNotExist()
        compose.onNodeWithText("一键重练").performClick()
        compose.runOnIdle { assertEquals(setOf(1L), practiced) }
        compose.onNodeWithText("移出").performClick()
        compose.runOnIdle { assertEquals(1L, removed) }
        compose.onNodeWithText("已掌握 1").performClick()
        compose.onNodeWithText("连对 2/2").assertExists()
        compose.onNodeWithText("2. 虚构已掌握题").assertExists()
        compose.onNodeWithText("一键重练").assertDoesNotExist()
        compose.onNodeWithText("移出").assertDoesNotExist()
    }

    @Test fun disabledThresholdOmitsDenominatorAndSettingChanges() {
        var threshold by mutableStateOf<Int?>(2)
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Column {
                MasterySettingsContent(threshold) { threshold = it }
                WrongBookContent(masteryItems(), emptyList(), null, threshold, {}, {}, {}, now = 1000)
            }
        } } }
        compose.onNodeWithText("掌握所需连对").assertExists()
        compose.onNodeWithText("2 次").assertIsSelected()
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { assertNull(threshold) }
        compose.onNodeWithText("连对 1").assertExists()
        compose.onNodeWithText("连对 1/2").assertDoesNotExist()
        compose.onNodeWithText("1 次").performClick()
        compose.runOnIdle { assertEquals(1, threshold) }
        compose.onNodeWithText("5 次").performClick()
        compose.runOnIdle { assertEquals(5, threshold) }
    }
}
