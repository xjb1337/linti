package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.BankSummary
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.FeatureTile
import com.xjb.linti.ui.components.LintiConfirmationSheet
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.LintiSearchField
import com.xjb.linti.ui.components.MetricItem
import com.xjb.linti.ui.components.MetricStrip
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.SimpleProgressBar
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.formatRelativeDay
import kotlinx.coroutines.launch

@Composable
fun BanksScreen(
    viewModel: LintiViewModel,
    onOpenBank: (Long) -> Unit,
    onQuiz: () -> Unit,
    onOpenImport: (() -> Unit)? = null,
) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val palette = LocalLintiPalette.current
    var query by remember { mutableStateOf("") }
    var restartBankId by remember { mutableStateOf<Long?>(null) }
    val filtered = if (query.isBlank()) {
        banks
    } else {
        banks.filter {
            it.name.contains(query.trim(), ignoreCase = true) ||
                it.note.orEmpty().contains(query.trim(), ignoreCase = true)
        }
    }

    val importAction: @Composable RowScope.() -> Unit = {
        if (onOpenImport != null) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(palette.primarySoft)
                    .clickable(onClick = onOpenImport)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = palette.primary, modifier = Modifier.size(16.dp))
                Text("导入", style = MaterialTheme.typography.labelMedium, color = palette.primary)
            }
        }
    }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("我的题库", pageListState, actions = importAction) {
        LazyColumn(
            state = pageListState,
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LintiPageHeader(title = "我的题库", actions = importAction)
            }
            item {
                LintiSearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "搜索题库名称或备注",
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${filtered.size} 个题库",
                        style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                        color = palette.textPrimary,
                    )
                    Text(
                        text = " · ${filtered.sumOf { it.questionCount }} 题",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = palette.textSecondary,
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    LintiCard(containerColor = palette.primaryPale, borderColor = palette.primary.copy(alpha = 0.25f), shapeRadius = 20.dp) {
                        EmptyState(
                            title = if (banks.isEmpty()) "还没有题库" else "没有匹配的题库",
                            message = if (banks.isEmpty()) "可以去首页导入文档、拍照识题，或先体验内置示例题库" else null,
                            action = if (banks.isEmpty()) {
                                {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        if (onOpenImport != null) {
                                            PrimaryActionButton(
                                                text = "去导题",
                                                onClick = onOpenImport,
                                            )
                                        }
                                        OutlinedActionButton(
                                            text = "载入示例题库",
                                            onClick = viewModel::loadSampleData,
                                        )
                                    }
                                }
                            } else {
                                {
                                    OutlinedActionButton(
                                        text = "清空搜索",
                                        onClick = { query = "" },
                                    )
                                }
                            },
                        )
                    }
                }
            } else {
                items(filtered, key = { it.id }) { bank ->
                    BankCard(
                        bank = bank,
                        onClick = { onOpenBank(bank.id) },
                        onContinue = {
                            if (bank.questionCount > 0 && bank.sequenceAnsweredCount >= bank.questionCount) {
                                restartBankId = bank.id
                            } else {
                                scope.launch {
                                    val result = viewModel.startSequenceQuiz(bank.id)
                                    if (result.started) {
                                        onQuiz()
                                    } else {
                                        viewModel.showMessage(result.message ?: "无法开始练习")
                                    }
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (restartBankId != null) {
        val id = restartBankId ?: 0L
        LintiConfirmationSheet("重新开始一轮？", "将从第一题开始，本轮进度清零；历史作答记录不会删除。", "重新开始",
            onDismiss = { restartBankId = null }, onConfirm = {
                restartBankId = null
                scope.launch {
                    viewModel.restartSequenceRound(id)
                    val result = viewModel.startSequenceQuiz(id)
                    if (result.started) onQuiz() else viewModel.showMessage(result.message ?: "无法开始练习")
                }
            })
    }
}

@Composable
private fun BankCard(
    bank: BankSummary,
    onClick: () -> Unit,
    onContinue: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    val progress = if (bank.questionCount == 0) {
        0f
    } else {
        (bank.sequenceAnsweredCount.toFloat() / bank.questionCount).coerceIn(0f, 1f)
    }
    val completed = bank.questionCount > 0 && bank.sequenceAnsweredCount >= bank.questionCount
    val (accent, accentSoft) = when ((bank.id % 4L).toInt()) {
        1 -> palette.aiAccent to palette.aiSoft
        2 -> palette.success to palette.successSoft
        3 -> palette.warning to palette.warningSoft
        else -> palette.primary to palette.primaryPale
    }
    LintiCard(shapeRadius = 20.dp, onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 48.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accentSoft)
                        .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(11.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(accent),
                    )
                    Icon(
                        Icons.Outlined.LibraryBooks,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp).align(Alignment.Center),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bank.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${bank.questionCount} 题 · " + if (bank.lastOpenedAt == null) "尚未开始" else "最近 ${formatRelativeDay(bank.lastOpenedAt)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusPill(
                    text = when {
                        completed -> "已完成"
                        bank.sequenceAnsweredCount > 0 -> "${(progress * 100).toInt()}%"
                        else -> "未开始"
                    },
                    color = when {
                        completed -> palette.success
                        bank.sequenceAnsweredCount > 0 -> accent
                        else -> palette.textSecondary
                    },
                    background = when {
                        completed -> palette.successSoft
                        bank.sequenceAnsweredCount > 0 -> accentSoft
                        else -> palette.surfaceContainer
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "顺序练习",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${bank.sequenceAnsweredCount}/${bank.questionCount}",
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            SimpleProgressBar(progress = progress, progressColor = accent)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "错题 ${bank.wrongCount}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = if (bank.pendingWrongCount > 0) palette.error else palette.textSecondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "收藏 ${bank.favoriteCount}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = palette.textSecondary,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "笔记 ${bank.noteCount}",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = palette.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                OutlinedActionButton(
                    text = when {
                        completed -> "重新开始一轮"
                        bank.sequenceAnsweredCount > 0 -> "继续练习"
                        else -> "开始练习"
                    },
                    onClick = onContinue,
                )
            }
        }
    }
}

@Composable
fun BankHomeScreen(
    viewModel: LintiViewModel,
    bankId: Long,
    onBack: () -> Unit,
    onOpenHome: () -> Unit,
    onQuiz: () -> Unit,
    onExamSetup: () -> Unit,
    onTopicPractice: () -> Unit,
    onChapters: () -> Unit,
    onPastPapers: () -> Unit,
    onAiQuestions: () -> Unit,
    onAddQuestion: () -> Unit,
    onWrongBook: () -> Unit,
    onFavorites: () -> Unit,
    onNotes: () -> Unit,
    onSearch: () -> Unit,
    onFlashcards: () -> Unit,
    onRecite: () -> Unit,
) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val bank = banks.firstOrNull { it.id == bankId }

    if (deleting) {
        Column(Modifier.fillMaxSize()) {
            LintiPageHeader(title = "正在删除题库…")
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    if (bank == null) {
        Column {
            LintiPageHeader(title = "题库", onBack = onBack)
            LintiCard(
                modifier = Modifier.padding(20.dp),
                containerColor = LocalLintiPalette.current.primaryPale,
            ) {
                EmptyState(
                    title = "题库不存在",
                    message = "题库可能已被删除。",
                    action = {
                        OutlinedActionButton(text = "返回", onClick = onBack)
                    },
                )
            }
        }
        return
    }

    val deleteActions: @Composable RowScope.() -> Unit = {
        IconButton(onClick = { showDeleteConfirm = true }, enabled = !deleting) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "删除题库",
                tint = LocalLintiPalette.current.error,
            )
        }
    }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage(bank.name, pageListState, onBack = onBack, actions = deleteActions) {
        LazyColumn(
            state = pageListState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LintiPageHeader(
                    title = bank.name,
                    subtitle = "${bank.questionCount} 题 · 已练 ${bank.sequenceAnsweredCount} · ${formatRelativeDay(bank.lastOpenedAt)}",
                    onBack = onBack,
                    actions = deleteActions,
                )
            }
            item {
                SearchEntry(onClick = onSearch)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedActionButton(text = "AI 出题", onClick = onAiQuestions, modifier = Modifier.weight(1f))
                    OutlinedActionButton(text = "新增题目", onClick = onAddQuestion, modifier = Modifier.weight(1f))
                }
            }
            if (bank.questionCount == 0) {
                item {
                    LintiCard(containerColor = LocalLintiPalette.current.primaryPale) {
                        EmptyState(
                            title = "题库暂无题目",
                            action = {
                                OutlinedActionButton(
                                    text = "返回首页导入",
                                    onClick = onOpenHome,
                                )
                            },
                        )
                    }
                }
            }
            item {
                LintiCard() {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "顺序练习",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LocalLintiPalette.current.textPrimary,
                                )
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        text = bank.sequenceAnsweredCount.toString(),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = LocalLintiPalette.current.primary,
                                    )
                                    Text(
                                        text = " / ${bank.questionCount}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = LocalLintiPalette.current.textSecondary,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(LocalLintiPalette.current.primarySoft),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.PlayArrow,
                                    contentDescription = null,
                                    tint = LocalLintiPalette.current.primary,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        SimpleProgressBar(
                            progress = if (bank.questionCount == 0) {
                                0f
                            } else {
                                bank.sequenceAnsweredCount.toFloat() / bank.questionCount
                            },
                            progressColor = LocalLintiPalette.current.primary,
                        )
                        Spacer(Modifier.height(7.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(Modifier.weight(1f))
                            val roundComplete = bank.questionCount > 0 && bank.sequenceAnsweredCount >= bank.questionCount
                            PrimaryActionButton(
                                text = when {
                                    bank.questionCount == 0 -> "暂无题目"
                                    roundComplete -> "重新开始一轮"
                                    bank.sequenceAnsweredCount > 0 -> "继续练习"
                                    else -> "开始练习"
                                },
                                enabled = bank.questionCount > 0,
                                onClick = {
                                    if (roundComplete) {
                                        showRestartConfirm = true
                                    } else {
                                        scope.launch {
                                            val result = viewModel.startSequenceQuiz(bank.id)
                                            if (result.started) onQuiz()
                                            else viewModel.showMessage(result.message ?: "无法开始练习")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item {
                MetricStrip(
                    items = listOf(
                        MetricItem("${bank.questionCount}", "题量"),
                        MetricItem("${bank.pendingWrongCount}", "待复习"),
                        MetricItem("${bank.favoriteCount}", "收藏"),
                        MetricItem("${bank.noteCount}", "笔记"),
                    ),
                )
            }
            item {
                SectionHeader(title = "学习工具")
            }
            item {
                LintiCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FeatureTile(
                                icon = Icons.Outlined.Quiz,
                                label = "模拟考试",
                                onClick = onExamSetup,
                                modifier = Modifier.weight(1f),
                                iconColor = LocalLintiPalette.current.primary,
                                iconBackground = LocalLintiPalette.current.primaryPale,
                            )
                            FeatureTile(
                                icon = Icons.Outlined.FactCheck,
                                label = "错题重练",
                                onClick = onWrongBook,
                                modifier = Modifier.weight(1f),
                                iconColor = LocalLintiPalette.current.error,
                                iconBackground = LocalLintiPalette.current.errorSoft,
                                badge = bank.pendingWrongCount.takeIf { it > 0 }?.let { if (it > 99) "99+" else it.toString() },
                                contentDesc = bank.pendingWrongCount.takeIf { it > 0 }?.let { "错题重练，待复习 $it 题" },
                            )
                            FeatureTile(
                                icon = Icons.Outlined.BookmarkBorder,
                                label = "收藏",
                                onClick = onFavorites,
                                modifier = Modifier.weight(1f),
                                iconColor = LocalLintiPalette.current.warning,
                                iconBackground = LocalLintiPalette.current.warningSoft,
                                badge = bank.favoriteCount.takeIf { it > 0 }?.let { if (it > 99) "99+" else it.toString() },
                                badgeColor = LocalLintiPalette.current.warning,
                                badgeBackground = LocalLintiPalette.current.warningSoft,
                                contentDesc = bank.favoriteCount.takeIf { it > 0 }?.let { "收藏，共 $it 题" },
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FeatureTile(
                                icon = Icons.Outlined.EditNote,
                                label = "笔记",
                                onClick = onNotes,
                                modifier = Modifier.weight(1f),
                                iconColor = LocalLintiPalette.current.aiAccent,
                                iconBackground = LocalLintiPalette.current.aiSoft,
                            )
                            FeatureTile(
                                icon = Icons.Outlined.Search,
                                label = "搜索",
                                onClick = onSearch,
                                modifier = Modifier.weight(1f),
                                iconColor = LocalLintiPalette.current.primary,
                                iconBackground = LocalLintiPalette.current.primaryPale,
                            )
                            FeatureTile(
                                icon = Icons.Outlined.LibraryBooks,
                                label = "章节",
                                onClick = onChapters,
                                modifier = Modifier.weight(1f),
                                iconColor = LocalLintiPalette.current.success,
                                iconBackground = LocalLintiPalette.current.successSoft,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FeatureTile(
                                icon = Icons.Outlined.Description,
                                label = "真题",
                                onClick = onPastPapers,
                                modifier = Modifier.weight(1f),
                                iconColor = LocalLintiPalette.current.warning,
                                iconBackground = LocalLintiPalette.current.warningSoft,
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.weight(2f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FeatureTile(
                                icon = Icons.Outlined.Tune,
                                label = "专项练习",
                                onClick = onTopicPractice,
                                modifier = Modifier.weight(1f),
                                iconColor = LocalLintiPalette.current.primary,
                                iconBackground = LocalLintiPalette.current.primaryPale,
                            )
                            FeatureTile(
                                icon = Icons.Outlined.Style,
                                label = "闪卡",
                                onClick = onFlashcards,
                                modifier = Modifier.weight(1f),
                                iconColor = LocalLintiPalette.current.aiAccent,
                                iconBackground = LocalLintiPalette.current.aiSoft,
                            )
                            FeatureTile(
                                icon = Icons.Outlined.AutoStories,
                                label = "背题",
                                onClick = onRecite,
                                modifier = Modifier.weight(1f),
                                iconColor = LocalLintiPalette.current.success,
                                iconBackground = LocalLintiPalette.current.successSoft,
                            )
                        }
                    }
                }
            }
            if (bank.note != null) {
                item {
                    LintiCard(containerColor = LocalLintiPalette.current.background, borderColor = LocalLintiPalette.current.line) {
                        Column {
                            Text(
                                text = "题库备注",
                                style = MaterialTheme.typography.labelMedium,
                                color = LocalLintiPalette.current.textSecondary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = bank.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalLintiPalette.current.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRestartConfirm) {
        LintiConfirmationSheet("重新开始一轮？", "将从第一题开始，本轮进度清零；历史作答记录不会删除。", "重新开始",
            onDismiss = { showRestartConfirm = false }, onConfirm = {
                showRestartConfirm = false
                scope.launch {
                    viewModel.restartSequenceRound(bank.id)
                    val result = viewModel.startSequenceQuiz(bank.id)
                    if (result.started) onQuiz() else viewModel.showMessage(result.message ?: "无法开始练习")
                }
            })
    }

    if (showDeleteConfirm) {
        LintiConfirmationSheet(
            "删除题库「${bank.name}」？",
            "将删除其中 ${bank.questionCount} 道题及作答、错题、收藏、笔记、闪卡与练习进度，不可撤销；模考历史快照保留。建议先在数据备份中导出。",
            "删除题库", danger = true,
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                if (!deleting) {
                    deleting = true
                    scope.launch {
                        runCatching { viewModel.deleteBank(bank.id) }.fold(
                            onSuccess = { onBack() },
                            onFailure = { error ->
                                deleting = false
                                viewModel.showMessage("删除失败：${error.message ?: "未知错误"}")
                            },
                        )
                    }
                }
            },
        )
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(LocalLintiPalette.current.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = LocalLintiPalette.current.textSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "搜索题干、知识点或答案",
            style = MaterialTheme.typography.bodySmall,
            color = LocalLintiPalette.current.textSecondary,
        )
    }
}
