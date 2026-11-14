package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi",
    application = android.app.Application::class,
)
class TopicSetupScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun requireRecordMode() {
        assumeTrue(
            "使用 -Proborazzi.test.record=true 生成专项练习设置页截图",
            System.getProperty("roborazzi.test.record") == "true",
        )
    }

    @Test
    fun captureTopicSetupStates() {
        val banks = listOf(
            topicBank(1, "虚构题库一"),
            topicBank(2, "虚构题库二"),
            topicBank(3, "虚构题库三"),
        )
        val questions = screenshotTopicQuestions()
        var scenario by mutableIntStateOf(1)

        composeRule.setContent {
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TopicPracticeSetupContent(
                        banks = banks,
                        questions = questions,
                        initialBankId = scenario.toLong(),
                        onBack = {},
                        onStart = { _, _, _ -> },
                    )
                }
            }
        }

        capture("topic_setup_normal")
        composeRule.runOnIdle { scenario = 2 }
        capture("topic_setup_fourteen")
        composeRule.runOnIdle { scenario = 3 }
        capture("topic_setup_empty")
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/$name.png",
        )
    }
}

private fun screenshotTopicQuestions(): List<Question> =
    listOf(
        topicQuestions(bankId = 1, singleCount = 30, multiCount = 30),
        topicQuestions(bankId = 2, singleCount = 8, multiCount = 6),
        (1..12).map { index ->
            topicQuestion(
                id = 3_000L + index,
                bankId = 3,
                type = if (index % 2 == 0) QuestionType.SINGLE else QuestionType.MULTI,
                needsReview = true,
            )
        },
    ).flatten()
