package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.ui.theme.LintiTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
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
class QuizToggleScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private var fixture: QuizToggleTestFixture? = null

    @Before fun setup() = runBlocking {
        assumeTrue("截图只在 -Proborazzi.test.record=true 时录制", System.getProperty("roborazzi.test.record") == "true")
        fixture = QuizToggleTestFixture().also { it.setup() }
    }

    @After fun close() { fixture?.close() }

    private fun showQuiz() {
        val viewModel = requireNotNull(fixture).viewModel
        compose.setContent {
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(Modifier.fillMaxSize()) { QuizScreen(viewModel, onBack = {}, onAnswerSheet = {}) }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/$name.png")
    }

    @Test fun answeringWithToggle() = runBlocking {
        requireNotNull(fixture).startPractice()
        fixture!!.viewModel.selectAnswer(1, "B")
        showQuiz()
        capture("quiz_toggle_answering")
    }

    @Test fun recitingShowsAnswerAndRetainedChoice() = runBlocking {
        requireNotNull(fixture).startPractice()
        fixture!!.viewModel.selectAnswer(1, "B")
        fixture!!.viewModel.setReciting(true)
        showQuiz()
        capture("quiz_toggle_reciting")
    }

    @Test fun timedExamHasNoToggle() = runBlocking {
        requireNotNull(fixture).startExam()
        showQuiz()
        capture("quiz_toggle_exam")
    }
}
