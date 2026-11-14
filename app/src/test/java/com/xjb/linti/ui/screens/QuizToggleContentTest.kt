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
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.ui.theme.LintiTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class QuizToggleContentTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: QuizToggleTestFixture

    @Before fun setup() = runBlocking { fixture = QuizToggleTestFixture(); fixture.setup() }
    @After fun close() { fixture.close() }

    private fun showQuiz() {
        compose.setContent {
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(Modifier.fillMaxSize()) { QuizScreen(fixture.viewModel, onBack = {}, onAnswerSheet = {}) }
            }
        }
    }

    @Test fun selectionIsRetainedWhenTogglingAndSwipingMarksNewQuestion() = runBlocking {
        fixture.startPractice()
        fixture.viewModel.selectAnswer(1, "B")
        showQuiz()
        compose.onNodeWithText("答题").assertIsSelected()
        compose.onNodeWithText("背题").assertIsNotSelected()
        compose.onNodeWithText("正确答案：AC").assertDoesNotExist()
        compose.onNodeWithContentDescription("B，虚构选项乙，已选").assertExists()
        compose.onNodeWithText("背题").performClick()
        compose.onNodeWithText("背题").assertIsSelected()
        compose.onNodeWithContentDescription("A，虚构选项甲，正确选项").assertExists()
        compose.onNodeWithContentDescription("B，虚构选项乙，错误选项，已选").assertExists()
        compose.onNodeWithText("正确答案：AC").assertExists()
        compose.onNodeWithText("虚构解析 1").assertExists()
        compose.onRoot().performTouchInput { swipeLeft() }
        compose.waitUntil(10_000) { fixture.viewModel.quizState.value.currentIndex == 1 }
        assertEquals(setOf(1L, 2L), fixture.viewModel.quizState.value.answerViewedIds)
        compose.onNodeWithText("虚构题干 2").assertExists()
        compose.onNodeWithText("正确答案：AC").assertExists()
        compose.onNodeWithText("答题").performClick()
        assertEquals(1, fixture.viewModel.quizState.value.currentIndex)
        compose.runOnIdle { fixture.viewModel.goToQuestion(0) }
        compose.waitUntil(10_000) { fixture.viewModel.quizState.value.currentIndex == 0 }
        compose.onNodeWithContentDescription("B，虚构选项乙，已选").assertExists()
        compose.onNodeWithText("正确答案：AC").assertDoesNotExist()
    }

    @Test fun allFourPracticeModesShowToggle() = runBlocking {
        fixture.startPractice(PracticeMode.FAVORITE)
        showQuiz()
        for (mode in listOf(PracticeMode.FAVORITE, PracticeMode.WRONG, PracticeMode.TOPIC, PracticeMode.SEQUENCE)) {
            fixture.startPractice(mode)
            compose.waitForIdle()
            compose.onNodeWithText("背题").assertExists()
            compose.onNodeWithText("答题").assertIsSelected()
        }
    }

    @Test fun examNeverShowsToggleAndDoesNotRevealBeforeSubmission() = runBlocking {
        fixture.startExam()
        showQuiz()
        compose.onNodeWithText("背题").assertDoesNotExist()
        compose.onNodeWithText("正确答案：AC").assertDoesNotExist()
        compose.runOnIdle { fixture.viewModel.setReciting(true) }
        compose.onNodeWithText("正确答案：AC").assertDoesNotExist()
        assertFalse(fixture.viewModel.quizState.value.reciting)
    }

    @Test fun roundSummaryAndAnswerSheetExcludeViewedCorrectButKeepItInDenominator() = runBlocking {
        fixture.startPractice()
        val viewModel = fixture.viewModel
        viewModel.setReciting(true)
        viewModel.setReciting(false)
        viewModel.selectAnswer(1, "A")
        viewModel.selectAnswer(1, "C")
        viewModel.commitAnswer(1)
        viewModel.goToQuestion(1)
        viewModel.selectAnswer(2, "B")
        viewModel.commitAnswer(2)
        viewModel.goToQuestion(2)
        viewModel.selectAnswer(3, "A")
        viewModel.selectAnswer(3, "C")
        viewModel.commitAnswer(3)
        var showSheet by mutableStateOf(false)
        compose.setContent {
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(Modifier.fillMaxSize()) {
                    if (showSheet) AnswerSheetScreen(viewModel) { showSheet = false }
                    else QuizScreen(viewModel, {}, { showSheet = true })
                }
            }
        }
        compose.onNodeWithText("✓ 对 1").assertExists()
        compose.onNodeWithText("✗ 错 1").assertExists()
        compose.onNodeWithText("对 1 · 错 1 · 正确率 33%").assertExists()
        compose.runOnIdle { showSheet = true }
        compose.onNodeWithText("正确 1 · 错误 1 · 未做 0").assertExists()
        Unit
    }

    @Test fun paperReviewHasNoToggleAndRejectsCallsUntilItIsDisposed() = runBlocking {
        val sessionId = fixture.createReview()
        fixture.startPractice()
        var showReview by mutableStateOf(true)
        compose.setContent {
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(Modifier.fillMaxSize()) {
                    if (showReview) ExamPaperReviewScreen(fixture.viewModel, sessionId, false, {})
                    else QuizScreen(fixture.viewModel, {}, {})
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("虚构原卷题干").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("背题").assertDoesNotExist()
        val before = fixture.viewModel.quizState.value
        compose.runOnIdle { fixture.viewModel.setReciting(true) }
        assertEquals(before, fixture.viewModel.quizState.value)
        assertFalse(fixture.viewModel.isRevealed(1))
        compose.runOnIdle { showReview = false }
        compose.onNodeWithText("背题").performClick()
        assertTrue(fixture.viewModel.quizState.value.reciting)
    }
}
