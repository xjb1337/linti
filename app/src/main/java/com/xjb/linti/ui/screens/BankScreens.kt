package com.xjb.linti.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.formatRelativeDay
import com.xjb.linti.ui.theme.AiAccent
import com.xjb.linti.ui.theme.AiSoft
import com.xjb.linti.ui.theme.Background
import com.xjb.linti.ui.theme.Error
import com.xjb.linti.ui.theme.ErrorSoft
import com.xjb.linti.ui.theme.Line
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
fun BanksScreen(
    viewModel: LintiViewModel,
    onOpenBank: (Long) -> Unit,
    onQuiz: () -> Unit,
) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
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
            LintiPageHeader(
                title = "我的题库",
                actions = {
                    StatusPill("本机存储", color = Success, background = SuccessSoft)
                },
            )
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
                    color = TextPrimary,
                )
                Text(
                    text = " · ${filtered.sumOf { it.questionCount }} 题 · 最近使用排序",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
        }
        if (filtered.isEmpty()) {
            item {
                LintiCard(containerColor = PrimaryPale) {
                    EmptyState(
                        title = if (banks.isEmpty()) "还没有题库" else "没有匹配的题库",
                        message = if (banks.isEmpty()) {
                            "从首页导入题集，或在设置中载入虚构示例题。"
                        } else {
                            "换一个关键词试试。"
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
                        scope.launch {
                            val result = viewModel.startSequenceQuiz(bank.id)
                            if (result.started) {
                                onQuiz()
                            } else {
                                viewModel.showMessage(result.message ?: "无法开始练习")
                            }
                        }
                    },
                )
            }
        }
        item {
            Text(
                text = "题库、收藏、笔记与错题都属于本机数据，可随时导出 JSON 备份。",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
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
                        .background(PrimaryPale),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.LibraryBooks,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bank.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${bank.questionCount} 题 · 本地题库 · 免费练习",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "顺序练习",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${bank.sequenceAnsweredCount}/${bank.questionCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Primary,
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
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(7.dp))
            SimpleProgressBar(progress = progress)
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "错题 ${bank.wrongCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bank.pendingWrongCount > 0) Error else TextSecondary,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "收藏 ${bank.favoriteCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "笔记 ${bank.noteCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.weight(1f))
                OutlinedActionButton(
                    text = if (bank.sequenceAnsweredCount > 0) "继续练习" else "开始练习",
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
    val bank = banks.firstOrNull { it.id == bankId }

    if (bank == null) {
        Column {
            LintiPageHeader(title = "题库", onBack = onBack)
            LintiCard(
                modifier = Modifier.padding(20.dp),
                containerColor = PrimaryPale,
            ) {
                EmptyState(
                    title = "正在载入题库",
                    message = "若持续为空，请返回题库列表重新打开。",
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
                actions = {
                    StatusPill("本机题库", color = Success, background = SuccessSoft)
                },
            )
        }
        item {
            SearchEntry(onClick = onSearch)
        }
        item {
            LintiCard(containerColor = PrimaryPale, borderColor = PrimarySoft) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "顺序练习",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = bank.sequenceAnsweredCount.toString(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    color = Primary,
                                )
                                Text(
                                    text = " / ${bank.questionCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(15.dp))
                                .background(PrimarySoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = Primary,
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
                        progressColor = Primary,
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = when {
                                bank.questionCount == 0 -> "先导入题目再开始"
                                bank.sequenceAnsweredCount >= bank.questionCount -> "这一轮已完成，可再练一次"
                                bank.sequenceAnsweredCount == 0 -> "从第一题开始"
                                else -> "继续上次的位置"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        PrimaryActionButton(
                            text = if (bank.sequenceAnsweredCount > 0) "继续练习" else "开始练习",
                            onClick = {
                                scope.launch {
                                    val result = viewModel.startSequenceQuiz(bank.id)
                                    if (result.started) onQuiz()
                                    else viewModel.showMessage(result.message ?: "无法开始练习")
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
                    MetricItem("${bank.pendingWrongCount}", "待复习", Error),
                    MetricItem("${bank.favoriteCount}", "收藏", Primary),
                    MetricItem("${bank.noteCount}", "笔记", AiAccent),
                ),
            )
        }
        item {
            SectionHeader(title = "学习工具", trailing = "全部免费")
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
                            iconColor = Error,
                            iconBackground = ErrorSoft,
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
                            iconColor = AiAccent,
                            iconBackground = AiSoft,
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
                            iconColor = Warning,
                            iconBackground = WarningSoft,
                        )
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (bank.note != null) {
            item {
                LintiCard(containerColor = Background, borderColor = Line) {
                    Column {
                        Text(
                            text = "题库备注",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = bank.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                        )
                    }
                }
            }
        }
        item {
            Text(
                text = "顺序进度、错题、收藏和笔记均保存在本机，不会上传。",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFFE9EFEC))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = "搜索题干、知识点或答案",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
    }
}
