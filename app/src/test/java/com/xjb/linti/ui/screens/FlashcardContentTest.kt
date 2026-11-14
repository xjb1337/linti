package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [34],
    qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi",
    application = android.app.Application::class,
)
class FlashcardContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `front shows every option without answer styling`() {
        setFlashcardContent(listOf(singleQuestion(answer = "B")))

        listOf("A. 甲选项", "B. 乙选项", "C. 丙选项", "D. 丁选项").forEach { option ->
            composeRule.onNodeWithText(option, useUnmergedTree = true).assertExists()
        }
        listOf("A", "B", "C", "D").forEach { label ->
            composeRule
                .onNodeWithTag("flashcard-option-$label", useUnmergedTree = true)
                .assertIsNotSelected()
            composeRule
                .onNodeWithTag("flashcard-highlight-$label", useUnmergedTree = true)
                .assertDoesNotExist()
        }
        composeRule.onNodeWithTag("flashcard-explanation", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("看答案").assertExists()
    }

    @Test
    fun `flip button reveals a single correct option and explanation`() {
        setFlashcardContent(listOf(singleQuestion(answer = "B")))

        composeRule.onNodeWithTag("flashcard-flip-button").performClick()

        composeRule.onNodeWithTag("flashcard-option-B", useUnmergedTree = true).assertIsSelected()
        listOf("A", "C", "D").forEach { label ->
            composeRule
                .onNodeWithTag("flashcard-option-$label", useUnmergedTree = true)
                .assertIsNotSelected()
        }
        composeRule.onNodeWithTag("flashcard-highlight-B", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("flashcard-explanation", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("看题面").assertExists()
    }

    @Test
    fun `card tap flips multi answer and highlights every correct option`() {
        setFlashcardContent(listOf(multiQuestion(answer = "ACD")))

        composeRule.onNodeWithTag("flashcard-card").performClick()

        listOf("A", "C", "D").forEach { label ->
            composeRule
                .onNodeWithTag("flashcard-option-$label", useUnmergedTree = true)
                .assertIsSelected()
            composeRule
                .onNodeWithTag("flashcard-highlight-$label", useUnmergedTree = true)
                .assertExists()
        }
        composeRule.onNodeWithTag("flashcard-option-B", useUnmergedTree = true).assertIsNotSelected()
        composeRule.onNodeWithTag("flashcard-display-answer", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag("flashcard-flip-button").performClick()

        composeRule.onNodeWithTag("flashcard-option-A", useUnmergedTree = true).assertIsNotSelected()
        composeRule.onNodeWithTag("flashcard-highlight-A", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("看答案").assertExists()
    }

    @Test
    fun `judge and fill answers are shown as readable text`() {
        setFlashcardContent(
            listOf(
                judgeQuestion(answer = "√"),
                fillQuestion(answer = "示例答案"),
            ),
        )

        composeRule.onNodeWithTag("flashcard-flip-button").performClick()
        composeRule.onNodeWithTag("flashcard-display-answer", useUnmergedTree = true)
            .assertTextContains("答案：正确")

        composeRule.onNodeWithTag("flashcard-next").performClick()
        composeRule.onNodeWithTag("flashcard-flip-button").performClick()
        composeRule.onNodeWithTag("flashcard-display-answer", useUnmergedTree = true)
            .assertTextContains("答案：示例答案")
    }

    @Test
    fun `missing explanation does not render an explanation node`() {
        setFlashcardContent(listOf(singleQuestion(answer = "A", explanation = " ")))

        composeRule.onNodeWithTag("flashcard-flip-button").performClick()

        composeRule.onNodeWithTag("flashcard-explanation", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `unresolvable answer shows raw value without highlighting options`() {
        setFlashcardContent(listOf(singleQuestion(answer = "Z")))

        composeRule.onNodeWithTag("flashcard-flip-button").performClick()

        composeRule.onNodeWithTag("flashcard-unresolvable", useUnmergedTree = true)
            .assertTextContains("答案无法对应：Z")
        listOf("A", "B", "C", "D").forEach { label ->
            composeRule
                .onNodeWithTag("flashcard-option-$label", useUnmergedTree = true)
                .assertIsNotSelected()
            composeRule
                .onNodeWithTag("flashcard-highlight-$label", useUnmergedTree = true)
                .assertDoesNotExist()
        }
    }

    @Test
    fun `blank unresolvable answer omits the raw value separator`() {
        setFlashcardContent(listOf(singleQuestion(answer = "  ")))

        composeRule.onNodeWithTag("flashcard-flip-button").performClick()

        composeRule.onNodeWithTag("flashcard-unresolvable", useUnmergedTree = true)
            .assertTextContains("答案无法对应")
    }

    @Test
    fun `previous and next return to the front and are disabled at the ends`() {
        setFlashcardContent(
            questions = listOf(
                singleQuestion(id = 1, stem = "第一道虚构题", answer = "A"),
                singleQuestion(id = 2, stem = "第二道虚构题", answer = "B"),
                singleQuestion(id = 3, stem = "第三道虚构题", answer = "C"),
            ),
            initialIndex = 1,
        )

        composeRule.onNodeWithTag("flashcard-previous").assertIsEnabled()
        composeRule.onNodeWithTag("flashcard-next").assertIsEnabled()
        composeRule.onNodeWithTag("flashcard-flip-button").performClick()
        composeRule.onNodeWithTag("flashcard-option-B", useUnmergedTree = true).assertIsSelected()

        composeRule.onNodeWithTag("flashcard-previous").performClick()
        composeRule.onNodeWithText("第一道虚构题", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("flashcard-option-A", useUnmergedTree = true).assertIsNotSelected()
        composeRule.onNodeWithText("看答案").assertExists()

        composeRule.onNodeWithTag("flashcard-flip-button").performClick()
        composeRule.onNodeWithTag("flashcard-next").performClick()
        composeRule.onNodeWithText("第二道虚构题", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("flashcard-option-B", useUnmergedTree = true).assertIsNotSelected()
        composeRule.onNodeWithText("看答案").assertExists()
    }

    @Test
    fun `navigation buttons disable at the first and last question`() {
        setFlashcardContent(
            questions = listOf(
                singleQuestion(id = 1, stem = "第一道虚构题", answer = "A"),
                singleQuestion(id = 2, stem = "第二道虚构题", answer = "B"),
            ),
        )

        composeRule.onNodeWithTag("flashcard-previous").assertIsNotEnabled()
        composeRule.onNodeWithTag("flashcard-next").assertIsEnabled()
        composeRule.onNodeWithTag("flashcard-next").assertTextContains("下一题")

        composeRule.onNodeWithTag("flashcard-next").performClick()
        composeRule.onNodeWithTag("flashcard-previous").assertIsEnabled()
        composeRule.onNodeWithTag("flashcard-next").assertIsNotEnabled()
        composeRule.onNodeWithTag("flashcard-previous").assertTextContains("上一题")
    }

    private fun setFlashcardContent(
        questions: List<Question>,
        initialIndex: Int = 0,
    ) {
        composeRule.setContent {
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TestFlashcardContent(
                        questions = questions,
                        initialIndex = initialIndex,
                    )
                }
            }
        }
    }
}

@Composable
private fun TestFlashcardContent(
    questions: List<Question>,
    initialIndex: Int,
) {
    var index by remember { mutableIntStateOf(initialIndex) }
    var showAnswer by remember { mutableStateOf(false) }

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

private fun singleQuestion(
    id: Long = 1,
    stem: String = "以下哪项是虚构示例？",
    answer: String,
    explanation: String = "这是虚构解析。",
): Question = baseQuestion(
    id = id,
    type = QuestionType.SINGLE,
    stem = stem,
    answer = answer,
    explanation = explanation,
)

private fun multiQuestion(answer: String): Question = baseQuestion(
    type = QuestionType.MULTI,
    stem = "请选择所有虚构示例。",
    answer = answer,
)

private fun judgeQuestion(answer: String): Question = baseQuestion(
    type = QuestionType.JUDGE,
    stem = "这是一道虚构判断题。",
    answer = answer,
    options = emptyList(),
)

private fun fillQuestion(answer: String): Question = baseQuestion(
    type = QuestionType.FILL,
    stem = "请填写虚构答案。",
    answer = answer,
    options = emptyList(),
)

private fun baseQuestion(
    id: Long = 1,
    type: QuestionType,
    stem: String,
    answer: String,
    explanation: String = "这是虚构解析。",
    options: List<QuestionOption> = listOf(
        QuestionOption("A", "甲选项"),
        QuestionOption("B", "乙选项"),
        QuestionOption("C", "丙选项"),
        QuestionOption("D", "丁选项"),
    ),
): Question = Question(
    id = id,
    type = type,
    stem = stem,
    options = options,
    answer = answer,
    explanation = explanation,
)
