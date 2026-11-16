package com.xjb.linti.ui.screens

import com.xjb.linti.ui.components.LintiConfirmationSheet
import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SimpleProgressBar
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider

@Composable
fun AnswerSheetScreen(
    viewModel: LintiViewModel,
    onBack: () -> Unit,
) {
    val quiz by viewModel.quizState.collectAsStateWithLifecycle()
    val submittingToken by viewModel.submittingExamToken.collectAsStateWithLifecycle()
    var showSubmitConfirm by remember { mutableStateOf(false) }
    val isSubmitting = submittingToken == quiz.sessionToken
    val revealCorrectness = quiz.mode != PracticeMode.EXAM || quiz.config.showAnswerAfterAnswer
    val revealedIds = quiz.committedIds.filter { quiz.revealed(it) }.toSet()
    val grade = if (revealedIds.isEmpty()) null else viewModel.gradeCurrent()
    val correct = revealedIds.count {
        it !in quiz.answerViewedIds && grade?.outcomes?.get(it)?.outcome == QuestionOutcome.CORRECT
    }
    val wrong = revealedIds.count {
        grade?.outcomes?.get(it)?.outcome == QuestionOutcome.WRONG
    }
    val unanswered = (quiz.questions.size - revealedIds.size).coerceAtLeast(0)

    if (!quiz.active || quiz.questions.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(modifier = Modifier.padding(horizontal = 16.dp), title = "答题卡", onBack = onBack)
            EmptyState(
                title = "没有进行中的答题",
                modifier = Modifier.padding(20.dp),
                action = {
                    OutlinedActionButton(text = "返回", onClick = onBack)
                },
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LintiPageHeader(
            modifier = Modifier.padding(horizontal = 16.dp),
            title = "答题卡",
            subtitle = "${quiz.bankName} · ${quiz.mode.label}",
            onBack = onBack,
            actions = {
                StatusPill(
                    text = "${quiz.questions.size} 题",
                    color = LocalLintiPalette.current.primary,
                    background = LocalLintiPalette.current.primaryPale,
                )
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
        ) {
            LintiCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${quiz.answeredCount}",
                            style = MaterialTheme.typography.headlineLarge,
                            color = LocalLintiPalette.current.primary,
                        )
                        Text(
                            text = " / ${quiz.questions.size} 已作答",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalLintiPalette.current.textSecondary,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        if (revealCorrectness) {
                            Text(
                                text = "正确 $correct · 错误 $wrong · 未做 $unanswered",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLintiPalette.current.textSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SimpleProgressBar(
                        progress = quiz.answeredCount.toFloat() / quiz.questions.size,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LegendDot(if (revealCorrectness) "正确" else "已答", LocalLintiPalette.current.success)
                        if (revealCorrectness) {
                            LegendDot("错误", LocalLintiPalette.current.error)
                        }
                        LegendDot("未做", LocalLintiPalette.current.textSecondary)
                        LegendDot("当前", LocalLintiPalette.current.primary)
                        LegendDot("已标记", LocalLintiPalette.current.warning)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(48.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(quiz.questions, key = { _, q -> q.questionId }) { index, item ->
                    val revealed = item.questionId in revealedIds
                    val outcome = if (revealed) {
                        grade?.outcomes?.get(item.questionId)?.outcome?.takeUnless {
                            it == QuestionOutcome.CORRECT && item.questionId in quiz.answerViewedIds
                        }
                    } else {
                        null
                    }
                    val rawAnswer = quiz.answers[item.questionId].orEmpty()
                    val answered = rawAnswer.isNotBlank() && (item.type != QuestionType.ESSAY || com.xjb.linti.domain.EssayAnswer.isAssessed(rawAnswer))
                    val current = index == quiz.currentIndex
                    val flagged = item.questionId in quiz.flaggedIds
                    AnswerGridCell(
                        number = index + 1,
                        current = current,
                        flagged = flagged,
                        outcome = outcome,
                        answered = answered,
                        onClick = {
                            viewModel.goToQuestion(index)
                            onBack()
                        },
                    )
                }
            }
        }
        Surface(color = LocalLintiPalette.current.surface, shadowElevation = 8.dp) {
            Column {
                ThinDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedActionButton(
                        text = "继续作答",
                        icon = Icons.Outlined.ArrowBack,
                        modifier = Modifier.weight(1f),
                        onClick = onBack,
                    )
                    if (quiz.mode == PracticeMode.EXAM) {
                        PrimaryActionButton(
                            text = "交卷",
                            modifier = Modifier.weight(1f),
                            enabled = !isSubmitting,
                            onClick = { showSubmitConfirm = true },
                        )
                    }
                }
            }
        }
    }

    if (showSubmitConfirm && quiz.mode == PracticeMode.EXAM) {
        LintiConfirmationSheet("交卷并查看成绩？", "未答 ${quiz.questions.size - quiz.answeredCount} 题。交卷后不可修改。",
            if (isSubmitting) "正在判分…" else "确认交卷", confirmEnabled = !isSubmitting, dismissLabel = "继续检查",
            onConfirm = { showSubmitConfirm = false; viewModel.submitQuiz(quiz.sessionToken) },
            onDismiss = { showSubmitConfirm = false })
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalLintiPalette.current.textSecondary,
        )
    }
}

@Composable
internal fun AnswerGridCell(
    number: Int,
    current: Boolean,
    flagged: Boolean,
    outcome: QuestionOutcome?,
    answered: Boolean,
    onClick: () -> Unit,
) {
    val background = when (outcome) {
        QuestionOutcome.CORRECT -> LocalLintiPalette.current.successSoft
        QuestionOutcome.WRONG -> LocalLintiPalette.current.errorSoft
        else -> if (answered) LocalLintiPalette.current.primaryPale else LocalLintiPalette.current.background
    }
    val color = when (outcome) {
        QuestionOutcome.CORRECT -> LocalLintiPalette.current.success
        QuestionOutcome.WRONG -> LocalLintiPalette.current.error
        else -> if (answered) LocalLintiPalette.current.primary else LocalLintiPalette.current.textSecondary
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .border(
                width = if (current) 2.dp else 1.dp,
                color = if (current) LocalLintiPalette.current.primary else LocalLintiPalette.current.line,
                shape = RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("第"); append(number); append("题")
                    append(
                        when (outcome) {
                            QuestionOutcome.CORRECT -> "，答对"
                            QuestionOutcome.WRONG -> "，答错"
                            else -> if (answered) "，已答" else "，未做"
                        },
                    )
                    if (current) append("，当前题")
                    if (flagged) append("，已标记")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
        )
        if (flagged) {
            Icon(
                Icons.Outlined.Flag,
                contentDescription = "已标记",
                tint = LocalLintiPalette.current.warning,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(10.dp),
            )
        }
    }
}
