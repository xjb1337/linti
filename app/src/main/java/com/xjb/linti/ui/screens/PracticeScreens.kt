package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.BankSummary
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.WrongQuestionItem
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.StudySummary
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.FeatureTile
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.MetricItem
import com.xjb.linti.ui.components.MetricStrip
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.SimpleProgressBar
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.formatDateTime
import com.xjb.linti.ui.formatPercent
import com.xjb.linti.ui.formatRelativeDay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@Composable
fun PracticeHubScreen(
    viewModel: LintiViewModel,
    onOpenBank: (Long) -> Unit,
    onQuiz: () -> Unit,
    onExamSetup: (Long) -> Unit,
    onExamHistory: () -> Unit,
    onWrongBook: () -> Unit,
    onFavorites: () -> Unit,
    onFlashcards: () -> Unit,
    onRecite: () -> Unit,
) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val wrongQuestions by viewModel.wrongQuestions.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var summary by remember { mutableStateOf<StudySummary?>(null) }
    val lastBank = remember(banks) {
        banks.maxWithOrNull(
            compareBy<BankSummary> { it.lastOpenedAt ?: Long.MIN_VALUE }
                .thenBy { it.updatedAt },
        )
    }
    var showRestartConfirm by remember { mutableStateOf(false) }
    val roundComplete = lastBank?.let {
        it.questionCount > 0 && it.sequenceAnsweredCount >= it.questionCount
    } ?: false

    LaunchedEffect(banks.size, wrongQuestions.size, banks.sumOf { it.sequenceAnsweredCount }) {
        summary = viewModel.studySummary()
    }

    fun continuePractice() {
        val bank = lastBank
        if (bank == null) {
            viewModel.showMessage("还没有题库，请先在首页导入或载入示例数据")
            return
        }
        if (roundComplete) {
            showRestartConfirm = true
            return
        }
        scope.launch {
            val result = viewModel.startSequenceQuiz(bank.id)
            if (result.started) onQuiz()
            else viewModel.showMessage(result.message ?: "无法继续练习")
        }
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LintiPageHeader(
                title = "练习中枢",
                actions = {
                    StatusPill("每天一点进步", color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
                },
            )
        }
        item {
            LintiCard(
                containerColor = LocalLintiPalette.current.primaryPale,
                borderColor = LocalLintiPalette.current.primarySoft,
                onClick = { lastBank?.let { onOpenBank(it.id) } },
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill("继续上次", color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primarySoft)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = formatRelativeDay(lastBank?.lastOpenedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = lastBank?.name ?: "还没有题库",
                        style = MaterialTheme.typography.titleLarge,
                        color = LocalLintiPalette.current.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "顺序练习",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = lastBank?.let {
                                "${it.sequenceAnsweredCount}/${it.questionCount}"
                            } ?: "0/0",
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalLintiPalette.current.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    SimpleProgressBar(
                        progress = lastBank?.let {
                            if (it.questionCount == 0) 0f
                            else it.sequenceAnsweredCount.toFloat() / it.questionCount
                        } ?: 0f,
                    )
                    Spacer(Modifier.height(13.dp))
                    PrimaryActionButton(
                        text = when {
                            lastBank == null -> "载入题库后开始"
                            roundComplete -> "重新开始一轮"
                            else -> "继续练习"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.PlayArrow,
                        onClick = ::continuePractice,
                    )
                }
            }
        }
        item {
            LintiCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "今日学习小结",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalLintiPalette.current.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "按自己的节奏来",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryNumber(
                            value = summary?.todayAnswered?.toString() ?: "—",
                            label = "今日已练",
                            color = LocalLintiPalette.current.primary,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryNumber(
                            value = summary?.pendingWrong?.toString() ?: "—",
                            label = "待复习错题",
                            color = LocalLintiPalette.current.error,
                            modifier = Modifier.weight(1f),
                        )
                        SummaryNumber(
                            value = summary?.accuracy?.let(::formatPercent) ?: "—",
                            label = "累计正确率",
                            color = LocalLintiPalette.current.success,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        item {
            SectionHeader(title = "选择一种练习方式", trailing = "全部免费")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FeatureTile(
                    icon = Icons.Outlined.FactCheck,
                    label = "顺序练习",
                    caption = lastBank?.let { "${it.sequenceAnsweredCount}/${it.questionCount}" } ?: "选择题库",
                    onClick = ::continuePractice,
                    modifier = Modifier.weight(1f),
                )
                FeatureTile(
                    icon = Icons.Outlined.Quiz,
                    label = "模拟考试",
                    caption = "自定义组卷",
                    onClick = {
                        val bank = lastBank
                        if (bank == null) {
                            viewModel.showMessage("请先导入题库")
                        } else {
                            onExamSetup(bank.id)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                FeatureTile(
                    icon = Icons.Outlined.ErrorOutline,
                    label = "错题本",
                    caption = "${wrongQuestions.count { it.wrongItem.pendingReview }} 待复习",
                    onClick = onWrongBook,
                    modifier = Modifier.weight(1f),
                    iconColor = LocalLintiPalette.current.error,
                    iconBackground = LocalLintiPalette.current.errorSoft,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FeatureTile(
                    icon = Icons.Outlined.TrendingUp,
                    label = "收藏练习",
                    caption = "本机收藏",
                    onClick = onFavorites,
                    modifier = Modifier.weight(1f),
                )
                FeatureTile(
                    icon = Icons.Outlined.Style,
                    label = "试题闪卡",
                    caption = "翻看答案",
                    onClick = onFlashcards,
                    modifier = Modifier.weight(1f),
                    iconColor = LocalLintiPalette.current.aiAccent,
                    iconBackground = LocalLintiPalette.current.aiSoft,
                )
                FeatureTile(
                    icon = Icons.Outlined.AutoStories,
                    label = "背题模式",
                    caption = "题答同屏",
                    onClick = onRecite,
                    modifier = Modifier.weight(1f),
                    iconColor = LocalLintiPalette.current.warning,
                    iconBackground = LocalLintiPalette.current.warningSoft,
                )
            }
        }
        item {
            LintiCard(onClick = onExamHistory) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(LocalLintiPalette.current.primaryPale),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.History,
                            contentDescription = null,
                            tint = LocalLintiPalette.current.primary,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Text(
                        text = "模考历史",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalLintiPalette.current.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = LocalLintiPalette.current.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        item {
            LintiCard(containerColor = LocalLintiPalette.current.background, borderColor = LocalLintiPalette.current.line) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "累计答题 ${summary?.totalAnswered ?: 0} 题 · 已完成 ${summary?.sessionCount ?: 0} 次练习",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalLintiPalette.current.textSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "连续 ${summary?.streakDays ?: 0} 天",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalLintiPalette.current.primary,
                    )
                }
            }
        }
    }
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("重新开始一轮？") },
            text = { Text("将从第一题开始，本轮进度清零；历史作答记录不会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    val id = lastBank?.id
                    if (id != null) {
                        scope.launch {
                            viewModel.restartSequenceRound(id)
                            val result = viewModel.startSequenceQuiz(id)
                            if (result.started) onQuiz()
                            else viewModel.showMessage(result.message ?: "无法开始练习")
                        }
                    }
                }) { Text("重新开始") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SummaryNumber(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = LocalLintiPalette.current.textSecondary,
        )
    }
}

private enum class WrongTimeFilter(val label: String) {
    ALL("全部时间"),
    WEEK("近七天"),
}

@Composable
fun WrongBookScreen(
    viewModel: LintiViewModel,
    initialBankId: Long?,
    onBack: () -> Unit,
    onQuiz: () -> Unit,
) {
    val wrongQuestions by viewModel.wrongQuestions.collectAsStateWithLifecycle()
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var bankFilter by remember(initialBankId) { mutableStateOf(initialBankId) }
    var timeFilter by remember { mutableStateOf(WrongTimeFilter.ALL) }
    val weekStart = remember {
        System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
    }
    val filtered = wrongQuestions.filter { item ->
        (bankFilter == null || item.wrongItem.bankId == bankFilter) &&
            (timeFilter == WrongTimeFilter.ALL || item.wrongItem.lastWrongAt >= weekStart)
    }
    val selectedBankName = banks.firstOrNull { it.id == bankFilter }?.name

    fun startWrong(questionId: Long? = null) {
        scope.launch {
            val result = viewModel.startWrongQuiz(bankId = bankFilter, questionId = questionId)
            if (result.started) onQuiz()
            else viewModel.showMessage(result.message ?: "无法开始错题练习")
        }
    }

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            LintiPageHeader(
                title = "错题本",
                subtitle = selectedBankName ?: "跨题库复习",
                onBack = onBack,
                actions = {
                    StatusPill("返回练习中枢", color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
                },
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                FilterChip(
                    selected = bankFilter == null,
                    onClick = { bankFilter = null },
                    label = { Text("全部题库") },
                )
                banks.forEach { bank ->
                    FilterChip(
                        selected = bankFilter == bank.id,
                        onClick = { bankFilter = bank.id },
                        label = {
                            Text(
                                bank.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                WrongTimeFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = timeFilter == filter,
                        onClick = { timeFilter = filter },
                        label = { Text(filter.label) },
                    )
                }
                Spacer(Modifier.weight(1f))
                OutlinedActionButton(
                    text = "一键重练",
                    icon = Icons.Outlined.Refresh,
                    enabled = filtered.isNotEmpty(),
                    onClick = { startWrong() },
                )
            }
        }
        item {
            MetricStrip(
                items = listOf(
                    MetricItem("${filtered.size}", "错题", LocalLintiPalette.current.error),
                    MetricItem(
                        "${filtered.count { it.wrongItem.corrected }}",
                        "已订正",
                        LocalLintiPalette.current.success,
                    ),
                    MetricItem(
                        "${filtered.count { it.wrongItem.pendingReview }}",
                        "待复习",
                        LocalLintiPalette.current.primary,
                    ),
                ),
            )
        }
        if (filtered.isEmpty()) {
            item {
                LintiCard(containerColor = LocalLintiPalette.current.primaryPale) {
                    EmptyState(
                        title = "这个范围还没有错题",
                        message = "继续练习，答错的题会自动进入错题本。",
                        action = {
                            OutlinedActionButton(text = "返回练习中枢", onClick = onBack)
                        },
                    )
                }
            }
        } else {
            items(filtered, key = { it.wrongItem.questionId }) { item ->
                WrongRow(
                    item = item,
                    onPractice = {
                        startWrong(item.wrongItem.questionId)
                    },
                )
            }
        }
        item {
            Text(
                text = "答错自动进入错题本；错题练习中答对会标为已订正，再次答错则回到待复习。",
                style = MaterialTheme.typography.labelSmall,
                color = LocalLintiPalette.current.textSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun WrongRow(
    item: WrongQuestionItem,
    onPractice: () -> Unit,
) {
    LintiCard(shapeRadius = 15.dp, onClick = onPractice) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = item.type.label,
                    color = LocalLintiPalette.current.primary,
                    background = LocalLintiPalette.current.primaryPale,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = item.bankName,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(
                    text = if (item.wrongItem.pendingReview) "待复习" else "已订正",
                    color = if (item.wrongItem.pendingReview) LocalLintiPalette.current.error else LocalLintiPalette.current.success,
                    background = if (item.wrongItem.pendingReview) LocalLintiPalette.current.errorSoft else LocalLintiPalette.current.successSoft,
                )
            }
            Spacer(Modifier.height(7.dp))
            Text(
                text = item.sourceNumber.ifBlank { "—" } + ". " + item.stem,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalLintiPalette.current.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "答错 ${item.wrongItem.wrongCount} 次 · 上次 ${formatDateTime(item.wrongItem.lastWrongAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "立即练习",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalLintiPalette.current.primary,
                )
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = LocalLintiPalette.current.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
fun FlashcardsScreen(
    viewModel: LintiViewModel,
    bankId: Long?,
    onBack: () -> Unit,
) {
    val collection by viewModel.questionCollection.collectAsStateWithLifecycle()
    var index by remember { mutableIntStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    LaunchedEffect(bankId) {
        viewModel.loadBankQuestionCollection(bankId)
        index = 0
        showAnswer = false
    }
    val questions = collection?.questions.orEmpty()
    val question = questions.getOrNull(index)

    Column(modifier = Modifier.fillMaxSize()) {
        LintiPageHeader(
            title = "试题闪卡",
            subtitle = if (questions.isEmpty()) "轻量翻看" else "${index + 1} / ${questions.size}",
            onBack = onBack,
            actions = {
                StatusPill("题答分离", color = LocalLintiPalette.current.aiAccent, background = LocalLintiPalette.current.aiSoft)
            },
        )
        if (questions.isNotEmpty()) {
            SimpleProgressBar(
                progress = (index + 1f) / questions.size,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        if (question == null) {
            LintiCard(
                modifier = Modifier.padding(20.dp),
                containerColor = LocalLintiPalette.current.primaryPale,
            ) {
                EmptyState(
                    title = if (collection?.loading == true) "正在载入" else "暂无可翻看的题目",
                    message = "导入题库后即可使用闪卡。",
                    action = {
                        OutlinedActionButton(text = "返回", onClick = onBack)
                    },
                )
            }
            return
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LintiCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                containerColor = LocalLintiPalette.current.surface,
                borderColor = LocalLintiPalette.current.primarySoft,
                onClick = { showAnswer = !showAnswer },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    StatusPill(
                        text = question.type.label,
                        color = LocalLintiPalette.current.primary,
                        background = LocalLintiPalette.current.primaryPale,
                    )
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = question.stem,
                        style = MaterialTheme.typography.headlineMedium,
                        color = LocalLintiPalette.current.textPrimary,
                    )
                    if (!showAnswer) {
                        Spacer(Modifier.height(28.dp))
                        Text(
                            text = "点击卡片或下方按钮翻看答案",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    } else {
                        Spacer(Modifier.height(22.dp))
                        Text(
                            text = "答案：${displayQuestionAnswer(question)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalLintiPalette.current.success,
                        )
                        if (question.explanation.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = question.explanation,
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalLintiPalette.current.textSecondary,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedActionButton(
                    text = "上一张",
                    icon = Icons.Outlined.ChevronLeft,
                    modifier = Modifier.weight(1f),
                    enabled = index > 0,
                    onClick = {
                        if (index > 0) {
                            index--
                            showAnswer = false
                        }
                    },
                )
                PrimaryActionButton(
                    text = if (showAnswer) "重新出题" else "翻看答案",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (showAnswer) {
                            index = if (index < questions.lastIndex) index + 1 else 0
                            showAnswer = false
                        } else {
                            showAnswer = true
                        }
                    },
                )
                OutlinedActionButton(
                    text = "下一张",
                    icon = Icons.Outlined.ChevronRight,
                    modifier = Modifier.weight(1f),
                    enabled = index < questions.lastIndex,
                    onClick = {
                        if (index < questions.lastIndex) {
                            index++
                            showAnswer = false
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun ReciteScreen(
    viewModel: LintiViewModel,
    bankId: Long?,
    onBack: () -> Unit,
) {
    val collection by viewModel.questionCollection.collectAsStateWithLifecycle()
    LaunchedEffect(bankId) {
        viewModel.loadBankQuestionCollection(bankId)
    }
    val questions = collection?.questions.orEmpty()

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LintiPageHeader(
                title = "背题模式",
                subtitle = "${questions.size} 题 · 题答同屏",
                onBack = onBack,
                actions = {
                    StatusPill("无需作答", color = LocalLintiPalette.current.warning, background = LocalLintiPalette.current.warningSoft)
                },
            )
        }
        if (questions.isEmpty()) {
            item {
                LintiCard(containerColor = LocalLintiPalette.current.primaryPale) {
                    EmptyState(
                        title = if (collection?.loading == true) "正在载入" else "暂无可背诵题目",
                        message = "导入题库后，题干、选项与答案会在此同屏展示。",
                    )
                }
            }
        } else {
            items(questions, key = { "recite-${it.id}" }) { question ->
                ReciteCard(question)
            }
        }
    }
}

@Composable
private fun ReciteCard(question: Question) {
    LintiCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(question.type.label, color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "第 ${question.sourceNumber.ifBlank { "—" }} 题",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = question.stem,
                style = MaterialTheme.typography.bodyLarge,
                color = LocalLintiPalette.current.textPrimary,
            )
            if (question.options.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                question.options.forEach { option ->
                    Text(
                        text = "${option.label}. ${option.text}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLintiPalette.current.textPrimary,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            Spacer(Modifier.height(9.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(LocalLintiPalette.current.successSoft)
                    .padding(horizontal = 11.dp, vertical = 8.dp),
            ) {
                Column {
                    Text(
                        text = "答案：${displayQuestionAnswer(question)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalLintiPalette.current.success,
                    )
                    if (question.explanation.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = question.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

private fun displayQuestionAnswer(question: Question): String = when (question.type) {
    QuestionType.JUDGE -> when (question.answer) {
        "true" -> "正确"
        "false" -> "错误"
        else -> question.answer
    }
    else -> question.answer
}
