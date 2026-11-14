package com.xjb.linti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.ExamPaperQuestion
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.matchesCurrentQuestion
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.AiExplanationSection
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.SimpleProgressBar
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.formatDateTime
import com.xjb.linti.ui.formatScore
import com.xjb.linti.ui.components.QuestionImages
import com.xjb.linti.ui.theme.LocalLintiPalette
import kotlinx.coroutines.launch

// 回看内容和判分来自交卷快照；仅 AI 解析入口核对实时题库，避免将新题解析贴在旧题上。
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamPaperReviewScreen(
    viewModel: LintiViewModel,
    sessionId: Long,
    initialWrongOnly: Boolean,
    onBack: () -> Unit,
) {
    DisposableEffect(viewModel) {
        viewModel.setExamPaperReviewVisible(true)
        onDispose { viewModel.setExamPaperReviewVisible(false) }
    }
    val state by viewModel.examPaperReview.collectAsStateWithLifecycle()
    LaunchedEffect(sessionId) { viewModel.loadExamPaperReview(sessionId) }
    val current = state.takeIf { it.sessionId == sessionId }
    val review = current?.review
    val fontScaleState by viewModel.fontScale.collectAsStateWithLifecycle()
    val fontScale = fontScaleState.factor

    var wrongOnly by rememberSaveable { mutableStateOf(initialWrongOnly) }
    val visible = when {
        review == null -> emptyList()
        wrongOnly -> review.wrongQuestions
        else -> review.questions
    }
    val pagerState = rememberPagerState(initialPage = 0) { visible.size }
    // 切换筛选从头看起；本场序号始终按整卷计，不因筛选重新编号
    LaunchedEffect(wrongOnly, sessionId) {
        if (pagerState.currentPage > 0) pagerState.scrollToPage(0)
    }
    val scope = rememberCoroutineScope()
    var showJumpSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LintiPageHeader(
            title = "试卷回看",
            subtitle = review?.let { "${it.bankName} · 模拟考试" },
            onBack = onBack,
            actions = {
                val data = review
                if (data != null) {
                    StatusPill(
                        text = "交卷 ${formatDateTime(data.submittedAt)}",
                        color = LocalLintiPalette.current.primary,
                        background = LocalLintiPalette.current.primaryPale,
                    )
                }
            },
        )
        when {
            current == null || current.loading -> {
                ReviewStatusCard(title = "正在载入")
            }
            current.failed -> {
                ReviewStatusCard(
                    title = "试卷读取失败",
                    message = "原卷快照缺失或损坏，请重试。",
                    action = {
                        OutlinedActionButton(
                            text = "重试",
                            onClick = { viewModel.loadExamPaperReview(sessionId) },
                        )
                    },
                )
            }
            review == null -> {
                ReviewStatusCard(
                    title = "没有原卷",
                    message = "这场考试没有保存原卷快照。",
                    action = { OutlinedActionButton(text = "返回", onClick = onBack) },
                )
            }
            visible.isEmpty() -> {
                if (wrongOnly) {
                    ReviewStatusCard(
                        title = "本场无错题",
                        action = {
                            OutlinedActionButton(text = "看全部试题", onClick = { wrongOnly = false })
                        },
                    )
                } else {
                    ReviewStatusCard(title = "原卷没有题目")
                }
            }
            else -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ReviewFilterTab(
                        text = "全部试题 ${review.questions.size}",
                        selected = !wrongOnly,
                        onClick = { wrongOnly = false },
                    )
                    ReviewFilterTab(
                        text = "本场错题 ${review.wrongQuestions.size}",
                        selected = wrongOnly,
                        onClick = { wrongOnly = true },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val page = pagerState.currentPage.coerceIn(0, visible.lastIndex)
                    Text(
                        text = "${page + 1} / ${visible.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalLintiPalette.current.textSecondary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(10.dp))
                    SimpleProgressBar(
                        progress = (page + 1f) / visible.size.coerceAtLeast(1),
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    key = { visible[it].index },
                ) { page ->
                    ExamPaperQuestionPage(
                        question = visible[page],
                        totalCount = review.questions.size,
                        fontScale = fontScale,
                        viewModel = viewModel,
                    )
                }
                Surface(color = LocalLintiPalette.current.surface, shadowElevation = 8.dp) {
                    Column {
                        ThinDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedActionButton(
                                text = "上一题",
                                enabled = pagerState.currentPage > 0,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                },
                            )
                            OutlinedActionButton(
                                text = "题目跳转",
                                modifier = Modifier.weight(1f),
                                onClick = { showJumpSheet = true },
                            )
                            OutlinedActionButton(
                                text = "下一题",
                                enabled = pagerState.currentPage < visible.lastIndex,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showJumpSheet && review != null && visible.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showJumpSheet = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    text = if (wrongOnly) "本场错题（本场序号）" else "全部试题（本场序号）",
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalLintiPalette.current.textPrimary,
                )
                Spacer(Modifier.height(12.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    contentPadding = PaddingValues(bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(visible, key = { _, question -> question.index }) { index, question ->
                        AnswerGridCell(
                            number = question.index + 1,
                            current = index == pagerState.currentPage,
                            flagged = false,
                            outcome = question.outcome,
                            answered = question.userAnswer != null,
                            onClick = {
                                scope.launch {
                                    pagerState.scrollToPage(index)
                                    showJumpSheet = false
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewStatusCard(
    title: String,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    LintiCard(
        modifier = Modifier.padding(20.dp),
        containerColor = LocalLintiPalette.current.primaryPale,
    ) {
        EmptyState(title = title, message = message, action = action)
    }
}

@Composable
private fun ReviewFilterTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) palette.primaryPale else palette.surface)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) palette.primary else palette.line,
                shape = RoundedCornerShape(15.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) palette.primary else palette.textSecondary,
        )
    }
}

@Composable
private fun ExamPaperQuestionPage(
    question: ExamPaperQuestion,
    totalCount: Int,
    fontScale: Float,
    viewModel: LintiViewModel,
) {
    val palette = LocalLintiPalette.current
    val current by produceState<Pair<Boolean, Question?>>(false to null, question.questionId, viewModel) {
        viewModel.observeQuestionForExplanation(question.questionId).collect { value = true to it }
    }
    var showLatest by remember(question.questionId) { mutableStateOf(false) }
    val userLetters = AnswerNormalizer.lettersIn(question.userAnswer)
    val correctLetters = AnswerNormalizer.lettersIn(question.correctAnswer)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(text = question.type.label, color = palette.primary, background = palette.primaryPale)
            if (question.origin == com.xjb.linti.domain.QuestionOrigin.AI_GENERATED) {
                StatusPill("AI 出题", color = palette.aiAccent, background = palette.aiSoft)
            }
            val outcomeStyle = examPaperOutcomeStyle(question.outcome)
            StatusPill(
                text = outcomeStyle.first,
                color = outcomeStyle.second,
                background = outcomeStyle.third,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "第 ${question.index + 1} 题 / 共 $totalCount 题",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
                maxLines = 1,
            )
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
                        selected = option.label.uppercase() in userLetters,
                        multiSelect = question.type == QuestionType.MULTI,
                        reveal = true,
                        correct = option.label.uppercase() in correctLetters,
                        fontScale = fontScale,
                        onClick = {},
                    )
                }
            }

            QuestionType.JUDGE -> {
                val correctIsTrue = AnswerNormalizer.normalizeJudge(question.correctAnswer) == "true"
                QuizChoiceRow(
                    badge = "A",
                    text = "正确",
                    selected = question.userAnswer == "true",
                    reveal = true,
                    correct = correctIsTrue,
                    fontScale = fontScale,
                    onClick = {},
                )
                QuizChoiceRow(
                    badge = "B",
                    text = "错误",
                    selected = question.userAnswer == "false",
                    reveal = true,
                    correct = !correctIsTrue,
                    fontScale = fontScale,
                    onClick = {},
                )
            }

            QuestionType.FILL, QuestionType.ESSAY -> Unit
        }
        ExamPaperAnswerSummary(question)
        question.explanation?.let { explanation ->
            SectionHeader(title = "解析")
            Text(
                text = explanation,
                style = scaledTextStyle(MaterialTheme.typography.bodyMedium, fontScale),
                color = palette.textSecondary,
            )
        }
        val latest = current.second
        if (current.first && latest != null && !question.matchesCurrentQuestion(latest)) {
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("题目已修改", color = palette.warning, style = MaterialTheme.typography.labelMedium)
                    OutlinedActionButton("AI 解析", onClick = { showLatest = true }, modifier = Modifier.testTag("ai-review-latest"))
                }
            }
        } else if (current.first) {
            AiExplanationSection(question.questionId, viewModel)
        }
    }
    if (showLatest) QuestionExplanationDetail(question.questionId, viewModel) { showLatest = false }
}

@Composable
private fun ExamPaperAnswerSummary(question: ExamPaperQuestion) {
    val palette = LocalLintiPalette.current
    LintiCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExamPaperSummaryRow(
                label = "我的作答",
                value = examPaperUserAnswerText(question),
                valueColor = if (question.userAnswer == null) palette.textSecondary else palette.textPrimary,
            )
            ExamPaperSummaryRow(
                label = "正确答案",
                value = examPaperCorrectAnswerText(question),
                valueColor = palette.success,
            )
            ExamPaperSummaryRow(
                label = "得分",
                value = "${formatScore(question.score)} / ${formatScore(question.maxScore)} 分",
                valueColor = palette.primary,
            )
        }
    }
}

@Composable
private fun ExamPaperSummaryRow(
    label: String,
    value: String,
    valueColor: Color,
) {
    val palette = LocalLintiPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary,
            modifier = Modifier.width(76.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            modifier = Modifier.weight(1f),
        )
    }
}

// 判分状态以文字说明为主，颜色只是辅助（读屏也能区分正确/错误/未做）
@Composable
private fun examPaperOutcomeStyle(outcome: QuestionOutcome): Triple<String, Color, Color> {
    val palette = LocalLintiPalette.current
    return when (outcome) {
        QuestionOutcome.CORRECT -> Triple("正确", palette.success, palette.successSoft)
        QuestionOutcome.WRONG -> Triple("错误", palette.error, palette.errorSoft)
        QuestionOutcome.UNANSWERED -> Triple("未做", palette.textSecondary, palette.surface)
    }
}

private fun examPaperUserAnswerText(question: ExamPaperQuestion): String {
    if (question.userAnswer.isNullOrBlank()) return "未做"
    return when (question.type) {
        QuestionType.JUDGE -> when (AnswerNormalizer.normalizeJudge(question.userAnswer)) {
            "true" -> "正确"
            "false" -> "错误"
            else -> question.userAnswer.orEmpty()
        }
        QuestionType.FILL -> question.userAnswer.orEmpty()
        QuestionType.ESSAY -> com.xjb.linti.domain.EssayAnswer.text(question.userAnswer.orEmpty()).ifBlank { "（未输入）" }
        else -> AnswerNormalizer.lettersIn(question.userAnswer).sorted().joinToString(" ")
    }
}

private fun examPaperCorrectAnswerText(question: ExamPaperQuestion): String =
    when (question.type) {
        QuestionType.JUDGE -> when (AnswerNormalizer.normalizeJudge(question.correctAnswer)) {
            "true" -> "正确"
            "false" -> "错误"
            else -> question.correctAnswer
        }
        QuestionType.FILL, QuestionType.ESSAY -> question.correctAnswer
        else -> AnswerNormalizer.lettersIn(question.correctAnswer).sorted().joinToString(" ")
    }
