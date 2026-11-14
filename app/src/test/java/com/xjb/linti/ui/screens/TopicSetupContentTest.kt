package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assert.assertEquals
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
class TopicSetupContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `zero eligible questions disables start and has no all option`() {
        setContent(
            banks = listOf(topicBank(1, "虚构题库")),
            questions = listOf(
                topicQuestion(1, 1, QuestionType.SINGLE, needsReview = true),
            ),
            initialBankId = 1,
        )

        composeRule.onNodeWithTag("topic-total").assertTextContains("可练 0 题")
        composeRule.onNodeWithTag("topic-start").assertIsNotEnabled()
        composeRule.onNodeWithText("全部 0 题").assertDoesNotExist()
        composeRule.onNodeWithTag("topic-count-10").assertIsNotEnabled()
        composeRule.onNodeWithTag("topic-count-20").assertIsNotEnabled()
        composeRule.onNodeWithTag("topic-count-50").assertIsNotEnabled()
    }

    @Test
    fun `fourteen eligible questions enable only ten and all fourteen tiers`() {
        setContent(
            banks = listOf(topicBank(7, "虚构题库")),
            questions = topicQuestions(bankId = 7, singleCount = 8, multiCount = 6),
            initialBankId = 7,
        )

        composeRule.onNodeWithTag("topic-total").assertTextContains("可练 14 题")
        composeRule.onNodeWithTag("topic-count-10").assertIsEnabled()
        composeRule.onNodeWithTag("topic-count-20").assertIsNotEnabled()
        composeRule.onNodeWithTag("topic-count-50").assertIsNotEnabled()
        composeRule.onNodeWithTag("topic-count-14")
            .assertIsEnabled()
            .assertIsSelected()
            .assertTextContains("全部 14 题")
        composeRule.onNodeWithTag("topic-start").assertIsEnabled()
    }

    @Test
    fun `switching type updates count and start receives current filters`() {
        var capturedBanks: Set<Long>? = null
        var capturedTypes: Set<QuestionType>? = null
        var capturedCount: Int? = null
        setContent(
            banks = listOf(topicBank(7, "虚构题库")),
            questions = topicQuestions(bankId = 7, singleCount = 8, multiCount = 6),
            initialBankId = 7,
            onStart = { banks, types, count ->
                capturedBanks = banks
                capturedTypes = types
                capturedCount = count
            },
        )

        composeRule.onNodeWithTag("topic-type-SINGLE").performClick()

        composeRule.onNodeWithTag("topic-total").assertTextContains("可练 6 题")
        composeRule.onNodeWithText("全部 6 题").assertExists()
        composeRule.onNodeWithTag("topic-count-14").assertDoesNotExist()
        composeRule.onNodeWithTag("topic-type-SINGLE").assertIsNotSelected()
        composeRule.onNodeWithTag("topic-type-MULTI").assertIsSelected()

        composeRule.onNodeWithTag("topic-start").performClick()
        composeRule.runOnIdle {
            assertEquals(setOf(7L), capturedBanks)
            assertEquals(setOf(QuestionType.MULTI), capturedTypes)
            assertEquals(6, capturedCount)
        }
    }

    @Test
    fun `practice entry defaults to every bank with questions`() {
        setContent(
            banks = listOf(
                topicBank(1, "虚构题库甲"),
                topicBank(2, "虚构题库乙"),
                topicBank(3, "空题库"),
            ),
            questions = listOf(
                topicQuestion(1, 1, QuestionType.SINGLE),
                topicQuestion(2, 2, QuestionType.MULTI),
            ),
            initialBankId = null,
        )

        composeRule.onNodeWithTag("topic-bank-1").assertIsSelected()
        composeRule.onNodeWithTag("topic-bank-2").assertIsSelected()
        composeRule.onNodeWithTag("topic-bank-3").assertIsNotSelected().assertIsNotEnabled()
        composeRule.onNodeWithTag("topic-total").assertTextContains("可练 2 题")
    }

    private fun setContent(
        banks: List<QuestionBank>,
        questions: List<Question>,
        initialBankId: Long?,
        onStart: (Set<Long>, Set<QuestionType>, Int) -> Unit = { _, _, _ -> },
    ) {
        composeRule.setContent {
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TopicPracticeSetupContent(
                        banks = banks,
                        questions = questions,
                        initialBankId = initialBankId,
                        onBack = {},
                        onStart = onStart,
                    )
                }
            }
        }
    }
}

internal fun topicBank(id: Long, name: String): QuestionBank = QuestionBank(
    id = id,
    name = name,
    createdAt = 1,
    updatedAt = 1,
)

internal fun topicQuestions(
    bankId: Long,
    singleCount: Int,
    multiCount: Int,
): List<Question> {
    val singles = (0 until singleCount).map { index ->
        topicQuestion(
            id = bankId * 1_000 + index + 1,
            bankId = bankId,
            type = QuestionType.SINGLE,
        )
    }
    val multis = (0 until multiCount).map { index ->
        topicQuestion(
            id = bankId * 1_000 + singleCount + index + 1,
            bankId = bankId,
            type = QuestionType.MULTI,
        )
    }
    return singles + multis
}

internal fun topicQuestion(
    id: Long,
    bankId: Long,
    type: QuestionType,
    needsReview: Boolean = false,
): Question = Question(
    id = id,
    bankId = bankId,
    sourceNumber = id.toString(),
    type = type,
    stem = "虚构专项题 $id",
    options = when (type) {
        QuestionType.SINGLE, QuestionType.MULTI -> listOf(
            QuestionOption("A", "虚构选项甲"),
            QuestionOption("B", "虚构选项乙"),
            QuestionOption("C", "虚构选项丙"),
            QuestionOption("D", "虚构选项丁"),
        )
        QuestionType.JUDGE, QuestionType.FILL -> emptyList()
    },
    answer = when (type) {
        QuestionType.SINGLE -> "A"
        QuestionType.MULTI -> "AB"
        QuestionType.JUDGE -> "正确"
        QuestionType.FILL -> "虚构答案"
    },
    needsReview = needsReview,
)
