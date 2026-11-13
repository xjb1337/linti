package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Quiz
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.LintiSearchField
import com.xjb.linti.ui.components.MetricItem
import com.xjb.linti.ui.components.MetricStrip
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.SimpleProgressBar
import com.xjb.linti.ui.formatRelativeDay
import kotlinx.coroutines.launch

@Composable
fun BanksScreen(
    viewModel: LintiViewModel,
    onOpenBank: (Long) -> Unit,
    onQuiz: () -> Unit,
) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
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

    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            LintiPageHeader(title = "我的题库")
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
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalLintiPalette.current.textPrimary,
                )
                Text(
                    text = " · ${filtered.sumOf { it.questionCount }} 题",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
        }
        if (filtered.isEmpty()) {
            item {
                LintiCard(containerColor = LocalLintiPalette.current.primaryPale) {
                    EmptyState(
                        title = if (banks.isEmpty()) "还没有题库" else "没有匹配的题库",
                        action = if (banks.isEmpty()) {
                            {
                                OutlinedActionButton(
                                    text = "载入示例题库",
                                    onClick = viewModel::loadSampleData,
                                )
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
    if (restartBankId != null) {
        val id = restartBankId ?: 0L
        AlertDialog(
            onDismissRequest = { restartBankId = null },
            title = { Text("重新开始一轮？") },
            text = { Text("将从第一题开始，本轮进度清零；历史作答记录不会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    restartBankId = null
                    scope.launch {
                        viewModel.restartSequenceRound(id)
                        val result = viewModel.startSequenceQuiz(id)
                        if (result.started) onQuiz()
                        else viewModel.showMessage(result.message ?: "无法开始练习")
                    }
                }) { Text("重新开始") }
            },
            dismissButton = {
                TextButton(onClick = { restartBankId = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun BankCard(
    bank: BankSummary,
    onClick: () -> Unit,
    onContinue: () -> Unit,
) {
    val progress = if (bank.questionCount == 0) {
        0f
    } else {
        bank.sequenceAnsweredCount.toFloat() / bank.questionCount
    }
    LintiCard(shapeRadius = 17.dp, onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(43.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(LocalLintiPalette.current.primaryPale),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.LibraryBooks,
                        contentDescription = null,
                        tint = LocalLintiPalette.current.primary,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bank.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = LocalLintiPalette.current.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${bank.questionCount} 题",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = LocalLintiPalette.current.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "顺序练习",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${bank.sequenceAnsweredCount}/${bank.questionCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalLintiPalette.current.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (bank.lastOpenedAt == null) {
                        "尚未开始"
                    } else {
                        "最近练习 ${formatRelativeDay(bank.lastOpenedAt)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
            Spacer(Modifier.height(7.dp))
            SimpleProgressBar(progress = progress)
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "错题 ${bank.wrongCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bank.pendingWrongCount > 0) LocalLintiPalette.current.error else LocalLintiPalette.current.textSecondary,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "收藏 ${bank.favoriteCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "笔记 ${bank.noteCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
                Spacer(Modifier.weight(1f))
                OutlinedActionButton(
                    text = when {
                        bank.questionCount > 0 && bank.sequenceAnsweredCount >= bank.questionCount -> "重新开始一轮"
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
    val bank = banks.firstOrNull { it.id == bankId }

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
                title = bank.name,
                subtitle = "${bank.questionCount} 题 · 已练 ${bank.sequenceAnsweredCount} · ${formatRelativeDay(bank.lastOpenedAt)}",
                onBack = onBack,
            )
        }
        item {
            SearchEntry(onClick = onSearch)
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
            LintiCard(containerColor = LocalLintiPalette.current.primaryPale, borderColor = LocalLintiPalette.current.primarySoft) {
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
                    MetricItem("${bank.pendingWrongCount}", "待复习", LocalLintiPalette.current.error),
                    MetricItem("${bank.favoriteCount}", "收藏", LocalLintiPalette.current.primary),
                    MetricItem("${bank.noteCount}", "笔记", LocalLintiPalette.current.aiAccent),
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
                        )
                        FeatureTile(
                            icon = Icons.Outlined.FactCheck,
                            label = "错题重练",
                            onClick = onWrongBook,
                            modifier = Modifier.weight(1f),
                            iconColor = LocalLintiPalette.current.error,
                            iconBackground = LocalLintiPalette.current.errorSoft,
                        )
                        FeatureTile(
                            icon = Icons.Outlined.BookmarkBorder,
                            label = "收藏",
                            onClick = onFavorites,
                            modifier = Modifier.weight(1f),
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
                        )
                        FeatureTile(
                            icon = Icons.Outlined.Style,
                            label = "闪卡",
                            onClick = onFlashcards,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FeatureTile(
                            icon = Icons.Outlined.AutoStories,
                            label = "背题",
                            onClick = onRecite,
                            modifier = Modifier.weight(1f),
                            iconColor = LocalLintiPalette.current.warning,
                            iconBackground = LocalLintiPalette.current.warningSoft,
                        )
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
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
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("重新开始一轮？") },
            text = { Text("将从第一题开始，本轮进度清零；历史作答记录不会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartConfirm = false
                    scope.launch {
                        viewModel.restartSequenceRound(bank.id)
                        val result = viewModel.startSequenceQuiz(bank.id)
                        if (result.started) onQuiz()
                        else viewModel.showMessage(result.message ?: "无法开始练习")
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
