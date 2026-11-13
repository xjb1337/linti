package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
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
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextStyle
import com.xjb.linti.domain.FontScale
import com.xjb.linti.domain.ReadingMode
import com.xjb.linti.ui.QuizUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private class PagerScrollGuard {
    var target: Int? = null
    var seenJump: Long = -1L
    var seenSession: Long = -1L
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    viewModel: LintiViewModel,
    onBack: () -> Unit,
    onAnswerSheet: () -> Unit,
    onFinished: () -> Unit,
    onWrongBook: (Long?) -> Unit = {},
    onEditQuestion: (Long) -> Unit = {},
) {
    val quiz by viewModel.quizState.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
    val readingMode by viewModel.readingMode.collectAsStateWithLifecycle()
    val autoNext by viewModel.autoNextOnCorrect.collectAsStateWithLifecycle()
    val pagerJump by viewModel.pagerJump.collectAsStateWithLifecycle()
    val palette = LocalLintiPalette.current
    val scope = rememberCoroutineScope()

    var showSettings by remember { mutableStateOf(false) }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var showExitConfirm by remember { mutableStateOf(false) }
    var showRetryConfirm by remember { mutableStateOf(false) }
    var noteDialogFor by remember { mutableStateOf<Long?>(null) }
    var noteText by remember { mutableStateOf("") }
    var noteRefresh by remember { mutableStateOf(0) }
    var isSubmitting by remember { mutableStateOf(false) }

    fun handleBack() {
        if (quiz.mode == PracticeMode.EXAM) {
            showExitConfirm = true
        } else {
            viewModel.leaveQuiz()
            onBack()
        }
    }
    BackHandler(onBack = { handleBack() })

    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000L)
            tick++
        }
    }
    val countdown = remember(tick) { viewModel.countdownState() }

    var warnedFiveMinutes by remember(quiz.sessionToken) { mutableStateOf(false) }
    LaunchedEffect(countdown.warning) {
        if (quiz.mode == PracticeMode.EXAM && countdown.warning && !warnedFiveMinutes) {
            warnedFiveMinutes = true
            viewModel.showMessage("距离交卷还有 5 分钟")
        }
    }
    LaunchedEffect(quiz.sessionToken) {
        if (quiz.mode == PracticeMode.EXAM) {
            viewModel.showMessage("本场考试交卷后统一显示答案与解析")
        }
    }
    LaunchedEffect(quiz.sessionToken) {
        while (isActive) {
            delay(1000L)
            if (viewModel.autoSubmitIfExpired()) {
                onFinished()
                break
            }
        }
    }

    LaunchedEffect(noteDialogFor, noteRefresh) {
        val questionId = noteDialogFor
        noteText = if (questionId == null) "" else viewModel.loadNote(questionId)
    }

    if (!quiz.active || quiz.currentQuestion == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(title = "答题", onBack = onBack)
            LintiCard(
                modifier = Modifier.padding(20.dp),
                containerColor = palette.primaryPale,
            ) {
                EmptyState(
                    title = "当前没有进行中的答题",
                    message = "请从题库或练习中枢重新开始。",
                    action = { OutlinedActionButton(text = "返回", onClick = onBack) },
                )
            }
        }
        return
    }

    val questions = quiz.questions
    val pagerState = rememberPagerState(initialPage = quiz.currentIndex) { questions.size }
    val scrollGuard = remember { PagerScrollGuard() }
    if (pagerJump != scrollGuard.seenJump || quiz.sessionToken != scrollGuard.seenSession) {
        scrollGuard.seenJump = pagerJump
        scrollGuard.seenSession = quiz.sessionToken
        scrollGuard.target = quiz.currentIndex
    }
    val currentIndexState = rememberUpdatedState(quiz.currentIndex)
    LaunchedEffect(pagerState, quiz.sessionToken) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val pending = scrollGuard.target
            if (pending != null) {
                if (page == pending) scrollGuard.target = null
            } else if (page != currentIndexState.value) {
                viewModel.goToQuestion(page, fromPager = true)
            }
        }
    }
    LaunchedEffect(pagerJump, quiz.sessionToken) {
        val target = quiz.currentIndex
        if (pagerState.settledPage != target) {
            try {
                pagerState.animateScrollToPage(target)
            } finally {
                if (scrollGuard.target == target) scrollGuard.target = null
            }
        }
    }
    var lastCommittedCount by remember(quiz.sessionToken) { mutableStateOf(quiz.committedIds.size) }
    LaunchedEffect(quiz.committedIds.size) {
        val count = quiz.committedIds.size
        val indexAtCommit = quiz.currentIndex
        if (count > lastCommittedCount && autoNext && quiz.mode != PracticeMode.EXAM) {
            val current = quiz.currentQuestion
            if (current != null && current.questionId in quiz.committedIds &&
                viewModel.outcomeFor(current.questionId) == QuestionOutcome.CORRECT &&
                indexAtCommit < questions.lastIndex
            ) {
                delay(600L)
                if (pagerState.settledPage == indexAtCommit) {
                    viewModel.goToQuestion(indexAtCommit + 1)
                }
            }
        }
        lastCommittedCount = count
    }

    val correctCount = quiz.committedIds.count { viewModel.outcomeFor(it) == QuestionOutcome.CORRECT }
    val wrongCount = (quiz.committedIds.size - correctCount).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LintiPageHeader(
            title = if (quiz.mode == PracticeMode.EXAM && !countdown.unlimited) {
                countdown.display
            } else {
                quiz.mode.label
            },
            subtitle = "${quiz.bankName} · ${quiz.mode.label}",
            onBack = { handleBack() },
            titleColor = if (quiz.mode == PracticeMode.EXAM && countdown.warning && !countdown.unlimited) {
                palette.warning
            } else {
                Color.Unspecified
            },
            actions = {
                IconButton(onClick = viewModel::toggleCurrentBookmark) {
                    Icon(
                        imageVector = if (quiz.currentBookmarked) {
                            Icons.Outlined.Bookmark
                        } else {
                            Icons.Outlined.BookmarkBorder
                        },
                        contentDescription = "收藏",
                        tint = if (quiz.currentBookmarked) palette.primary else palette.textSecondary,
                    )
                }
                IconButton(onClick = { noteDialogFor = quiz.currentQuestion?.questionId }) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = "笔记",
                        tint = palette.aiAccent,
                    )
                }
                IconButton(onClick = { showSettings = true }) {
                    Text(
                        text = "⋯",
                        style = MaterialTheme.typography.titleLarge,
                        color = palette.textSecondary,
                    )
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${quiz.currentIndex + 1} / ${questions.size}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            SimpleProgressBar(
                progress = (quiz.currentIndex + 1f) / questions.size.coerceAtLeast(1),
                modifier = Modifier.weight(1f),
                progressColor = if (quiz.mode == PracticeMode.EXAM && countdown.warning && !countdown.unlimited) {
                    palette.warning
                } else {
                    palette.primary
                },
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (quiz.mode == PracticeMode.EXAM) {
                    "已答 ${quiz.answeredCount}"
                } else {
                    "已做 ${quiz.committedIds.size}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (quiz.mode == PracticeMode.EXAM && countdown.warning && !countdown.unlimited) {
                    palette.warning
                } else {
                    palette.textSecondary
                },
                maxLines = 1,
            )
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            key = { questions[it].questionId },
            beyondViewportPageCount = 1,
        ) { page ->
            val question = questions[page]
            QuizQuestionPage(
                question = question,
                quiz = quiz,
                viewModel = viewModel,
                fontScale = fontScale.factor,
                noteRefresh = noteRefresh,
                onOpenNote = { noteDialogFor = question.questionId },
                allCommitted = quiz.committedIds.size == questions.size,
                isLast = page == questions.lastIndex,
                correctCount = correctCount,
                wrongCount = wrongCount,
                onWrongBook = { onWrongBook(quiz.bankId) },
                onBackToBank = { handleBack() },
            )
        }
        Surface(color = palette.surface, shadowElevation = 8.dp) {
            Column {
                ThinDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (quiz.mode == PracticeMode.EXAM) {
                        OutlinedActionButton(
                            text = "答题卡 已答 ${quiz.answeredCount}/${questions.size}",
                            onClick = onAnswerSheet,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryActionButton(
                            text = "交卷",
                            onClick = { showSubmitConfirm = true },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            text = if (quiz.currentBookmarked) "★ 收藏" else "☆ 收藏",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = viewModel::toggleCurrentBookmark),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "✓ 对 $correctCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.success,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "✗ 错 $wrongCount",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = "已做 ${quiz.committedIds.size}/${questions.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(onClick = onAnswerSheet),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            QuizSettingsSheet(
                quiz = quiz,
                fontScale = fontScale,
                readingMode = readingMode,
                autoNext = autoNext,
                onEditQuestion = { questionId ->
                    showSettings = false
                    onEditQuestion(questionId)
                },
                onRetry = {
                    showSettings = false
                    showRetryConfirm = true
                },
                onToggleFlag = viewModel::toggleCurrentFlag,
                onFontScale = viewModel::setFontScale,
                onReadingMode = viewModel::setReadingMode,
                onAutoNext = viewModel::setAutoNextOnCorrect,
                onDismiss = { showSettings = false },
            )
        }
    }

    if (showRetryConfirm) {
        AlertDialog(
            onDismissRequest = { showRetryConfirm = false },
            title = { Text("重练本题？") },
            text = { Text("将清除本题本次作答与提交状态，历史作答记录不会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    quiz.currentQuestion?.questionId?.let { viewModel.retryQuestion(it) }
                    showRetryConfirm = false
                }) { Text("重练") }
            },
            dismissButton = {
                TextButton(onClick = { showRetryConfirm = false }) { Text("取消") }
            },
        )
    }

    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirm = false },
            title = { Text("确认交卷？") },
            text = {
                Text("已答 ${quiz.answeredCount} / ${questions.size} 题，未答 ${questions.size - quiz.answeredCount} 题。交卷后错题会自动进入错题本。")
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
                ) { Text(if (isSubmitting) "正在判分…" else "确认交卷") }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirm = false }) { Text("继续作答") }
            },
        )
    }

    if (showExitConfirm) {
        AlertDialog(
            onDismissRequest = { showExitConfirm = false },
            title = { Text("退出考试？") },
            text = { Text("本次作答不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirm = false
                    viewModel.leaveQuiz()
                    onBack()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirm = false }) { Text("继续作答") }
            },
        )
    }

    if (noteDialogFor != null) {
        val targetId = noteDialogFor ?: return
        AlertDialog(
            onDismissRequest = { noteDialogFor = null },
            title = { Text("本题笔记") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        viewModel.saveNote(targetId, noteText)
                        noteRefresh++
                        noteDialogFor = null
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { noteDialogFor = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun QuizQuestionPage(
    question: QuizQuestion,
    quiz: QuizUiState,
    viewModel: LintiViewModel,
    fontScale: Float,
    noteRefresh: Int,
    onOpenNote: () -> Unit,
    allCommitted: Boolean,
    isLast: Boolean,
    correctCount: Int,
    wrongCount: Int,
    onWrongBook: () -> Unit,
    onBackToBank: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    val reveal = quiz.revealed(question.questionId)
    val currentAnswer = quiz.answers[question.questionId].orEmpty()
    val outcome = if (reveal) {
        viewModel.outcomeFor(question.questionId)
    } else {
        QuestionOutcome.UNANSWERED
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                text = question.type.label,
                color = palette.primary,
                background = palette.primaryPale,
            )
            if (quiz.mode == PracticeMode.EXAM) {
                StatusPill(
                    text = "${formatScore(question.points)} 分",
                    color = palette.warning,
                    background = palette.warningSoft,
                )
            }
        }
        Text(
            text = question.stem,
            style = scaledTextStyle(MaterialTheme.typography.bodyLarge, fontScale),
            color = palette.textPrimary,
        )
        when (question.type) {
            QuestionType.SINGLE, QuestionType.MULTI -> {
                question.options.forEach { option ->
                    QuizChoiceRow(
                        badge = option.label,
                        text = option.text,
                        selected = option.label.uppercase() in AnswerNormalizer.lettersIn(currentAnswer),
                        reveal = reveal,
                        correct = option.label.uppercase() in AnswerNormalizer.lettersIn(question.correctAnswer),
                        fontScale = fontScale,
                        onClick = { viewModel.selectAnswer(question.questionId, option.label) },
                    )
                }
                if (question.type == QuestionType.MULTI && !reveal) {
                    Text(
                        text = "多选题：勾选全部正确选项后确认答案",
                        style = MaterialTheme.typography.labelSmall,
                        color = palette.textSecondary,
                    )
                }
                if (!reveal && viewModel.needsConfirm(question.questionId)) {
                    PrimaryActionButton(
                        text = "确认答案",
                        onClick = { viewModel.commitAnswer(question.questionId) },
                        enabled = viewModel.canCommit(question.questionId),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            QuestionType.JUDGE -> {
                val correctIsTrue = AnswerNormalizer.normalizeJudge(question.correctAnswer) == "true"
                QuizChoiceRow(
                    badge = "A",
                    text = "正确",
                    selected = currentAnswer == "true",
                    reveal = reveal,
                    correct = correctIsTrue,
                    fontScale = fontScale,
                    onClick = { viewModel.selectAnswer(question.questionId, "true") },
                )
                QuizChoiceRow(
                    badge = "B",
                    text = "错误",
                    selected = currentAnswer == "false",
                    reveal = reveal,
                    correct = !correctIsTrue,
                    fontScale = fontScale,
                    onClick = { viewModel.selectAnswer(question.questionId, "false") },
                )
            }

            QuestionType.FILL -> {
                OutlinedTextField(
                    value = currentAnswer,
                    onValueChange = { viewModel.setFillAnswer(question.questionId, it) },
                    label = { Text("填写答案") },
                    minLines = 1,
                    maxLines = 4,
                    readOnly = reveal,
                    enabled = !reveal,
                    textStyle = scaledTextStyle(MaterialTheme.typography.bodyMedium, fontScale),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!reveal && viewModel.needsConfirm(question.questionId)) {
                    PrimaryActionButton(
                        text = "确认答案",
                        onClick = { viewModel.commitAnswer(question.questionId) },
                        enabled = viewModel.canCommit(question.questionId),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (reveal) {
            AnswerFeedback(
                question = question,
                outcome = outcome,
                userAnswer = currentAnswer,
                viewModel = viewModel,
                fontScale = fontScale,
                noteRefresh = noteRefresh,
                onOpenNote = onOpenNote,
            )
        }
        if (quiz.mode != PracticeMode.EXAM && allCommitted && isLast) {
            RoundCompleteCard(
                correct = correctCount,
                wrong = wrongCount,
                onWrongBook = onWrongBook,
                onBackToBank = onBackToBank,
            )
        }
    }
}

@Composable
private fun QuizChoiceRow(
    badge: String,
    text: String,
    selected: Boolean,
    reveal: Boolean,
    correct: Boolean,
    fontScale: Float,
    onClick: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    val badgeBackground = when {
        reveal && correct -> palette.success
        reveal && selected && !correct -> palette.error
        selected -> palette.primary
        else -> palette.background
    }
    val badgeContent = when {
        reveal && correct -> palette.onPrimary
        reveal && selected && !correct -> palette.onPrimary
        selected -> palette.onPrimary
        else -> palette.textSecondary
    }
    val containerColor = when {
        reveal && correct -> palette.successSoft
        reveal && selected && !correct -> palette.errorSoft
        selected -> palette.primaryPale
        else -> palette.surface
    }
    val borderColor = when {
        reveal && correct -> palette.success
        reveal && selected && !correct -> palette.error
        selected -> palette.primary
        else -> palette.line
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(badgeBackground),
            contentAlignment = Alignment.Center,
        ) {
            when {
                reveal && correct -> Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = "正确",
                    tint = badgeContent,
                    modifier = Modifier.size(22.dp),
                )

                reveal && selected -> Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "错误",
                    tint = badgeContent,
                    modifier = Modifier.size(22.dp),
                )

                else -> Text(
                    text = badge,
                    style = MaterialTheme.typography.labelLarge,
                    color = badgeContent,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = text,
            style = scaledTextStyle(MaterialTheme.typography.bodyMedium, fontScale),
            color = if (reveal && correct) palette.success else palette.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AnswerFeedback(
    question: QuizQuestion,
    outcome: QuestionOutcome,
    userAnswer: String,
    viewModel: LintiViewModel,
    fontScale: Float,
    noteRefresh: Int,
    onOpenNote: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    val stats = viewModel.attemptStatsFor(question.questionId)
    val note by produceState(
        initialValue = "",
        key1 = question.questionId,
        key2 = noteRefresh,
    ) {
        value = viewModel.loadNote(question.questionId)
    }
    val accent = when (outcome) {
        QuestionOutcome.CORRECT -> palette.success
        QuestionOutcome.WRONG -> palette.error
        else -> palette.textSecondary
    }
    val background = when (outcome) {
        QuestionOutcome.CORRECT -> palette.successSoft
        QuestionOutcome.WRONG -> palette.errorSoft
        else -> palette.background
    }
    LintiCard(containerColor = background, borderColor = accent.copy(alpha = 0.35f)) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                text = when (outcome) {
                    QuestionOutcome.CORRECT -> "回答正确"
                    QuestionOutcome.WRONG -> "回答错误"
                    else -> "尚未作答"
                },
                style = scaledTextStyle(MaterialTheme.typography.titleMedium, fontScale),
                color = accent,
            )
            Text(
                text = "正确答案：" + AnswerNormalizer.displayAnswer(question.type, question.correctAnswer),
                style = scaledTextStyle(MaterialTheme.typography.bodyMedium, fontScale),
                color = palette.success,
            )
            if (userAnswer.isNotBlank()) {
                Text(
                    text = "你的答案：" + AnswerNormalizer.displayAnswer(question.type, userAnswer),
                    style = scaledTextStyle(MaterialTheme.typography.bodyMedium, fontScale),
                    color = if (outcome == QuestionOutcome.CORRECT) palette.success else palette.error,
                )
            }
            Text(
                text = "本人作答 ${stats.total} 次 · 正确率 ${formatPercent(stats.accuracy)}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
            )
            ThinDivider()
            Text(
                text = "── 解析 ──",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
            )
            Text(
                text = if (question.explanation.isBlank()) "暂无解析" else question.explanation,
                style = scaledTextStyle(MaterialTheme.typography.bodySmall, fontScale),
                color = palette.textPrimary,
            )
            ThinDivider()
            Text(
                text = "── 我的笔记 ──",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
            )
            val notePreview = note.lines().filter { it.isNotBlank() }.take(3).joinToString("\n")
                .ifBlank { "暂无笔记，点击添加" }
            Text(
                text = notePreview,
                style = scaledTextStyle(MaterialTheme.typography.bodySmall, fontScale),
                color = palette.textPrimary,
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenNote),
            )
        }
    }
}

@Composable
private fun RoundCompleteCard(
    correct: Int,
    wrong: Int,
    onWrongBook: () -> Unit,
    onBackToBank: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    val total = correct + wrong
    LintiCard(containerColor = palette.successSoft, borderColor = palette.success) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "本轮完成",
                style = MaterialTheme.typography.titleMedium,
                color = palette.success,
            )
            Text(
                text = "对 $correct · 错 $wrong · 正确率 ${formatPercent(if (total == 0) 0.0 else correct.toDouble() / total)}",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (wrong > 0) {
                    OutlinedActionButton(
                        text = "查看错题",
                        onClick = onWrongBook,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedActionButton(
                    text = "返回题库",
                    onClick = onBackToBank,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuizSettingsSheet(
    quiz: QuizUiState,
    fontScale: FontScale,
    readingMode: ReadingMode,
    autoNext: Boolean,
    onEditQuestion: (Long) -> Unit,
    onRetry: () -> Unit,
    onToggleFlag: () -> Unit,
    onFontScale: (FontScale) -> Unit,
    onReadingMode: (ReadingMode) -> Unit,
    onAutoNext: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    val isExam = quiz.mode == PracticeMode.EXAM
    val currentId = quiz.currentQuestion?.questionId
    val committed = currentId != null && currentId in quiz.committedIds
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "答题设置",
            style = MaterialTheme.typography.titleLarge,
            color = palette.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        if (!isExam && currentId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("试题编辑", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onEditQuestion(currentId) }) { Text("编辑本题") }
            }
        }
        if (!isExam) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "重练本题",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (committed) palette.textPrimary else palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = committed, onClick = onRetry) { Text("重练") }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("标记本题", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(
                checked = quiz.currentFlagged,
                onCheckedChange = { onToggleFlag() },
            )
        }
        if (!isExam) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("答对自动跳转下一题", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = autoNext, onCheckedChange = onAutoNext)
            }
        }
        ThinDivider()
        Text("字体大小", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontScale.entries.forEach { value ->
                FilterChip(
                    selected = fontScale == value,
                    onClick = { onFontScale(value) },
                    label = {
                        Text(
                            when (value) {
                                FontScale.SMALL -> "小"
                                FontScale.STANDARD -> "标准"
                                FontScale.LARGE -> "大"
                                FontScale.EXTRA_LARGE -> "较大"
                            },
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        Text("阅读模式", style = MaterialTheme.typography.labelMedium, color = palette.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingMode.entries.forEach { value ->
                FilterChip(
                    selected = readingMode == value,
                    onClick = { onReadingMode(value) },
                    label = {
                        Text(
                            when (value) {
                                ReadingMode.DAY -> "日间"
                                ReadingMode.NIGHT -> "夜间"
                                ReadingMode.EYE_CARE -> "护眼"
                            },
                            maxLines = 1,
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedActionButton(
            text = "完成",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun scaledTextStyle(style: TextStyle, factor: Float): TextStyle =
    if (factor == 1f) {
        style
    } else {
        style.copy(
            fontSize = style.fontSize * factor,
            lineHeight = style.lineHeight * factor,
        )
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
                        } else {
                            Text(
                                text = "考试中仅展示作答与标记状态",
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
                            onClick = { showSubmitConfirm = true },
                        )
                    }
                }
            }
        }
    }

    if (showSubmitConfirm && quiz.mode == PracticeMode.EXAM) {
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
            color = LocalLintiPalette.current.textSecondary,
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
                tint = LocalLintiPalette.current.warning,
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
                containerColor = LocalLintiPalette.current.primaryPale,
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
                StatusPill("自定义组卷", color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
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
                    color = LocalLintiPalette.current.textSecondary,
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
                                color = LocalLintiPalette.current.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${formatScore(currentConfig.totalPoints)} 分",
                                style = MaterialTheme.typography.titleLarge,
                                color = LocalLintiPalette.current.primary,
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
                                    color = LocalLintiPalette.current.textPrimary,
                                )
                                Text(
                                    text = "建议 30–120 分钟",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalLintiPalette.current.textSecondary,
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
                                    color = LocalLintiPalette.current.textPrimary,
                                )
                                Text(
                                    text = "满分 ${formatScore(currentConfig.totalPoints)} 分",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalLintiPalette.current.textSecondary,
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
                            color = LocalLintiPalette.current.textSecondary,
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
                            color = LocalLintiPalette.current.textSecondary,
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
        Surface(color = LocalLintiPalette.current.surface, shadowElevation = 8.dp) {
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
                color = LocalLintiPalette.current.textPrimary,
            )
            Text(
                text = "可用 $available 题",
                style = MaterialTheme.typography.labelSmall,
                color = LocalLintiPalette.current.textSecondary,
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
            .background(LocalLintiPalette.current.background)
            .border(1.dp, LocalLintiPalette.current.line, RoundedCornerShape(10.dp))
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
            color = LocalLintiPalette.current.textPrimary,
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
                color = LocalLintiPalette.current.textPrimary,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = LocalLintiPalette.current.textSecondary,
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
                    StatusPill("已完成", color = LocalLintiPalette.current.success, background = LocalLintiPalette.current.successSoft)
                },
            )
        }
        if (data.autoSubmitted) {
            item {
                WarningNote(message = "已到时自动交卷")
            }
        }
        item {
            LintiCard(
                containerColor = LocalLintiPalette.current.primaryPale,
                borderColor = LocalLintiPalette.current.primarySoft,
            ) {
                Column {
                    Text(
                        text = "${data.bankName} · ${data.mode.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatScore(data.grading.totalScore),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontSize = MaterialTheme.typography.headlineLarge.fontSize * 2.2f,
                            ),
                            color = if (data.grading.passed) LocalLintiPalette.current.success else LocalLintiPalette.current.error,
                        )
                        Text(
                            text = " / ${formatScore(data.grading.maxScore)} 分",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LocalLintiPalette.current.textSecondary,
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
                        color = if (data.grading.passed) LocalLintiPalette.current.success else LocalLintiPalette.current.error,
                        background = if (data.grading.passed) LocalLintiPalette.current.successSoft else LocalLintiPalette.current.errorSoft,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "用时 ${formatDuration(data.durationSeconds)} · 共 ${data.questions.size} 题 · 限时 ${data.config.durationMinutes} 分钟",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                }
            }
        }
        item {
            MetricStrip(
                items = listOf(
                    MetricItem("${data.grading.correctCount}", "正确", LocalLintiPalette.current.success),
                    MetricItem("${data.grading.wrongCount}", "错误", LocalLintiPalette.current.error),
                    MetricItem("${data.grading.unansweredCount}", "未做", LocalLintiPalette.current.textSecondary),
                    MetricItem(formatPercent(data.grading.accuracy), "正确率", LocalLintiPalette.current.primary),
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
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                }
            }
        }
        item {
            LintiCard(containerColor = LocalLintiPalette.current.background, borderColor = LocalLintiPalette.current.line) {
                Text(
                    text = "交卷后错题已自动写入错题本。答题与错题状态都保存在本机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
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
                color = LocalLintiPalette.current.textPrimary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${stats.correct} / ${stats.total} · ${formatPercent(stats.accuracy)}",
                style = MaterialTheme.typography.labelMedium,
                color = if (stats.accuracy >= 0.6) LocalLintiPalette.current.success else LocalLintiPalette.current.error,
            )
        }
        Spacer(Modifier.height(5.dp))
        SimpleProgressBar(
            progress = stats.accuracy.toFloat(),
            progressColor = if (stats.accuracy >= 0.6) LocalLintiPalette.current.success else LocalLintiPalette.current.error,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "得分 ${formatScore(stats.score)} / ${formatScore(stats.maxScore)} · " +
                "错误 ${stats.wrong} · 未做 ${stats.unanswered}",
            style = MaterialTheme.typography.labelSmall,
            color = LocalLintiPalette.current.textSecondary,
        )
    }
}
