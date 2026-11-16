package com.xjb.linti.ui.screens

import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.PracticeMode
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
import com.xjb.linti.ui.components.WarningNote
import com.xjb.linti.ui.formatDuration
import com.xjb.linti.ui.formatPercent
import com.xjb.linti.ui.formatScore

@Composable
fun ResultScreen(
    viewModel: LintiViewModel,
    onClose: () -> Unit,
    onOpenBank: (Long) -> Unit,
    onWrongBook: (Long?) -> Unit = {},
    onReviewPaper: (sessionId: Long, wrongOnly: Boolean) -> Unit = { _, _ -> },
    onRetryExam: (Long) -> Unit,
) {
    val result by viewModel.lastResult.collectAsStateWithLifecycle()
    val data = result

    if (data == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(modifier = Modifier.padding(horizontal = 16.dp), title = "成绩报告", onBack = onClose)
            EmptyState(
                title = "没有可显示的成绩",
                modifier = Modifier.padding(20.dp),
                action = {
                    OutlinedActionButton(text = "返回", onClick = onClose)
                },
            )
        }
        return
    }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("成绩报告", pageListState, onBack = onClose) {
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
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    if (data.bankId != null && data.mode == PracticeMode.EXAM) {
                        PrimaryActionButton(
                            text = "再考一次",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onRetryExam(data.bankId) },
                        )
                    }
                    if (data.mode == PracticeMode.EXAM) {
                        // 模考走本场原卷回看，不再跳题库错题本：
                        // 全部试题恒可看，零错题也有入口；有错题才加「本场错题」
                        OutlinedActionButton(
                            text = "全部试题",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onReviewPaper(data.sessionId, false) },
                        )
                        if (data.grading.wrongCount > 0) {
                            OutlinedActionButton(
                                text = "本场错题",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onReviewPaper(data.sessionId, true) },
                            )
                        }
                    } else if (data.grading.wrongCount > 0) {
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
}

@Composable
fun TypeStatsRow(stats: TypeStats) {
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
