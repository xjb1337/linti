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
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.Chapter
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class ChapterContentTest {
    @get:Rule val compose = createComposeRule()
    private val chapters = listOf(Chapter(11, 1, "虚构甲", 0), Chapter(12, 1, "虚构乙", 1))
    private val questions = listOf(topicQuestion(1, 1, QuestionType.SINGLE).copy(chapterId = 11), topicQuestion(2, 1, QuestionType.MULTI).copy(chapterId = 11), topicQuestion(3, 1, QuestionType.SINGLE))
    private fun chapterUi(onMove: (Set<Long>, Long?) -> Unit = { _, _ -> }) {
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            ChapterContent(chapters, questions, {}, {}, { _, _ -> }, {}, { _, _ -> }, onMove)
        } } }
    }
    @Test fun countsAndDeleteConfirmation() {
        chapterUi()
        compose.onNodeWithText("虚构甲").assertExists()
        compose.onNodeWithText("2 题").assertExists()
        compose.onNodeWithText("未分类").assertExists()
        compose.onNodeWithText("1 题").assertExists()
        compose.onNodeWithContentDescription("删除虚构甲").performClick()
        compose.onNodeWithText("2 题将回到未分类").assertExists()
    }
    @Test fun duplicateNameDisablesSaveAndDisplaysError() {
        chapterUi()
        compose.onNodeWithText("新建").performClick()
        compose.onNodeWithTag("chapter-name").performTextInput("虚构甲")
        compose.onNodeWithText("章节名称已存在").assertExists()
        compose.onNodeWithText("保存").assertIsNotEnabled()
    }
    @Test fun batchMoveSelectedQuestions() {
        var moved: Pair<Set<Long>, Long?>? = null
        chapterUi { ids, target -> moved = ids to target }
        compose.onNodeWithText("虚构甲").performClick()
        compose.onNodeWithTag("chapter-question-1").performClick()
        compose.onNodeWithTag("chapter-question-2").performClick()
        compose.onNodeWithText("已选 2 题").assertExists()
        compose.onNodeWithText("移到…").performClick()
        compose.onNodeWithText("未分类").performClick()
        compose.runOnIdle { assertEquals(setOf(1L, 2L) to null, moved) }
    }
    @Test fun singleBankChapterFilterChangesCountsAndMultiBankHidesIt() {
        val banks = listOf(topicBank(1, "虚构题库甲"), topicBank(2, "虚构题库乙"))
        val all = (1L..12L).map { topicQuestion(it, 1, QuestionType.SINGLE).copy(chapterId = if (it <= 8) 11 else null) } + topicQuestion(99, 2, QuestionType.MULTI)
        var captured: Set<Long?>? = null
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            TopicPracticeSetupContent(banks, all, 1, {}, { _, _, _ -> }, chapters = chapters,
                onStartFiltered = { _, _, _, ids -> captured = ids })
        } } }
        compose.onNodeWithTag("topic-chapter-11").assertIsSelected().assertTextContains("虚构甲 8")
        compose.onNodeWithTag("topic-chapter-12").assertIsNotEnabled()
        compose.onNodeWithTag("topic-chapter-uncategorized").performClick()
        compose.onNodeWithTag("topic-total").assertTextContains("可练 8 题")
        compose.onNodeWithTag("topic-type-SINGLE").assertTextContains("单选 8")
        compose.onNodeWithTag("topic-count-8").assertExists()
        compose.onNodeWithTag("topic-count-10").assertIsNotEnabled()
        compose.onNodeWithTag("topic-start").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(setOf(11L, 12L), captured) }
        compose.onNodeWithTag("topic-bank-2").performScrollTo().performClick()
        compose.onNodeWithTag("topic-chapter-11").assertDoesNotExist()
        compose.onNodeWithTag("topic-total").assertTextContains("可练 13 题")
        compose.onNodeWithTag("topic-bank-2").performClick()
        compose.onNodeWithTag("topic-chapter-uncategorized").assertIsSelected()
    }
}
