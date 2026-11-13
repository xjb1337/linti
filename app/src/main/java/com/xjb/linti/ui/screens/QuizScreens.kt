package com.xjb.linti.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.MultiSelectRule
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizQuestion
import com.xjb.linti.domain.TypeStats
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.MetricItem
import com.xjb.linti.ui.components.MetricStrip
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.SimpleProgressBar
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.components.WarningNote
import com.xjb.linti.ui.formatDuration
import com.xjb.linti.ui.formatPercent
import com.xjb.linti.ui.formatScore
import com.xjb.linti.ui.theme.AiAccent
import com.xjb.linti.ui.theme.AiSoft
import com.xjb.linti.ui.theme.Background
import com.xjb.linti.ui.theme.Error
import com.xjb.linti.ui.theme.ErrorSoft
import com.xjb.linti.ui.theme.Line
import com.xjb.linti.ui.theme.OnPrimary
import com.xjb.linti.ui.theme.Primary
import com.xjb.linti.ui.theme.PrimaryPale
import com.xjb.linti.ui.theme.PrimarySoft
import com.xjb.linti.ui.theme.Success
import com.xjb.linti.ui.theme.SuccessSoft
import com.xjb.linti.ui.theme.Surface
import com.xjb.linti.ui.theme.TextPrimary
import com.xjb.linti.ui.theme.TextSecondary
import com.xjb.linti.ui.theme.Warning
import com.xjb.linti.ui.theme.WarningSoft
import kotlinx.coroutines.launch

@Composable
fun QuizScreen(
    viewModel: LintiViewModel,
    onBack: () -> Unit,
    onAnswerSheet: () -> Unit,
    onFinished: () -> Unit,
) {
    val quiz by viewModel.quizState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var showBackConfirm by remember { mutableStateOf(false) }
    var noteDialogFor by remember { mutableStateOf<Long?>(null) }
    var noteText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val question = quiz.currentQuestion

    LaunchedEffect(noteDialogFor) {
        val questionId = noteDialogFor
        noteText = if (questionId == null) "" else viewModel.loadNote(questionId)
    }

    if (!quiz.active || question == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(title = "答题", onBack = onBack)
            LintiCard(
                modifier = Modifier.padding(20.dp),
                containerColor = PrimaryPale,
            ) {
                EmptyState(
                    title = "当前没有进行中的答题",
                    message = "请从题库或练习中枢重新开始。",
                    action = {
                        OutlinedActionButton(text = "返回", onClick = onBack)
                    },
                )
            }
        }
        return
    }

    val currentAnswer = quiz.answers[question.questionId].orEmpty()
    val hasAnswer = currentAnswer.isNotBlank()
    val revealAnswer = quiz.currentVerified ||
        (hasAnswer &&
            question.type != QuestionType.FILL &&
            (quiz.mode != PracticeMode.EXAM || quiz.config.showAnswerAfterAnswer))
    val outcome = if (revealAnswer) {
        viewModel.outcomeFor(question.questionId)
    } else {
        QuestionOutcome.UNANSWERED
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LintiPageHeader(
            title = "第 ${quiz.currentIndex + 1} 题",
            subtitle = "${quiz.bankName} · ${quiz.mode.label}",
            onBack = {
                if (quiz.answeredCount > 0) showBackConfirm = true else onBack()
            },
            actions = {
                IconButton(onClick = viewModel::toggleCurrentBookmark) {
                    Icon(
                        imageVector = if (quiz.currentBookmarked) {
                            Icons.Outlined.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = if (quiz.currentBookmarked) "取消收藏" else "收藏",
                        tint = if (quiz.currentBookmarked) Primary else TextSecondary,
                    )
                }
                IconButton(onClick = viewModel::toggleCurrentFlag) {
                    Icon(
                        Icons.Outlined.Flag,
                        contentDescription = if (quiz.currentFlagged) "取消标记" else "标记本题",
                        tint = if (quiz.currentFlagged) Warning else TextSecondary,
                    )
                }
                IconButton(onClick = { noteDialogFor = question.questionId }) {
                    Icon(
                        Icons.Outlined.EditNote,
                        contentDescription = "笔记",
                        tint = AiAccent,
                    )
                }
            },
        )
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${quiz.currentIndex + 1} / ${quiz.questions.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "已答 ${quiz.answeredCount} / ${quiz.questions.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(6.dp))
            SimpleProgressBar(
                progress = (quiz.currentIndex + 1f) / quiz.questions.size,
            )
            Spacer(Modifier.height(9.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StatusPill(question.type.label, color = Primary, background = PrimaryPale)
                if (quiz.mode == PracticeMode.EXAM) {
                    StatusPill(
                        text = "${quiz.config.durationMinutes} 分钟 · ${formatScore(question.points)} 分",
                        color = Warning,
                        background = WarningSoft,
                    )
                } else {
                    StatusPill(
                        text = "练习即时判分",
                        color = Success,
                        background = SuccessSoft,
                    )
                }
                if (quiz.currentFlagged) {
                    StatusPill("已标记", color = Warning, background = WarningSoft)
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 12.dp,
                bottom = 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = question.stem,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                )
            }
            when (question.type) {
                QuestionType.SINGLE, QuestionType.MULTI -> {
                    items(question.options, key = { "opt-${question.questionId}-${it.label}" }) { option ->
                        val selectedLetters = AnswerNormalizer.lettersIn(currentAnswer)
                        val isSelected = option.label.uppercase() in selectedLetters
                        val correctLetters = AnswerNormalizer.lettersIn(question.correctAnswer)
                        val isCorrectOption = option.label.uppercase() in correctLetters
                        QuizOptionRow(
                            option = option,
                            selected = isSelected,
                            reveal = revealAnswer,
                            correct = isCorrectOption,
                            onClick = {
                                if (quiz.mode == PracticeMode.EXAM && revealAnswer) return@QuizOptionRow
                                viewModel.selectAnswer(question.questionId, option.label)
                            },
                        )
                    }
                    if (question.type == QuestionType.MULTI) {
                        item {
                            Text(
                                text = if (quiz.mode == PracticeMode.EXAM) {
                                    "多选：可点选多个选项，交卷后按设置计分。"
                                } else {
                                    "多选：可点选多个选项；" + quiz.config.multiSelectRule.label
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
                QuestionType.JUDGE -> {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            JudgeOption(
                                text = "正确",
                                selected = currentAnswer == "true",
                                reveal = revealAnswer,
                                correct = AnswerNormalizer.judgeMatches("true", question.correctAnswer),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (quiz.mode != PracticeMode.EXAM || !revealAnswer) {
                                        viewModel.selectAnswer(question.questionId, "true")
                                    }
                                },
                            )
                            JudgeOption(
                                text = "错误",
                                selected = currentAnswer == "false",
                                reveal = revealAnswer,
                                correct = AnswerNormalizer.judgeMatches("false", question.correctAnswer),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (quiz.mode != PracticeMode.EXAM || !revealAnswer) {
                                        viewModel.selectAnswer(question.questionId, "false")
                                    }
                                },
                            )
                        }
                    }
                }
                QuestionType.FILL -> {
                    item {
                        OutlinedTextField(
                            value = currentAnswer,
                            onValueChange = { viewModel.setFillAnswer(question.questionId, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("填写答案") },
                            minLines = 1,
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            shape = RoundedCornerShape(13.dp),
                            enabled = !revealAnswer ||
                                quiz.mode != PracticeMode.EXAM ||
                                !quiz.config.showAnswerAfterAnswer,
                        )
                        if (!revealAnswer && hasAnswer &&
                            (quiz.mode != PracticeMode.EXAM || quiz.config.showAnswerAfterAnswer)
                        ) {
                            Spacer(Modifier.height(7.dp))
                            OutlinedActionButton(
                                text = "核对填空答案",
                                onClick = viewModel::revealCurrentAnswer,
                            )
                        }
                    }
                }
            }
            if (revealAnswer) {
                item {
                    AnswerFeedback(
                        question = question,
                        outcome = outcome,
                        userAnswer = currentAnswer,
                    )
                }
            } else if (quiz.mode == PracticeMode.EXAM && !quiz.config.showAnswerAfterAnswer) {
                item {
                    LintiCard(containerColor = Background, borderColor = Line) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Schedule,
                                contentDescription = null,
                                tint = Warning,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "本场考试设置为交卷后显示答案，作答过程中不提前揭示正误。",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
            if (quiz.currentFlagged) {
                item {
                    WarningNote("本题已标记，可在答题卡中快速定位。")
                }
            }
        }

        Surface(color = Surface, shadowElevation = 8.dp) {
            Column {
                ThinDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedActionButton(
                        text = "上一题",
                        icon = Icons.Outlined.ChevronLeft,
                        modifier = Modifier.weight(1f),
                        enabled = quiz.currentIndex > 0,
                        onClick = viewModel::previousQuestion,
                    )
                    OutlinedActionButton(
                        text = "答题卡",
                        icon = Icons.Outlined.GridView,
                        modifier = Modifier.weight(1f),
                        onClick = onAnswerSheet,
                    )
                    OutlinedActionButton(
                        text = "下一题",
                        icon = Icons.Outlined.ChevronRight,
                        modifier = Modifier.weight(1f),
                        enabled = quiz.currentIndex < quiz.questions.lastIndex,
                        onClick = viewModel::nextQuestion,
                    )
                    PrimaryActionButton(
                        text = "交卷",
                        modifier = Modifier.weight(1f),
                        onClick = { showSubmitConfirm = true },
                    )
                }
            }
        }
    }

    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("确认交卷？") },
            text = {
                Text(
                    "已答 ${quiz.answeredCount} / ${quiz.questions.size} 题，" +
                        "未答 ${quiz.questions.size - quiz.answeredCount} 题。交卷后错题会自动进入错题本。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            showSubmitConfirm = false
                            if (viewModel.submitQuiz()) {
                                onFinished()
                            }
                            isSubmitting = false
                        }
                    },
                ) {
                    Text(if (isSubmitting) "正在交卷…" else "确认交卷")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) {
                    Text("继续作答")
                }
            },
        )
    }

    if (showBackConfirm) {
        AlertDialog(
            onDismissRequest = { showBackConfirm = false },
            title = { Text("暂时离开答题？") },
            text = { Text("顺序练习会保存当前位置；本次未交卷的答题不会出现在成绩报告中。") },
            confirmButton = {
                TextButton(onClick = onBack) {
                    Text("离开")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackConfirm = false }) {
                    Text("继续答题")
                }
            },
        )
    }

    val noteQuestionId = noteDialogFor
    if (noteQuestionId != null) {
        AlertDialog(
            onDismissRequest = { noteDialogFor = null },
            title = { Text("本题笔记") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("记录易错点、记忆线索或待核对内容") },
                    minLines = 4,
                    maxLines = 8,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(12.dp),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            viewModel.saveNote(noteQuestionId, noteText)
                            noteDialogFor = null
                        }
                    },
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteDialogFor = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun QuizOptionRow(
    option: QuestionOption,
    selected: Boolean,
    reveal: Boolean,
    correct: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        reveal && correct -> SuccessSoft
        reveal && selected && !correct -> ErrorSoft
        selected -> PrimaryPale
        else -> Surface
    }
    val borderColor = when {
        reveal && correct -> Success
        reveal && selected && !correct -> Error
        selected -> Primary
        else -> Line
    }
    val labelColor = when {
        reveal && correct -> Success
        reveal && selected && !correct -> Error
        selected -> Primary
        else -> TextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(if (selected) PrimarySoft else Background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                color = labelColor,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = option.text,
            style = MaterialTheme.typography.bodyMedium,
            color = TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (reveal && correct) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = "正确答案",
                tint = Success,
                modifier = Modifier.size(19.dp),
            )
        } else if (reveal && selected) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "所选错误",
                tint = Error,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun JudgeOption(
    text: String,
    selected: Boolean,
    reveal: Boolean,
    correct: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val containerColor = when {
        reveal && correct -> SuccessSoft
        reveal && selected && !correct -> ErrorSoft
        selected -> PrimaryPale
        else -> Surface
    }
    val borderColor = when {
        reveal && correct -> Success
        reveal && selected && !correct -> Error
        selected -> Primary
        else -> Line
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 21.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = if (selected) Primary else TextPrimary,
        )
        if (reveal) {
            Text(
                text = if (correct) "标准答案" else if (selected) "你的选择" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (correct) Success else Error,
            )
        }
    }
}

@Composable
private fun AnswerFeedback(
    question: QuizQuestion,
    outcome: QuestionOutcome,
    userAnswer: String,
) {
    val color = when (outcome) {
        QuestionOutcome.CORRECT -> Success
        QuestionOutcome.WRONG -> Error
        QuestionOutcome.UNANSWERED -> TextSecondary
    }
    val background = when (outcome) {
        QuestionOutcome.CORRECT -> SuccessSoft
        QuestionOutcome.WRONG -> ErrorSoft
        QuestionOutcome.UNANSWERED -> Background
    }
    LintiCard(containerColor = background, borderColor = color.copy(alpha = 0.35f)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (outcome == QuestionOutcome.CORRECT) {
                        Icons.Outlined.CheckCircle
                    } else {
                        Icons.Outlined.ErrorOutline
                    },
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = when (outcome) {
                        QuestionOutcome.CORRECT -> "回答正确"
                        QuestionOutcome.WRONG -> "回答错误"
                        QuestionOutcome.UNANSWERED -> "尚未作答"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = "标准答案：" + AnswerNormalizer.displayAnswer(
                    question.type,
                    question.correctAnswer,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
            )
            if (userAnswer.isNotBlank()) {
                Text(
                    text = "你的作答：" + AnswerNormalizer.displayAnswer(question.type, userAnswer),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            if (question.explanation.isNotBlank()) {
                Spacer(Modifier.height(9.dp))
                ThinDivider()
                Spacer(Modifier.height(9.dp))
                Text(
                    text = question.explanation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                )
            }
        }
    }
}

@Composable
fun AnswerSheetScreen(
    viewModel: LintiViewModel,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val quiz by viewModel.quizState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val revealCorrectness = quiz.mode != PracticeMode.EXAM || quiz.config.showAnswerAfterAnswer
    val grade = if (revealCorrectness) viewModel.gradeCurrent() else null
    val correct = grade?.correctCount ?: 0
    val wrong = grade?.wrongCount ?: 0
    val unanswered = if (revealCorrectness) {
        grade?.unansweredCount ?: quiz.questions.size
    } else {
        (quiz.questions.size - quiz.answeredCount).coerceAtLeast(0)
    }

    if (!quiz.active || quiz.questions.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(title = "答题卡", onBack = onBack)
            EmptyState(
                title = "没有进行中的答题",
                message = "返回题库重新开始一次练习。",
                modifier = Modifier.padding(20.dp),
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LintiPageHeader(
            title = "答题卡",
            subtitle = "${quiz.bankName} · ${quiz.mode.label}",
            onBack = onBack,
            actions = {
                StatusPill(
                    text = "${quiz.questions.size} 题",
                    color = Primary,
                    background = PrimaryPale,
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
                            color = Primary,
                        )
                        Text(
                            text = " / ${quiz.questions.size} 已作答",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        if (revealCorrectness) {
                            Text(
                                text = "正确 $correct · 错误 $wrong · 未做 $unanswered",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                            )
                        } else {
                            Text(
                                text = "考试中仅展示作答与标记状态",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
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
                        LegendDot(if (revealCorrectness) "正确" else "已答", Success)
                        if (revealCorrectness) {
                            LegendDot("错误", Error)
                        }
                        LegendDot("未做", TextSecondary)
                        LegendDot("当前", Primary)
                        LegendDot("已标记", Warning)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(quiz.questions, key = { _, q -> q.questionId }) { index, item ->
                    val outcome = if (revealCorrectness) {
                        grade?.outcomes?.get(item.questionId)?.outcome
                    } else {
                        null
                    }
                    val answered = quiz.answers[item.questionId].orEmpty().isNotBlank()
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
        Surface(color = Surface, shadowElevation = 8.dp) {
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
                    PrimaryActionButton(
                        text = "交卷",
                        modifier = Modifier.weight(1f),
                        onClick = { showSubmitConfirm = true },
                    )
                }
            }
        }
    }

    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("交卷并查看成绩？") },
            text = {
                Text(
                    "未答 ${quiz.questions.size - quiz.answeredCount} 题。" +
                        "交卷后本次错题会自动进入错题本。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isSubmitting,
                    onClick = {
                        scope.launch {
                            isSubmitting = true
                            showSubmitConfirm = false
                            if (viewModel.submitQuiz()) onFinished()
                            isSubmitting = false
                        }
                    },
                ) {
                    Text(if (isSubmitting) "正在判分…" else "确认交卷")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) {
                    Text("继续检查")
                }
            },
        )
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
            color = TextSecondary,
        )
    }
}

@Composable
private fun AnswerGridCell(
    number: Int,
    current: Boolean,
    flagged: Boolean,
    outcome: QuestionOutcome?,
    answered: Boolean,
    onClick: () -> Unit,
) {
    val background = when (outcome) {
        QuestionOutcome.CORRECT -> SuccessSoft
        QuestionOutcome.WRONG -> ErrorSoft
        else -> if (answered) PrimaryPale else Background
    }
    val color = when (outcome) {
        QuestionOutcome.CORRECT -> Success
        QuestionOutcome.WRONG -> Error
        else -> if (answered) Primary else TextSecondary
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(11.dp))
            .background(background)
            .border(
                width = if (current) 2.dp else 1.dp,
                color = if (current) Primary else Line,
                shape = RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick),
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
                tint = Warning,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(10.dp),
            )
        }
    }
}

@Composable
fun ExamSetupScreen(
    viewModel: LintiViewModel,
    bankId: Long,
    onBack: () -> Unit,
    onQuiz: () -> Unit,
) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf<ExamConfig?>(null) }
    var available by remember { mutableStateOf<Map<QuestionType, Int>>(emptyMap()) }
    var passScoreText by remember { mutableStateOf("60") }
    var starting by remember { mutableStateOf(false) }
    val bankName = banks.firstOrNull { it.id == bankId }?.name ?: "题库"

    LaunchedEffect(bankId) {
        val setup = viewModel.examSetupData(bankId)
        available = setup.available
        config = setup.config
        passScoreText = formatScore(setup.config.passScore)
    }

    val currentConfig = config
    if (currentConfig == null) {
        Column {
            LintiPageHeader(title = "模拟考试", onBack = onBack)
            LintiCard(
                modifier = Modifier.padding(20.dp),
                containerColor = PrimaryPale,
            ) {
                EmptyState("正在准备组卷", "读取本机题库中的题型与题量。")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LintiPageHeader(
            title = "模拟考试",
            subtitle = "题库：$bankName",
            onBack = onBack,
            actions = {
                StatusPill("自定义组卷", color = Primary, background = PrimaryPale)
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "按当前题库组卷，数量不足时会明确提示。",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
            item {
                SectionHeader(title = "题型与分值", trailing = "共 ${currentConfig.totalQuestions} 题")
            }
            item {
                LintiCard {
                    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        QuestionType.entries.forEach { type ->
                            ExamTypeRow(
                                type = type,
                                count = currentConfig.countFor(type),
                                available = available[type] ?: 0,
                                points = currentConfig.pointsFor(type),
                                onCountChange = { count ->
                                    config = currentConfig.withCount(type, count)
                                },
                                onPointsChange = { points ->
                                    config = currentConfig.withPoints(type, points)
                                },
                            )
                        }
                        ThinDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "试卷总分",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${formatScore(currentConfig.totalPoints)} 分",
                                style = MaterialTheme.typography.titleLarge,
                                color = Primary,
                            )
                        }
                    }
                }
            }
            item {
                SectionHeader(title = "时间与及格线")
            }
            item {
                LintiCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "考试时长",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = "建议 30–120 分钟",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                            }
                            Counter(
                                value = currentConfig.durationMinutes,
                                suffix = " 分钟",
                                min = 5,
                                max = 240,
                                step = 5,
                                onChange = {
                                    config = currentConfig.copy(durationMinutes = it)
                                },
                            )
                        }
                        ThinDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "及格分",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = "满分 ${formatScore(currentConfig.totalPoints)} 分",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                            }
                            OutlinedTextField(
                                value = passScoreText,
                                onValueChange = { input ->
                                    passScoreText = input.filter { it.isDigit() || it == '.' }
                                    passScoreText.toDoubleOrNull()?.let { score ->
                                        config = currentConfig.copy(
                                            passScore = score.coerceIn(
                                                0.0,
                                                currentConfig.totalPoints.coerceAtLeast(0.0),
                                            ),
                                        )
                                    }
                                },
                                modifier = Modifier.width(96.dp),
                                singleLine = true,
                                suffix = { Text("分") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = MaterialTheme.typography.bodyMedium,
                                shape = RoundedCornerShape(10.dp),
                            )
                        }
                    }
                }
            }
            item {
                SectionHeader(title = "组卷与判分规则")
            }
            item {
                LintiCard {
                    Column {
                        SettingSwitchRow(
                            title = "选项乱序",
                            subtitle = "仅打乱展示顺序，标准答案跟随选项",
                            checked = currentConfig.shuffleOptions,
                            onCheckedChange = {
                                config = currentConfig.copy(shuffleOptions = it)
                            },
                        )
                        ThinDivider()
                        SettingSwitchRow(
                            title = "错题优先",
                            subtitle = "优先抽取本题库错题本中的题目",
                            checked = currentConfig.wrongFirst,
                            onCheckedChange = {
                                config = currentConfig.copy(wrongFirst = it)
                            },
                        )
                        ThinDivider()
                        SettingSwitchRow(
                            title = "未做优先",
                            subtitle = "优先抽取从未作答过的题目",
                            checked = currentConfig.unansweredFirst,
                            onCheckedChange = {
                                config = currentConfig.copy(unansweredFirst = it)
                            },
                        )
                        ThinDivider()
                        SettingSwitchRow(
                            title = "答后立即显示答案",
                            subtitle = "关闭时交卷后统一查看答案与解析",
                            checked = currentConfig.showAnswerAfterAnswer,
                            onCheckedChange = {
                                config = currentConfig.copy(showAnswerAfterAnswer = it)
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "多选计分",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MultiSelectRule.entries.forEach { rule ->
                                FilterChip(
                                    selected = currentConfig.multiSelectRule == rule,
                                    onClick = {
                                        config = currentConfig.copy(multiSelectRule = rule)
                                    },
                                    label = { Text(rule.label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "填空题按归一化后的答案完全一致计分。",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
            item {
                val missing = QuestionType.entries.any { type ->
                    currentConfig.countFor(type) > (available[type] ?: 0)
                }
                if (missing) {
                    WarningNote("部分题型数量超过题库可用题量，开始后会提示不足。")
                }
            }
        }
        Surface(color = Surface, shadowElevation = 8.dp) {
            Column {
                ThinDivider()
                PrimaryActionButton(
                    text = if (starting) "正在组卷…" else "开始考试",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 11.dp),
                    enabled = !starting && currentConfig.totalQuestions > 0,
                    icon = Icons.Outlined.TaskAlt,
                    onClick = {
                        scope.launch {
                            starting = true
                            val result = viewModel.startExam(bankId, currentConfig)
                            if (result.started) {
                                if (result.warnings.isNotEmpty()) {
                                    viewModel.showMessage(result.warnings.joinToString("；"))
                                }
                                onQuiz()
                            } else {
                                viewModel.showMessage(result.message ?: "无法开始考试")
                            }
                            starting = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ExamTypeRow(
    type: QuestionType,
    count: Int,
    available: Int,
    points: Double,
    onCountChange: (Int) -> Unit,
    onPointsChange: (Double) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = type.label,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = "可用 $available 题",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        Counter(
            value = count,
            suffix = " 题",
            min = 0,
            max = available,
            step = 1,
            onChange = onCountChange,
        )
        Spacer(Modifier.width(10.dp))
        Counter(
            value = (points * 2).toInt(),
            suffix = " 分",
            min = 1,
            max = 20,
            step = 1,
            displayValue = formatScore(points),
            onChange = { onPointsChange(it / 2.0) },
        )
    }
}

@Composable
private fun Counter(
    value: Int,
    suffix: String,
    min: Int,
    max: Int,
    step: Int,
    modifier: Modifier = Modifier,
    displayValue: String = value.toString(),
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Background)
            .border(1.dp, Line, RoundedCornerShape(10.dp))
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onChange((value - step).coerceAtLeast(min)) },
            enabled = value > min,
        ) {
            Icon(
                Icons.Outlined.Remove,
                contentDescription = "减少",
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = displayValue + suffix,
            style = MaterialTheme.typography.labelMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(50.dp),
        )
        IconButton(
            onClick = { onChange((value + step).coerceAtMost(max)) },
            enabled = value < max,
        ) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = "增加",
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ResultScreen(
    viewModel: LintiViewModel,
    onClose: () -> Unit,
    onOpenBank: (Long) -> Unit,
    onWrongBook: (Long?) -> Unit,
    onRetryExam: (Long) -> Unit,
) {
    val result by viewModel.lastResult.collectAsStateWithLifecycle()
    val data = result

    if (data == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(title = "成绩报告", onBack = onClose)
            EmptyState(
                title = "没有可显示的成绩",
                message = "请重新完成一次练习或考试。",
                modifier = Modifier.padding(20.dp),
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LintiPageHeader(
                title = "成绩报告",
                subtitle = "${data.bankName} · ${data.mode.label}",
                onBack = onClose,
                actions = {
                    StatusPill("已完成", color = Success, background = SuccessSoft)
                },
            )
        }
        item {
            LintiCard(
                containerColor = PrimaryPale,
                borderColor = PrimarySoft,
            ) {
                Column {
                    Text(
                        text = "${data.bankName} · ${data.mode.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatScore(data.grading.totalScore),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = MaterialTheme.typography.headlineLarge.fontSize * 2.2f,
                            ),
                            color = if (data.grading.passed) Success else Error,
                        )
                        Text(
                            text = " / ${formatScore(data.grading.maxScore)} 分",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 9.dp),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    StatusPill(
                        text = if (data.grading.passed) {
                            "及格 · 及格线 ${formatScore(data.config.passScore)} 分"
                        } else {
                            "未达及格线 ${formatScore(data.config.passScore)} 分"
                        },
                        color = if (data.grading.passed) Success else Error,
                        background = if (data.grading.passed) SuccessSoft else ErrorSoft,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "用时 ${formatDuration(data.durationSeconds)} · 共 ${data.questions.size} 题 · 限时 ${data.config.durationMinutes} 分钟",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
        item {
            MetricStrip(
                items = listOf(
                    MetricItem("${data.grading.correctCount}", "正确", Success),
                    MetricItem("${data.grading.wrongCount}", "错误", Error),
                    MetricItem("${data.grading.unansweredCount}", "未做", TextSecondary),
                    MetricItem(formatPercent(data.grading.accuracy), "正确率", Primary),
                ),
            )
        }
        item {
            SectionHeader(title = "分题型统计", trailing = "完全答对 / 题数")
        }
        item {
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    data.grading.typeStats.filter { it.total > 0 }.forEach { stats ->
                        TypeStatsRow(stats)
                    }
                    if (data.grading.typeStats.all { it.total == 0 }) {
                        Text(
                            text = "本次没有分题型数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }
        item {
            LintiCard(containerColor = Background, borderColor = Line) {
                Text(
                    text = "交卷后错题已自动写入错题本。答题与错题状态都保存在本机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (data.bankId != null && data.mode == PracticeMode.EXAM) {
                    PrimaryActionButton(
                        text = "再考一次",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onRetryExam(data.bankId) },
                    )
                }
                if (data.grading.wrongCount > 0) {
                    OutlinedActionButton(
                        text = "查看错题",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onWrongBook(data.bankId) },
                    )
                }
                if (data.bankId != null) {
                    OutlinedActionButton(
                        text = "返回题库",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpenBank(data.bankId) },
                    )
                } else {
                    OutlinedActionButton(
                        text = "返回练习中枢",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onClose,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeStatsRow(stats: TypeStats) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stats.type.label,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${stats.correct} / ${stats.total} · ${formatPercent(stats.accuracy)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (stats.accuracy >= 0.6) Success else Error,
            )
        }
        Spacer(Modifier.height(5.dp))
        SimpleProgressBar(
            progress = stats.accuracy.toFloat(),
            progressColor = if (stats.accuracy >= 0.6) Success else Error,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "得分 ${formatScore(stats.score)} / ${formatScore(stats.maxScore)} · " +
                "错误 ${stats.wrong} · 未做 ${stats.unanswered}",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}
