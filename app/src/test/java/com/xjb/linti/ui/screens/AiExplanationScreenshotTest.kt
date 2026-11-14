package com.xjb.linti.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.data.db.AiExplanationEntity
import com.xjb.linti.data.explanation.AiExplanationClient
import com.xjb.linti.data.explanation.AiExplanationConfig
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.data.explanation.AiExplanationRepository
import com.xjb.linti.data.explanation.FakeExplanationSettings
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.Question
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.theme.LintiTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class AiExplanationScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private var fixture: QuizToggleTestFixture? = null
    private val gate = CompletableDeferred<Unit>()
    private var shouldFail = false
    private var rendering by mutableStateOf(true)
    private val fake = object : AiExplanationClient {
        override suspend fun explain(question: Question, config: AiExplanationConfig): String {
            if (shouldFail) throw AiExplanationException(AiExplanationError.TIMEOUT)
            gate.await(); return "新的虚构解析"
        }
    }

    @Before fun setup() = runBlocking {
        assumeTrue("截图只在 -Proborazzi.test.record=true 时录制", System.getProperty("roborazzi.test.record") == "true")
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fixture = QuizToggleTestFixture().also { it.setup() }
        val f = fixture!!
        f.db.aiExplanationDao().upsert(AiExplanationEntity(1, "根据给定答案，虚构选项甲与丙符合题意。", "fictional-model", "CUSTOM", 1, 2))
        f.viewModel = LintiViewModel(ApplicationProvider.getApplicationContext<Application>(), f.container,
            aiExplanationRepository = AiExplanationRepository(f.db, FakeExplanationSettings(), fake))
        f.startPractice()
        f.viewModel.selectAnswer(1, "A")
        f.viewModel.selectAnswer(1, "C")
        f.viewModel.commitAnswer(1)
    }
    @After fun close() {
        gate.cancel()
        if (fixture != null) {
            compose.runOnIdle { rendering = false }
            compose.waitForIdle()
            fixture?.close()
            Dispatchers.resetMain()
        }
    }

    private fun show() {
        val vm = fixture!!.viewModel
        compose.setContent { if (rendering) LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) { QuizScreen(vm, {}, {}) } } }
        compose.waitUntil(10_000) { runBlocking { fixture!!.db.aiExplanationDao().get(1) != null } }
        compose.onNodeWithTag("ai-explanation-delete").performScrollTo()
        compose.waitForIdle()
    }
    private fun capture(name: String) { compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/$name.png") }

    @Test fun feedback() { show(); capture("ai_explanation_feedback") }
    @Test fun stale() = runBlocking {
        val db = fixture!!.db
        db.questionDao().update(db.questionDao().getById(1)!!.copy(updatedAt = 9))
        show(); capture("ai_explanation_stale")
    }
    @Test fun loading() {
        show()
        compose.runOnIdle { fixture!!.viewModel.requestAiExplanation(1) {} }
        compose.waitUntil(10_000) { fixture!!.viewModel.aiExplanationRequests.value[1]?.loading == true }
        compose.onNodeWithTag("ai-explanation-delete").performScrollTo()
        capture("ai_explanation_loading")
    }
    @Test fun failure() {
        shouldFail = true; show()
        compose.runOnIdle { fixture!!.viewModel.requestAiExplanation(1) {} }
        compose.waitUntil(10_000) { fixture!!.viewModel.aiExplanationRequests.value[1]?.error != null }
        compose.onNodeWithTag("ai-explanation-delete").performScrollTo()
        capture("ai_explanation_failure")
    }
}
