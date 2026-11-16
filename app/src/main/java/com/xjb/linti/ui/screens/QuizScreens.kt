package com.xjb.linti.ui.screens

import android.annotation.SuppressLint
import com.xjb.linti.ui.components.LintiChoiceCard
import com.xjb.linti.ui.components.LintiConfirmationSheet
import com.xjb.linti.ui.components.QuestionImages
import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.text.font.FontWeight
import com.xjb.linti.ui.theme.LintiMotion
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizQuestion
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiFilterChip
import com.xjb.linti.ui.components.AiExplanationSection
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SimpleProgressBar
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.formatPercent
import com.xjb.linti.ui.formatScore
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextStyle
import com.xjb.linti.domain.FontScale
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
    onWrongBook: (Long?) -> Unit = {},
    onEditQuestion: (Long) -> Unit = {},
) {
    val quiz by viewModel.quizState.collectAsStateWithLifecycle()
    val submittingToken by viewModel.submittingExamToken.collectAsStateWithLifecycle()
    val fontScale by viewModel.fontScale.collectAsStateWithLifecycle()
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
    var noteOriginal by remember { mutableStateOf("") }
    var showNoteDiscardConfirm by remember { mutableStateOf(false) }
    var noteRefresh by remember { mutableIntStateOf(0) }
    val isSubmitting = submittingToken == quiz.sessionToken
    var isLeaving by remember { mutableStateOf(false) }

    fun handleBack() {
        if (quiz.mode == PracticeMode.EXAM) {
            showExitConfirm = true
        } else {
            viewModel.leaveQuiz()
            onBack()
        }
    }
    BackHandler(onBack = { handleBack() })

    val haptic = LocalHapticFeedback.current
    // 每秒 tick 只留在 QuizHeader 内部重组，避免整屏（含 Pager）每秒重组
    var countdownWarning by remember(quiz.sessionToken) { mutableStateOf(false) }
    LaunchedEffect(noteDialogFor, noteRefresh) {
        val questionId = noteDialogFor
        noteText = if (questionId == null) "" else viewModel.loadNote(questionId)
        noteOriginal = noteText
    }
    fun requestCloseNote() {
        if (noteText != noteOriginal) showNoteDiscardConfirm = true else noteDialogFor = null
    }

    if (!quiz.active || quiz.currentQuestion == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(modifier = Modifier.padding(horizontal = 16.dp), title = "答题", onBack = onBack)
            LintiCard(
                modifier = Modifier.padding(20.dp),
                containerColor = palette.primaryPale,
            ) {
                EmptyState(
                    title = "当前没有进行中的答题",
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
    var lastCommittedCount by remember(quiz.sessionToken) {
        mutableIntStateOf(quiz.committedIds.size)
    }
    LaunchedEffect(quiz.committedIds.size) {
        val count = quiz.committedIds.size
        val indexAtCommit = quiz.currentIndex
        if (count > lastCommittedCount && quiz.mode != PracticeMode.EXAM) {
            val current = quiz.currentQuestion
            if (current != null && current.questionId in quiz.committedIds) {
                when (viewModel.outcomeFor(current.questionId)) {
                    QuestionOutcome.CORRECT -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    QuestionOutcome.WRONG -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    else -> Unit
                }
                if (autoNext &&
                    viewModel.outcomeFor(current.questionId) == QuestionOutcome.CORRECT &&
                    indexAtCommit < questions.lastIndex
                ) {
                    delay(600L)
                    if (pagerState.settledPage == indexAtCommit) {
                        viewModel.goToQuestion(indexAtCommit + 1)
                    }
                }
            }
        }
        lastCommittedCount = count
    }

    val correctCount = quiz.committedIds.count {
        it !in quiz.answerViewedIds && viewModel.outcomeFor(it) == QuestionOutcome.CORRECT
    }
    val wrongCount = quiz.committedIds.count { viewModel.outcomeFor(it) == QuestionOutcome.WRONG }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        QuizHeader(
            quiz = quiz,
            viewModel = viewModel,
            onBack = { handleBack() },
            onToggleBookmark = viewModel::toggleCurrentBookmark,
            onOpenNote = { noteDialogFor = quiz.currentQuestion?.questionId },
            onOpenSettings = { showSettings = true },
            onWarningChange = { countdownWarning = it },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (quiz.canSwitchRecite) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.surfaceContainer)
                            .border(1.dp, palette.line.copy(alpha = 0.7f), CircleShape)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        QuizModeTab(
                            label = "答题",
                            selected = !quiz.reciting,
                            onClick = { viewModel.setReciting(false) },
                        )
                        QuizModeTab(
                            label = "背题",
                            selected = quiz.reciting,
                            onClick = { viewModel.setReciting(true) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    text = "${quiz.currentIndex + 1} / ${questions.size}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (quiz.mode == PracticeMode.EXAM) {
                        "已答 ${quiz.answeredCount}"
                    } else {
                        "已做 ${quiz.committedIds.size}"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (quiz.mode == PracticeMode.EXAM && countdownWarning) {
                        palette.warning
                    } else {
                        palette.textSecondary
                    },
                    maxLines = 1,
                )
                if (!quiz.canSwitchRecite) {
                    Spacer(Modifier.weight(1f))
                }
            }
            SimpleProgressBar(
                progress = (quiz.currentIndex + 1f) / questions.size.coerceAtLeast(1),
                height = 5.dp,
                progressColor = if (quiz.mode == PracticeMode.EXAM && countdownWarning) {
                    palette.warning
                } else {
                    palette.primary
                },
                progressDescription = "答题进度 ${quiz.currentIndex + 1}/${questions.size}",
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
                totalCount = quiz.committedIds.size,
                onWrongBook = { onWrongBook(quiz.bankId) },
                onBackToBank = { handleBack() },
            )
        }
        Surface(color = palette.surface, shadowElevation = 0.dp) {
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
                            enabled = !isSubmitting,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        val hasPrev = quiz.currentIndex > 0
                        val hasNext = quiz.currentIndex < questions.lastIndex
                        Row(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(CircleShape)
                                .clickable(enabled = hasPrev) { viewModel.goToQuestion(quiz.currentIndex - 1) }
                                .padding(horizontal = 10.dp)
                                .semantics {
                                    contentDescription = "上一题"
                                    role = Role.Button
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.ChevronLeft,
                                contentDescription = null,
                                tint = if (hasPrev) palette.textPrimary else palette.textTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "上一题",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasPrev) palette.textPrimary else palette.textTertiary,
                            )
                        }
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, tint = palette.success, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "对 $correctCount",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = palette.success,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Outlined.Close, contentDescription = null, tint = palette.error, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "错 $wrongCount",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = palette.error,
                                maxLines = 1,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(CircleShape)
                                .background(palette.surfaceContainer)
                                .clickable(onClick = onAnswerSheet)
                                .padding(horizontal = 10.dp)
                                .semantics {
                                    contentDescription = "答题卡，已做 ${quiz.committedIds.size}/${questions.size}"
                                    role = Role.Button
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.GridView, contentDescription = null, tint = palette.primary, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${quiz.committedIds.size}/${questions.size}",
                                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                color = palette.primary,
                                maxLines = 1,
                            )
                        }
                        Row(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(CircleShape)
                                .background(if (hasNext) palette.primarySoft else palette.surfaceContainer)
                                .clickable(enabled = hasNext) { viewModel.goToQuestion(quiz.currentIndex + 1) }
                                .padding(horizontal = 10.dp)
                                .semantics {
                                    contentDescription = "下一题"
                                    role = Role.Button
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "下一题",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (hasNext) palette.primary else palette.textTertiary,
                            )
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = if (hasNext) palette.primary else palette.textTertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
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
                onAutoNext = viewModel::setAutoNextOnCorrect,
                onDismiss = { showSettings = false },
            )
        }
    }

    if (showRetryConfirm) {
        LintiConfirmationSheet("重练本题？", "将清除本题本次作答与提交状态，历史作答记录不会删除。", "重练",
            onConfirm = {
                quiz.currentQuestion?.questionId?.let { viewModel.retryQuestion(it) }
                showRetryConfirm = false
            }, onDismiss = { showRetryConfirm = false })
    }
    if (showSubmitConfirm) {
        LintiConfirmationSheet("确认交卷？", "已答 ${quiz.answeredCount} / ${questions.size} 题，未答 ${questions.size - quiz.answeredCount} 题。交卷后不可修改。",
            if (isSubmitting) "正在判分…" else "确认交卷", confirmEnabled = !isSubmitting, dismissLabel = "继续作答",
            onConfirm = { showSubmitConfirm = false; viewModel.submitQuiz(quiz.sessionToken) },
            onDismiss = { showSubmitConfirm = false })
    }
    if (showExitConfirm) {
        LintiConfirmationSheet("退出考试？", "退出后保留进度，考试继续计时。", "退出", confirmEnabled = !isLeaving && !isSubmitting,
            dismissLabel = "继续作答", onDismiss = { showExitConfirm = false }, onConfirm = {
                scope.launch {
                    isLeaving = true
                    try {
                        if (viewModel.leaveExam()) { showExitConfirm = false; onBack() }
                    } finally { isLeaving = false }
                }
            })
    }

    if (noteDialogFor != null) {
        val targetId = noteDialogFor ?: return
        AlertDialog(
            onDismissRequest = { requestCloseNote() },
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
                TextButton(onClick = { requestCloseNote() }) { Text("取消") }
            },
        )
    }

    if (showNoteDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showNoteDiscardConfirm = false },
            title = { Text("放弃笔记修改？") },
            text = { Text("编辑的内容尚未保存，放弃后将丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    showNoteDiscardConfirm = false
                    noteDialogFor = null
                }) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDiscardConfirm = false }) { Text("继续编辑") }
            },
        )
    }
}

@Composable
private fun QuizModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) palette.primary else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) palette.onPrimary else palette.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun QuizHeader(
    quiz: QuizUiState,
    viewModel: LintiViewModel,
    onBack: () -> Unit,
    onToggleBookmark: () -> Unit,
    onOpenNote: () -> Unit,
    onOpenSettings: () -> Unit,
    onWarningChange: (Boolean) -> Unit,
) {
    val palette = LocalLintiPalette.current
    val timedExam = quiz.mode == PracticeMode.EXAM
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(quiz.sessionToken, timedExam) {
        if (!timedExam) return@LaunchedEffect
        while (isActive) {
            delay(1000L)
            tick++
        }
    }
    val countdown = remember(tick, quiz.sessionToken, quiz.startedAt) { viewModel.countdownState() }
    var warnedFiveMinutes by remember(quiz.sessionToken) { mutableStateOf(false) }
    LaunchedEffect(countdown.warning) {
        onWarningChange(countdown.warning)
        if (timedExam && countdown.warning && !warnedFiveMinutes) {
            warnedFiveMinutes = true
            viewModel.showMessage("距离交卷还有 5 分钟")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.surface)
                .border(1.dp, palette.line.copy(alpha = 0.65f), CircleShape),
        ) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = palette.textPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (timedExam && !countdown.unlimited) countdown.display else quiz.mode.label,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = if (timedExam && countdown.warning && !countdown.unlimited) palette.warning else palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = quiz.bankName,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleBookmark) {
            Icon(
                imageVector = if (quiz.currentBookmarked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (quiz.currentBookmarked) "取消收藏" else "收藏",
                tint = if (quiz.currentBookmarked) palette.primary else palette.textSecondary,
            )
        }
        IconButton(onClick = onOpenNote) {
            Icon(
                imageVector = Icons.Outlined.EditNote,
                contentDescription = "笔记",
                tint = palette.aiAccent,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.MoreHoriz,
                contentDescription = "更多设置",
                tint = palette.textSecondary,
            )
        }
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
    totalCount: Int,
    onWrongBook: () -> Unit,
    onBackToBank: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    val reveal = quiz.revealed(question.questionId)
    val currentAnswer = quiz.answers[question.questionId].orEmpty()
    val outcome = if (reveal && question.questionId in quiz.committedIds) {
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
            if (question.origin == com.xjb.linti.domain.QuestionOrigin.AI_GENERATED) {
                StatusPill("AI 出题", color = palette.aiAccent, background = palette.aiSoft)
            }
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
        QuestionImages(question.images)
        when (question.type) {
            QuestionType.SINGLE, QuestionType.MULTI -> {
                question.options.forEach { option ->
                    QuizChoiceRow(
                        badge = option.label,
                        text = option.text,
                        selected = option.label.uppercase() in AnswerNormalizer.lettersIn(currentAnswer),
                        multiSelect = question.type == QuestionType.MULTI,
                        reveal = reveal,
                        correct = option.label.uppercase() in AnswerNormalizer.lettersIn(question.correctAnswer),
                        fontScale = fontScale,
                        onClick = { viewModel.selectAnswer(question.questionId, option.label) },
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
                    label = { Text(if (question.acceptedAnswers.size > 1) "填写答案（每空用分号分隔）" else "填写答案") },
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
            QuestionType.ESSAY -> {
                val assessed = com.xjb.linti.domain.EssayAnswer.isAssessed(currentAnswer)
                val shown = question.questionId in quiz.essayRevealedIds || assessed || (quiz.reciting && quiz.canSwitchRecite)
                EssayAnswerContent(
                    answer = com.xjb.linti.domain.EssayAnswer.text(currentAnswer),
                    reference = question.correctAnswer,
                    shown = shown,
                    assessed = assessed,
                    correct = assessed && com.xjb.linti.domain.EssayAnswer.isCorrect(currentAnswer),
                    reciting = quiz.reciting && quiz.canSwitchRecite,
                    fontScale = fontScale,
                    onAnswer = { viewModel.setFillAnswer(question.questionId, it) },
                    onReveal = { viewModel.revealEssay(question.questionId) },
                    onAssess = { viewModel.assessEssay(question.questionId, it) },
                )
            }
        }
        AnimatedVisibility(
            visible = reveal && question.type != QuestionType.ESSAY,
            enter = fadeIn(tween(LintiMotion.TransitionMillis)) + expandVertically(tween(LintiMotion.TransitionMillis)),
            exit = ExitTransition.None,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AnswerFeedback(
                    question = question,
                    outcome = outcome,
                    userAnswer = currentAnswer,
                    viewModel = viewModel,
                    fontScale = fontScale,
                    noteRefresh = noteRefresh,
                    onOpenNote = onOpenNote,
                )
                if (quiz.mode != PracticeMode.EXAM) AiExplanationSection(question.questionId, viewModel)
            }
        }
        if (quiz.mode != PracticeMode.EXAM && allCommitted && isLast) {
            RoundCompleteCard(
                correct = correctCount,
                wrong = wrongCount,
                total = totalCount,
                onWrongBook = onWrongBook,
                onBackToBank = onBackToBank,
            )
        }
    }
}

@Composable
internal fun EssayAnswerContent(
    answer: String, reference: String, shown: Boolean, assessed: Boolean, reciting: Boolean, correct: Boolean = false,
    fontScale: Float = 1f,
    onAnswer: (String) -> Unit, onReveal: () -> Unit, onAssess: (Boolean) -> Unit,
) {
    val palette = LocalLintiPalette.current
    if (!reciting) {
        OutlinedTextField(value = answer, onValueChange = onAnswer, label = { Text("你的答案（可不填）") },
            readOnly = shown, minLines = 2, modifier = Modifier.fillMaxWidth(),
            textStyle = scaledTextStyle(MaterialTheme.typography.bodyMedium, fontScale))
    }
    if (!shown) {
        PrimaryActionButton(text = "看参考答案", onClick = onReveal, modifier = Modifier.fillMaxWidth())
    } else {
        Text(
            text = "参考答案：$reference",
            style = scaledTextStyle(MaterialTheme.typography.bodyMedium, fontScale),
            color = palette.textPrimary,
        )
        if (!assessed && !reciting) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onAssess(false) }, modifier = Modifier.weight(1f)) { Text("没答对") }
                Button(onClick = { onAssess(true) }, modifier = Modifier.weight(1f)) { Text("答对") }
            }
        } else if (assessed) {
            Text(
                text = if (correct) "已自评：答对" else "已自评：没答对",
                style = scaledTextStyle(MaterialTheme.typography.bodyMedium, fontScale),
                color = if (correct) palette.success else palette.error,
            )
        }
    }
}

@Composable
internal fun QuizChoiceRow(
    badge: String,
    text: String,
    selected: Boolean,
    multiSelect: Boolean = false,
    reveal: Boolean,
    correct: Boolean,
    fontScale: Float,
    onClick: () -> Unit,
) {
    LintiChoiceCard(
        label = badge, text = text, selected = selected, multiSelect = multiSelect,
        reveal = reveal, correct = correct,
        textStyle = scaledTextStyle(MaterialTheme.typography.bodyMedium, fontScale), onClick = onClick,
    )
}

// Kotlin 2.0 lint 误报：value 已在 producer 内赋值（含 collect 回调）
@SuppressLint("ProduceStateDoesNotAssignValue")
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
        else -> palette.surfaceContainer
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
                    color = when (outcome) {
                        QuestionOutcome.CORRECT -> palette.success
                        QuestionOutcome.WRONG -> palette.error
                        QuestionOutcome.UNANSWERED -> palette.textSecondary
                    },
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
                text = "解析",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
            )
            Text(
                text = if (question.explanation.isBlank()) "暂无解析" else question.explanation,
                style = scaledTextStyle(MaterialTheme.typography.bodySmall, fontScale),
                color = palette.textPrimary,
            )
            ThinDivider()
            val notePreview = note.lines().filter { it.isNotBlank() }.take(3).joinToString("\n")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "笔记",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenNote) {
                    Text(if (notePreview.isBlank()) "添加" else "编辑")
                }
            }
            if (notePreview.isNotBlank()) {
                Text(
                    text = notePreview,
                    style = scaledTextStyle(MaterialTheme.typography.bodySmall, fontScale),
                    color = palette.textPrimary,
                    maxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun RoundCompleteCard(
    correct: Int,
    wrong: Int,
    total: Int,
    onWrongBook: () -> Unit,
    onBackToBank: () -> Unit,
) {
    val palette = LocalLintiPalette.current
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
    autoNext: Boolean,
    onEditQuestion: (Long) -> Unit,
    onRetry: () -> Unit,
    onToggleFlag: () -> Unit,
    onFontScale: (FontScale) -> Unit,
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
            .navigationBarsPadding()
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
                LintiFilterChip(
                    selected = fontScale == value,
                    onClick = { onFontScale(value) },
                    label = when (value) {
                        FontScale.SMALL -> "小"
                        FontScale.STANDARD -> "标准"
                        FontScale.LARGE -> "大"
                        FontScale.EXTRA_LARGE -> "较大"
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

internal fun scaledTextStyle(style: TextStyle, factor: Float): TextStyle =
    if (factor == 1f) {
        style
    } else {
        style.copy(
            fontSize = style.fontSize * factor,
            lineHeight = style.lineHeight * factor,
        )
    }

