package com.xjb.linti.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.explanation.AiExplanationClient
import com.xjb.linti.data.explanation.AiExplanationConfig
import com.xjb.linti.data.explanation.AiExplanationRepository
import com.xjb.linti.data.explanation.FakeExplanationSettings
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.Question
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LocalAiExplanationSettings
import com.xjb.linti.ui.components.AiExplanationSection
import com.xjb.linti.ui.theme.LintiTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class AiExplanationContentTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: QuizToggleTestFixture
    private val settings = FakeExplanationSettings()
    private var calls = 0
    private var openedSettings = 0
    private var rendering by mutableStateOf(true)
    private val fake = object : AiExplanationClient {
        override suspend fun explain(question: Question, config: AiExplanationConfig): String {
            calls++; return "虚构 AI 解析"
        }
    }

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fixture = QuizToggleTestFixture(); fixture.setup()
        val app = ApplicationProvider.getApplicationContext<Application>()
        fixture.viewModel = LintiViewModel(app, fixture.container,
            aiExplanationRepository = AiExplanationRepository(fixture.db, settings, fake))
    }
    @After fun close() {
        compose.runOnIdle { rendering = false }
        compose.waitForIdle()
        fixture.close()
        Dispatchers.resetMain()
    }

    private fun showQuiz() {
        compose.setContent {
            if (rendering) LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                CompositionLocalProvider(LocalAiExplanationSettings provides { openedSettings++ }) {
                    Surface(Modifier.fillMaxSize()) { QuizScreen(fixture.viewModel, {}, {}) }
                }
            }
        }
    }

    @Test fun beforeAnswerHiddenAfterCommitVisibleAndNothingSentUntilClicked() = runBlocking {
        fixture.startPractice()
        showQuiz()
        compose.onNodeWithTag("ai-explanation-generate").assertDoesNotExist()
        assertEquals(0, calls)
        compose.runOnIdle {
            fixture.viewModel.selectAnswer(1, "A"); fixture.viewModel.selectAnswer(1, "C")
            fixture.viewModel.commitAnswer(1)
        }
        compose.waitUntil(10_000) { fixture.viewModel.quizState.value.committedIds.contains(1) }
        compose.onNodeWithTag("ai-explanation-generate").assertExists().performScrollTo()
        assertEquals(0, calls)
        compose.onNodeWithTag("ai-explanation-generate").performClick()
        compose.waitUntil(10_000) { calls == 1 || fixture.viewModel.aiExplanationRequests.value[1]?.error != null }
        assertEquals("请求状态 ${fixture.viewModel.aiExplanationRequests.value}", 1, calls)
        compose.waitUntil(10_000) { runBlocking { fixture.db.aiExplanationDao().get(1) != null } }
        assertEquals("虚构 AI 解析", fixture.db.aiExplanationDao().get(1)!!.content)
    }

    @Test fun missingConfigurationNavigatesToSettingsWithoutCallingClient() = runBlocking {
        settings.key = ""
        fixture.startPractice()
        fixture.viewModel.setReciting(true)
        showQuiz()
        compose.onNodeWithTag("ai-explanation-generate").performScrollTo().performClick()
        assertEquals(1, openedSettings)
        assertEquals(0, calls)
    }

    @Test fun examNeverShowsButtonBeforeSubmission() = runBlocking {
        fixture.startExam(); showQuiz()
        compose.onNodeWithTag("ai-explanation-generate").assertDoesNotExist()
        assertEquals(0, calls)
    }

    @Test fun examEvenWithImmediateFeedbackHasNoButton() = runBlocking {
        assertTrue(fixture.viewModel.startExam(1, ExamConfig(multiCount = 1, durationMinutes = 30, passScore = 0.0, showAnswerAfterAnswer = true)).started)
        val id = fixture.viewModel.quizState.value.currentQuestion!!.questionId
        fixture.viewModel.selectAnswer(id, "A")
        fixture.viewModel.commitAnswer(id)
        assertTrue(fixture.viewModel.quizState.value.revealed(id))
        showQuiz()
        compose.onNodeWithTag("ai-explanation-generate").assertDoesNotExist()
        assertEquals(0, calls)
    }

    @Test fun changedExamSnapshotOpensLatestQuestionBeforeGeneration() = runBlocking {
        val session = fixture.createReview()
        compose.setContent {
            if (rendering) LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(Modifier.fillMaxSize()) { ExamPaperReviewScreen(fixture.viewModel, session, false, {}) }
            }
        }
        compose.waitUntil(10_000) { fixture.viewModel.examPaperReview.value.review != null }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("ai-review-latest").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ai-review-latest").performScrollTo().performClick()
        compose.onNodeWithText("题目详情").assertExists()
        assertEquals(0, calls)
        compose.onNodeWithTag("ai-explanation-generate").performScrollTo().performClick()
        compose.waitUntil(10_000) { calls == 1 }
        compose.waitUntil(10_000) { runBlocking { fixture.db.aiExplanationDao().get(1) != null } }
    }

    @Test fun flashcardShowsButtonOnlyAfterFlip() = runBlocking {
        val question = fixture.container.repository.getQuestion(1)!!
        var flipped by mutableStateOf(false)
        compose.setContent {
            if (rendering) LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                FlashcardContent(listOf(question), 0, flipped, { flipped = !flipped }, {}, {},
                    answerExtra = { AiExplanationSection(it.id, fixture.viewModel) })
            }
        }
        compose.onNodeWithTag("ai-explanation-generate").assertDoesNotExist()
        compose.onNodeWithTag("flashcard-flip-button").performClick()
        compose.onNodeWithTag("ai-explanation-generate").assertExists()
        assertEquals(0, calls)
    }
}
