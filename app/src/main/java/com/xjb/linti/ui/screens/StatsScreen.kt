package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.StudySummary
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.MetricItem
import com.xjb.linti.ui.components.MetricStrip
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.SimpleProgressBar
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.formatPercent
import com.xjb.linti.ui.theme.LocalLintiPalette

@Composable
fun StatsScreen(viewModel: LintiViewModel, onBack: () -> Unit) {
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    var summary by remember { mutableStateOf<StudySummary?>(null) }

    LaunchedEffect(banks.size, banks.sumOf { it.sequenceAnsweredCount }) {
        summary = viewModel.studySummary()
    }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("学习统计", pageListState, onBack = onBack) {
        LazyColumn(
            state = pageListState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LintiPageHeader(title = "学习统计", onBack = onBack)
            }
            item {
                MetricStrip(
                    items = listOf(
                        MetricItem("${summary?.totalAnswered ?: 0}", "累计答题"),
                        MetricItem(summary?.accuracy?.let(::formatPercent) ?: "0%", "练习正确率"),
                        MetricItem("${summary?.streakDays ?: 0}", "连续学习 / 天"),
                    ),
                )
            }
            item {
                LintiCard(shapeRadius = 20.dp) {
                    Column {
                        StatsRow("今日答题", "${summary?.todayAnswered ?: 0} 次")
                        ThinDivider()
                        StatsRow("待复习错题", "${summary?.pendingWrong ?: 0} 题")
                        ThinDivider()
                        StatsRow("题库总题量", "${summary?.totalQuestions ?: 0} 题")
                        ThinDivider()
                        StatsRow("练习场次", "${summary?.sessionCount ?: 0} 场")
                    }
                }
            }
            item {
                SectionHeader(title = "题库顺序练习进度")
            }
            if (banks.isEmpty()) {
                item {
                    LintiCard {
                        Text(
                            text = "还没有题库，导入后这里会显示各题库的练习进度。",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                }
            } else {
                items(banks, key = { it.id }) { bank ->
                    LintiCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = bank.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LocalLintiPalette.current.textPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${bank.sequenceAnsweredCount}/${bank.questionCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = LocalLintiPalette.current.primary,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            SimpleProgressBar(
                                progress = if (bank.questionCount == 0) 0f else bank.sequenceAnsweredCount.toFloat() / bank.questionCount,
                                progressDescription = "${bank.name} 顺序练习进度 ${bank.sequenceAnsweredCount}/${bank.questionCount}",
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "待复习错题 ${bank.pendingWrongCount} · 收藏 ${bank.favoriteCount} · 笔记 ${bank.noteCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalLintiPalette.current.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalLintiPalette.current.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = LocalLintiPalette.current.primary,
        )
    }
}