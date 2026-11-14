package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
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
class FlashcardScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun requireRecordMode() {
        assumeTrue(
            "使用 -Proborazzi.test.record=true 生成闪卡截图",
            System.getProperty("roborazzi.test.record") == "true",
        )
    }

    @Test
    fun captureFlashcardStates() {
        val questions = screenshotQuestions()
        var index by mutableIntStateOf(0)
        var showAnswer by mutableStateOf(false)

        composeRule.setContent {
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FlashcardContent(
                        questions = questions,
                        index = index,
                        showAnswer = showAnswer,
                        onToggleAnswer = { showAnswer = !showAnswer },
                        onPrevious = {
                            if (index > 0) {
                                index--
                                showAnswer = false
                            }
                        },
                        onNext = {
                            if (index < questions.lastIndex) {
                                index++
                                showAnswer = false
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        capture("flashcard_long_option_front")
        composeRule.runOnIdle {
            index = 1
            showAnswer = true
        }
        capture("flashcard_multi_answer_back")
        composeRule.runOnIdle {
            index = 2
            showAnswer = true
        }
        capture("flashcard_judge_answer_back")
        composeRule.runOnIdle {
            index = 3
            showAnswer = true
        }
        capture("flashcard_fill_answer_back")
        composeRule.runOnIdle {
            index = 4
            showAnswer = true
        }
        capture("flashcard_no_explanation_back")
        composeRule.runOnIdle {
            index = 5
            showAnswer = true
        }
        capture("flashcard_bad_data_back")
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/$name.png",
        )
    }
}

private fun screenshotQuestions(): List<Question> = listOf(
    Question(
        id = 1,
        type = QuestionType.SINGLE,
        stem = "这是一道用于检查长题干换行的虚构示例题。题干会继续补充一些描述，以便观察卡片内容在手机宽度下自然换行并保持可滚动。",
        options = listOf(
            QuestionOption(
                "A",
                "这是一个很长的虚构选项内容，用来验证卡片会给选项足够的高度并让文字自然换行，而不是截断或省略。",
            ),
            QuestionOption(
                "B",
                "另一个同样较长的虚构选项，继续描述不会出现在真实题库中的占位内容，确保布局稳定。",
            ),
            QuestionOption("C", "较短的虚构选项"),
            QuestionOption("D", "最后一个虚构选项"),
        ),
        answer = "A",
        explanation = "这是虚构解析，用于观察背面布局。",
    ),
    Question(
        id = 2,
        type = QuestionType.MULTI,
        stem = "多选正确答案的展示示例。",
        options = listOf(
            QuestionOption("A", "虚构选项甲"),
            QuestionOption("B", "虚构选项乙"),
            QuestionOption("C", "虚构选项丙"),
            QuestionOption("D", "虚构选项丁"),
        ),
        answer = "A,C,D",
        explanation = "三个正确选项都应高亮并显示勾选图标。",
    ),
    Question(
        id = 3,
        type = QuestionType.JUDGE,
        stem = "这是一道虚构判断题。",
        options = emptyList(),
        answer = "√",
        explanation = "判断题答案应显示为可读文字。",
    ),
    Question(
        id = 4,
        type = QuestionType.FILL,
        stem = "这是一道虚构填空题。",
        options = emptyList(),
        answer = "示例填空答案",
        explanation = "填空题答案应保留原始文本。",
    ),
    Question(
        id = 5,
        type = QuestionType.SINGLE,
        stem = "这道虚构题没有解析。",
        options = listOf(
            QuestionOption("A", "虚构选项甲"),
            QuestionOption("B", "虚构选项乙"),
        ),
        answer = "B",
        explanation = " ",
    ),
    Question(
        id = 6,
        type = QuestionType.SINGLE,
        stem = "这道虚构题的答案无法对应。",
        options = listOf(
            QuestionOption("A", "虚构选项甲"),
            QuestionOption("B", "虚构选项乙"),
        ),
        answer = "A1",
        explanation = "",
    ),
)
