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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
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
import com.xjb.linti.domain.FlashcardSchedule
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

@Composable
fun PracticeHubScreen(
    viewModel: LintiViewModel,
    onOpenBank: (Long) -> Unit,
    onOpenHome: () -> Unit,
    onQuiz: () -> Unit,
    onExamSetup: (Long) -> Unit,
    onTopicPractice: () -> Unit,
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
            onOpenHome()
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
            LintiPageHeader(title = "练习")
        }
        item {
            LintiCard(
                containerColor = LocalLintiPalette.current.primaryPale,
                borderColor = LocalLintiPalette.current.primarySoft,
                onClick = { lastBank?.let { onOpenBank(it.id) } ?: onOpenHome() },
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
                            lastBank == null -> "导入题库"
                            roundComplete -> "重新开始一轮"
                            else -> "继续练习"
                        },
                        modifier = Modifier.fillMaxWidth(),
                        icon = Icons.Outlined.PlayArrow,
                        onClick = {
                            if (lastBank == null) onOpenHome() else continuePractice()
                        },
                    )
                }
            }
        }
        item {
            LintiCard {
                Column {
                    Text(
                        text = "今日学习",
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalLintiPalette.current.textPrimary,
                    )
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
            SectionHeader(title = "练习方式")
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
                    caption = null,
                    onClick = {
                        val bank = lastBank
                        if (bank == null) {
                            onOpenHome()
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
                    icon = Icons.Outlined.Tune,
                    label = "专项练习",
                    caption = null,
                    onClick = onTopicPractice,
                    modifier = Modifier.weight(1f),
                )
                FeatureTile(
                    icon = Icons.Outlined.TrendingUp,
                    label = "收藏练习",
                    caption = null,
                    onClick = onFavorites,
                    modifier = Modifier.weight(1f),
                )
                FeatureTile(
                    icon = Icons.Outlined.Style,
                    label = "试题闪卡",
                    caption = null,
                    onClick = onFlashcards,
                    modifier = Modifier.weight(1f),
                    iconColor = LocalLintiPalette.current.aiAccent,
                    iconBackground = LocalLintiPalette.current.aiSoft,
                )
                FeatureTile(
                    icon = Icons.Outlined.AutoStories,
                    label = "背题模式",
                    caption = null,
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
    val threshold by viewModel.masteryThreshold.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    WrongBookContent(
        wrongQuestions = wrongQuestions,
        banks = banks,
        initialBankId = initialBankId,
        threshold = threshold,
        onBack = onBack,
        onPractice = { ids ->
            scope.launch {
                val result = viewModel.startWrongQuiz(questionIds = ids)
                if (result.started) onQuiz()
                else viewModel.showMessage(result.message ?: "无法开始错题练习")
            }
        },
        onRemove = viewModel::removeWrongItem,
    )
}

@Composable
internal fun WrongBookContent(
    wrongQuestions: List<WrongQuestionItem>,
    banks: List<BankSummary>,
    initialBankId: Long?,
    threshold: Int?,
    onBack: () -> Unit,
    onPractice: (Set<Long>) -> Unit,
    onRemove: (Long) -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    var bankFilter by remember(initialBankId) { mutableStateOf(initialBankId) }
    var timeFilter by remember { mutableStateOf(WrongTimeFilter.ALL) }
    var showMastered by remember { mutableStateOf(false) }
    val weekStart = now - TimeUnit.DAYS.toMillis(7)
    val inScope = wrongQuestions.filter { item ->
        (bankFilter == null || item.wrongItem.bankId == bankFilter) &&
            (timeFilter == WrongTimeFilter.ALL || item.wrongItem.lastWrongAt >= weekStart)
    }
    val filtered = inScope.filter { it.wrongItem.pendingReview != showMastered }
    val selectedBankName = banks.firstOrNull { it.id == bankFilter }?.name

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item { LintiPageHeader(title = "错题本", subtitle = selectedBankName ?: "全部题库", onBack = onBack) }
        item {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(selected = bankFilter == null, onClick = { bankFilter = null }, label = { Text("全部题库") })
                banks.forEach { bank ->
                    FilterChip(selected = bankFilter == bank.id, onClick = { bankFilter = bank.id }, label = {
                        Text(bank.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                WrongTimeFilter.entries.forEach { filter ->
                    FilterChip(selected = timeFilter == filter, onClick = { timeFilter = filter }, label = { Text(filter.label) })
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(selected = !showMastered, onClick = { showMastered = false }, label = { Text("待复习 ${inScope.count { it.wrongItem.pendingReview }}") })
                FilterChip(selected = showMastered, onClick = { showMastered = true }, label = { Text("已掌握 ${inScope.count { !it.wrongItem.pendingReview }}") })
                Spacer(Modifier.weight(1f))
                if (!showMastered) {
                    OutlinedActionButton(text = "一键重练", enabled = filtered.isNotEmpty(), onClick = {
                        onPractice(filtered.map { it.wrongItem.questionId }.toSet())
                    })
                }
            }
        }
        if (filtered.isEmpty()) {
            item {
                LintiCard(containerColor = LocalLintiPalette.current.primaryPale) {
                    EmptyState(title = if (showMastered) "暂无已掌握题目" else "暂无待复习题目", action = {
                        OutlinedActionButton(text = "返回练习", onClick = onBack)
                    })
                }
            }
        } else {
            items(filtered, key = { it.wrongItem.questionId }) { item ->
                WrongRow(item, threshold, onPractice = { onPractice(setOf(item.wrongItem.questionId)) }, onRemove = { onRemove(item.wrongItem.questionId) })
            }
        }
    }
}

@Composable
private fun WrongRow(item: WrongQuestionItem, threshold: Int?, onPractice: () -> Unit, onRemove: () -> Unit) {
    val pending = item.wrongItem.pendingReview
    LintiCard(shapeRadius = 15.dp, onClick = if (pending) onPractice else null) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(item.type.label, color = LocalLintiPalette.current.primary, background = LocalLintiPalette.current.primaryPale)
                Spacer(Modifier.width(7.dp))
                Text(item.bankName, style = MaterialTheme.typography.labelSmall, color = LocalLintiPalette.current.textSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(7.dp))
            Text(item.sourceNumber.ifBlank { "—" } + ". " + item.stem, style = MaterialTheme.typography.bodyMedium, color = LocalLintiPalette.current.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text("答错 ${item.wrongItem.wrongCount} 次 · 上次 ${formatDateTime(item.wrongItem.lastWrongAt)}", style = MaterialTheme.typography.labelSmall, color = LocalLintiPalette.current.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("连对 ${item.wrongItem.correctStreak}" + (threshold?.let { "/$it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = LocalLintiPalette.current.primary, modifier = Modifier.weight(1f))
                if (pending) TextButton(onClick = onRemove) { Text("移出") }
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
    val limit by viewModel.dailyNewCards.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lock = remember(bankId) { Mutex() }
    var snapshot by remember(bankId) { mutableStateOf<com.xjb.linti.data.repo.LintiRepository.FlashcardSnapshot?>(null) }
    var tail by remember(bankId) { mutableStateOf<List<Long>>(emptyList()) }
    var all by remember(bankId) { mutableStateOf(false) }
    var index by remember(bankId) { mutableIntStateOf(0) }
    var showAnswer by remember(bankId) { mutableStateOf(false) }
    var saving by remember(bankId) { mutableStateOf(false) }
    var tick by remember(bankId) { mutableIntStateOf(0) }

    LaunchedEffect(bankId) {
        while (true) {
            lock.withLock { snapshot = viewModel.loadFlashcards(bankId) }
            tick++
            delay(30_000)
        }
    }
    // tick 使本地日期变化后，即使数据未变也会重新计算队列。
    val queue = remember(snapshot, limit, tail, tick) { snapshot?.let { viewModel.flashcardQueue(it, limit, tail) } }
    val visible = if (all) snapshot?.questions.orEmpty() else queue?.questions.orEmpty()
    val safeIndex = index.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
    val current = visible.getOrNull(safeIndex)
    val previousStep = snapshot?.reviews?.firstOrNull { it.questionId == current?.id }?.step ?: 0
    LaunchedEffect(current?.id, all) { showAnswer = false }

    FlashcardStudyContent(
        questions = visible,
        index = safeIndex,
        dueCount = queue?.questions?.size ?: 0,
        all = all,
        loading = snapshot == null,
        nextDueText = queue?.nextDueAt?.let(viewModel::flashcardDueText),
        showAnswer = showAnswer,
        rememberedDays = FlashcardSchedule.rememberedDays(previousStep),
        saving = saving,
        onBack = onBack,
        onModeChange = { selectedAll -> all = selectedAll; index = 0; showAnswer = false },
        onToggleAnswer = { if (!saving) showAnswer = !showAnswer },
        onPrevious = { if (!saving && safeIndex > 0) { index = safeIndex - 1; showAnswer = false } },
        onNext = { if (!saving && safeIndex < visible.lastIndex) { index = safeIndex + 1; showAnswer = false } },
        onReview = { remembered ->
            if (!saving && current != null) {
                saving = true
                val ratedId = current.id
                val nextIndex = if (all && visible.isNotEmpty()) (safeIndex + 1) % visible.size else 0
                scope.launch {
                    try {
                        lock.withLock {
                            val updated = viewModel.reviewFlashcard(ratedId, remembered, bankId)
                            tail = tail.filterNot { it == ratedId } + if (remembered) emptyList() else listOf(ratedId)
                            snapshot = updated
                        }
                        index = nextIndex
                        showAnswer = false
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        viewModel.showMessage("闪卡评分保存失败：${e.message ?: "请重试"}")
                    } finally {
                        saving = false
                    }
                }
            }
        },
    )
}

@Composable
internal fun FlashcardStudyContent(
    questions: List<Question>, index: Int, dueCount: Int, all: Boolean, loading: Boolean,
    nextDueText: String?, showAnswer: Boolean, rememberedDays: Int, saving: Boolean,
    onBack: () -> Unit, onModeChange: (Boolean) -> Unit, onToggleAnswer: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onReview: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LintiPageHeader(title = "试题闪卡", subtitle = if (questions.isEmpty()) null else "${index + 1} / ${questions.size}", onBack = onBack)
        Row(modifier = Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !all, onClick = { onModeChange(false) }, label = { Text("待复习 $dueCount") }, enabled = !saving)
            FilterChip(selected = all, onClick = { onModeChange(true) }, label = { Text("全部") }, enabled = !saving)
        }
        if (questions.isNotEmpty()) {
            SimpleProgressBar(progress = (index + 1f) / questions.size, modifier = Modifier.padding(horizontal = 20.dp))
            FlashcardContent(
                questions = questions, index = index, showAnswer = showAnswer,
                onToggleAnswer = onToggleAnswer, onPrevious = onPrevious, onNext = onNext,
                showNavigation = all, rememberedDays = rememberedDays, onReview = onReview,
                reviewing = saving, modifier = Modifier.weight(1f),
            )
        } else {
            LintiCard(modifier = Modifier.padding(20.dp), containerColor = LocalLintiPalette.current.primaryPale) {
                EmptyState(
                    title = if (loading) "正在载入" else if (all) "暂无可翻看的题目" else "今天复习完了",
                    message = if (!loading && !all) nextDueText?.let { "下次到期：$it" } else null,
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
                subtitle = "${questions.size} 题",
                onBack = onBack,
            )
        }
        if (questions.isEmpty()) {
            item {
                LintiCard(containerColor = LocalLintiPalette.current.primaryPale) {
                    EmptyState(
                        title = if (collection?.loading == true) "正在载入" else "暂无可背诵题目",
                        action = {
                            OutlinedActionButton(text = "返回", onClick = onBack)
                        },
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
