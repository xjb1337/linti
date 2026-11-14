package com.xjb.linti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.xjb.linti.domain.FlashcardAnswer
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.resolveFlashcardAnswer
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.theme.LocalLintiPalette
import java.text.Normalizer
import java.util.Locale

@Composable
fun FlashcardContent(
    questions: List<Question>,
    index: Int,
    showAnswer: Boolean,
    onToggleAnswer: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    showNavigation: Boolean = true,
    rememberedDays: Int = 1,
    onReview: ((Boolean) -> Unit)? = null,
    reviewing: Boolean = false,
    answerExtra: @Composable (Question) -> Unit = {},
) {
    val question = questions.getOrNull(index) ?: return
    val answer = remember(question) { resolveFlashcardAnswer(question) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LintiCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("flashcard-card"),
            containerColor = LocalLintiPalette.current.surface,
            borderColor = LocalLintiPalette.current.primarySoft,
            onClick = onToggleAnswer,
        ) {
            FlashcardCardBody(
                question = question,
                answer = answer,
                showAnswer = showAnswer,
                answerExtra = answerExtra,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showNavigation) OutlinedActionButton(
                text = "上一题",
                modifier = Modifier
                    .weight(1f)
                    .testTag("flashcard-previous"),
                enabled = index > 0,
                onClick = onPrevious,
            )
            PrimaryActionButton(
                text = if (showAnswer) "看题面" else "看答案",
                modifier = Modifier
                    .weight(1f)
                    .testTag("flashcard-flip-button"),
                onClick = onToggleAnswer,
            )
            if (showNavigation) OutlinedActionButton(
                text = "下一题",
                modifier = Modifier
                    .weight(1f)
                    .testTag("flashcard-next"),
                enabled = index < questions.lastIndex,
                onClick = onNext,
            )
        }
        if (showAnswer && onReview != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedActionButton(
                    text = "忘记 · 10 分钟后", onClick = { onReview(false) }, enabled = !reviewing,
                    modifier = Modifier.weight(1f).testTag("flashcard-forgot"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                )
                PrimaryActionButton(
                    text = "记得 · $rememberedDays 天后", onClick = { onReview(true) }, enabled = !reviewing,
                    modifier = Modifier.weight(1f).testTag("flashcard-remembered"),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FlashcardCardBody(
    question: Question,
    answer: FlashcardAnswer,
    showAnswer: Boolean,
    answerExtra: @Composable (Question) -> Unit,
) {
    val palette = LocalLintiPalette.current
    val scrollState = rememberScrollState()
    val correctLabels = (answer as? FlashcardAnswer.Resolved)?.highlightedLabels.orEmpty()

    LaunchedEffect(question.id, showAnswer) {
        scrollState.scrollTo(0)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        StatusPill(
            text = question.type.label,
            color = palette.primary,
            background = palette.primaryPale,
        )
        if (question.origin == com.xjb.linti.domain.QuestionOrigin.AI_GENERATED) {
            StatusPill("AI 出题", color = palette.aiAccent, background = palette.aiSoft)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = question.stem,
            style = MaterialTheme.typography.headlineMedium,
            color = palette.textPrimary,
        )
        if (question.options.isNotEmpty() && question.type != com.xjb.linti.domain.QuestionType.ESSAY) {
            Spacer(Modifier.height(18.dp))
            question.options.forEach { option ->
                val highlighted = showAnswer && normalizeOptionLabel(option.label) in correctLabels
                FlashcardOptionRow(
                    option = option,
                    highlighted = highlighted,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (showAnswer) {
            when (answer) {
                is FlashcardAnswer.Resolved -> {
                    val displayAnswer = answer.displayAnswer
                    if (displayAnswer != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${if (question.type == com.xjb.linti.domain.QuestionType.ESSAY) "参考答案" else "答案"}：$displayAnswer",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.success,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.successSoft)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("flashcard-display-answer"),
                        )
                    }
                }
                is FlashcardAnswer.Unresolvable -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (answer.raw.isBlank()) {
                            "答案无法对应"
                        } else {
                            "答案无法对应：${answer.raw}"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.warning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.warningSoft)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("flashcard-unresolvable"),
                    )
                }
            }
            val explanation = answer.explanation
            if (explanation != null) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.background)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("flashcard-explanation"),
                ) {
                    Text(
                        text = "解析",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            answerExtra(question)
        }
    }
}

private fun normalizeOptionLabel(label: String): String =
    Normalizer.normalize(label, Normalizer.Form.NFKC)
        .trim()
        .uppercase(Locale.ROOT)

@Composable
private fun FlashcardOptionRow(
    option: QuestionOption,
    highlighted: Boolean,
) {
    val palette = LocalLintiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("flashcard-option-${option.label}")
            .semantics {
                selected = highlighted
                stateDescription = if (highlighted) "正确选项" else "选项"
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) palette.successSoft else palette.surface)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = "${option.label}. ${option.text}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (highlighted) palette.success else palette.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (highlighted) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "正确选项",
                tint = palette.success,
                modifier = Modifier
                    .size(20.dp)
                    .testTag("flashcard-highlight-${option.label}"),
            )
        }
    }
}
