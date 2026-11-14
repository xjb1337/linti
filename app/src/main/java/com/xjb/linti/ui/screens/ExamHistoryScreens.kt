package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.domain.ExamAccuracyPoint
import com.xjb.linti.domain.ExamHistoryDetail
import com.xjb.linti.domain.ExamHistoryEntry
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.MetricItem
import com.xjb.linti.ui.components.MetricStrip
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.formatDateTime
import com.xjb.linti.ui.formatDuration
import com.xjb.linti.ui.formatPercent
import com.xjb.linti.ui.formatScore
import com.xjb.linti.ui.formatShortDate
import kotlin.math.roundToInt

// 模考历史只展示交卷时保存的成绩快照；加载中、空列表与读取失败分别呈现。
@Composable
fun ExamHistoryScreen(
    viewModel: LintiViewModel,
    onBack: () -> Unit,
    onOpenSession: (Long) -> Unit,
) {
    val state by viewModel.examHistory.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshExamHistory() }
    val accuracyPoints = state.accuracyPoints

    LazyColumn(
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        item {
            LintiPageHeader(
                title = "模考历史",
                onBack = onBack,
            )
        }
        if (accuracyPoints.any { it.accuracy != null }) {
            item { ExamAccuracyChartCard(points = accuracyPoints) }
        }
        when {
            state.entries.isEmpty() && state.loading -> item {
                ExamHistoryStatusCard(title = "正在载入")
            }
            state.entries.isEmpty() && state.failed -> item {
                ExamHistoryStatusCard(
                    title = "读取失败",
                    action = {
                        OutlinedActionButton(text = "重试", onClick = { viewModel.refreshExamHistory() })
                    },
                )
            }
            state.entries.isEmpty() -> item {
                ExamHistoryStatusCard(
                    title = "还没有模考记录",
                    action = {
                        OutlinedActionButton(text = "返回练习", onClick = onBack)
                    },
                )
            }
            else -> items(state.entries, key = { it.sessionId }) { entry ->
                ExamHistoryRow(entry = entry, onClick = { onOpenSession(entry.sessionId) })
            }
        }
    }
}

@Composable
private fun ExamHistoryStatusCard(
    title: String,
    message: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    LintiCard(containerColor = LocalLintiPalette.current.primaryPale) {
        EmptyState(title = title, message = message, action = action)
    }
}

// 「最近 10 次模考正确率」：横轴按场次等距（从旧到新），未知值断线、真实 0 照常画点。
@Composable
private fun ExamAccuracyChartCard(points: List<ExamAccuracyPoint>) {
    val palette = LocalLintiPalette.current
    var selectedSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = points.firstOrNull { it.sessionId == selectedSessionId }

    LintiCard {
        Column {
            Text(
                text = "最近 10 次模考正确率",
                style = MaterialTheme.typography.titleMedium,
                color = palette.textPrimary,
            )
            selected?.let { point ->
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatDateTime(point.submittedAt),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                    )
                    Text(
                        text = point.accuracy?.let(::formatPercent) ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.primary,
                    )
                }
                Text(
                    text = point.bankName,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            ExamAccuracyChart(
                points = points,
                selectedSessionId = selectedSessionId,
                onPointClick = { point ->
                    selectedSessionId =
                        if (selectedSessionId == point.sessionId) null else point.sessionId
                },
            )
        }
    }
}

@Composable
private fun ExamAccuracyChart(
    points: List<ExamAccuracyPoint>,
    selectedSessionId: Long?,
    onPointClick: (ExamAccuracyPoint) -> Unit,
) {
    val palette = LocalLintiPalette.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val axisLabelStyle = MaterialTheme.typography.labelSmall.copy(color = palette.textSecondary)

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(172.dp)) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val plotLeft = with(density) { 36.dp.toPx() }
        val plotRight = widthPx - with(density) { 8.dp.toPx() }
        val plotTop = with(density) { 12.dp.toPx() }
        val plotBottom = heightPx - with(density) { 26.dp.toPx() }
        val centers = examChartPointCenters(points, plotLeft, plotRight, plotTop, plotBottom)
        val touchWidthPx = with(density) {
            val spacing = if (points.size <= 1) {
                plotRight - plotLeft
            } else {
                (plotRight - plotLeft) / (points.size - 1)
            }
            minOf(spacing + 8.dp.toPx(), 44.dp.toPx())
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // 纵轴固定 0–100%，只画 0 / 50 / 100 三档网格与标签
            listOf(0f, 0.5f, 1f).forEach { fraction ->
                val y = plotBottom - fraction * (plotBottom - plotTop)
                drawLine(
                    color = palette.line,
                    start = Offset(plotLeft, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1.dp.toPx(),
                )
                val label = when (fraction) {
                    0.5f -> "50%"
                    1f -> "100%"
                    else -> "0%"
                }
                val measured = textMeasurer.measure(label, axisLabelStyle)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        plotLeft - 6.dp.toPx() - measured.size.width,
                        y - measured.size.height / 2f,
                    ),
                )
            }
            // 折线只在相邻两个有效点之间绘制，未知场次断线
            for (index in 0 until points.lastIndex) {
                if (points[index].accuracy == null || points[index + 1].accuracy == null) continue
                drawLine(
                    color = palette.primary,
                    start = centers[index],
                    end = centers[index + 1],
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            points.forEachIndexed { index, point ->
                val accuracy = point.accuracy ?: return@forEachIndexed
                drawCircle(color = palette.primary, radius = 4.dp.toPx(), center = centers[index])
                if (point.sessionId == selectedSessionId) {
                    drawCircle(
                        color = palette.primary,
                        radius = 7.dp.toPx(),
                        center = centers[index],
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            // 横轴只标首、中、尾三个日期，避免小屏文字重叠
            val dateIndices = buildSet {
                if (points.isNotEmpty()) add(0)
                if (points.size >= 3) add((points.size - 1) / 2)
                if (points.size >= 2) add(points.lastIndex)
            }
            dateIndices.forEach { index ->
                val measured = textMeasurer.measure(formatShortDate(points[index].submittedAt), axisLabelStyle)
                val x = (centers[index].x - measured.size.width / 2f)
                    .coerceIn(0f, widthPx - measured.size.width)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(x, plotBottom + 6.dp.toPx()),
                )
            }
        }
        points.forEachIndexed { index, point ->
            val left = centers[index].x - touchWidthPx / 2f
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), 0) }
                    .size(width = with(density) { touchWidthPx.toDp() }, height = with(density) { heightPx.toDp() })
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPointClick(point) }
                    .semantics { contentDescription = examAccuracyPointDescription(point) },
            )
        }
    }
}

// 点位：x 按场次等距（单场居中），有效点 y 按正确率落在 0–100% 区间，未知点占垂直中线。
private fun examChartPointCenters(
    points: List<ExamAccuracyPoint>,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
): List<Offset> {
    if (points.isEmpty()) return emptyList()
    val count = points.size
    return List(count) { index ->
        val fraction = if (count == 1) 0.5f else index / (count - 1f)
        val accuracy = points[index].accuracy
        Offset(
            x = plotLeft + fraction * (plotRight - plotLeft),
            y = if (accuracy == null) {
                (plotTop + plotBottom) / 2f
            } else {
                plotBottom - accuracy.toFloat() * (plotBottom - plotTop)
            },
        )
    }
}

private fun examAccuracyPointDescription(point: ExamAccuracyPoint): String =
    "${formatDateTime(point.submittedAt)}，${point.bankName}，" +
        if (point.accuracy == null) "正确率未知" else "正确率 ${formatPercent(point.accuracy)}"

@Composable
private fun ExamHistoryRow(
    entry: ExamHistoryEntry,
    onClick: () -> Unit,
) {
    LintiCard(shapeRadius = 15.dp, onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.bankName,
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalLintiPalette.current.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                entry.passed?.let { passed ->
                    StatusPill(
                        text = if (passed) "及格" else "未及格",
                        color = if (passed) LocalLintiPalette.current.success else LocalLintiPalette.current.error,
                        background = if (passed) LocalLintiPalette.current.successSoft else LocalLintiPalette.current.errorSoft,
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "交卷 ${formatDateTime(entry.submittedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = examHistoryScoreText(entry.score, entry.maxScore),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
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

private fun examHistoryScoreText(score: Double?, maxScore: Double?): String = when {
    score != null && maxScore != null -> "${formatScore(score)} / ${formatScore(maxScore)}"
    score != null -> "${formatScore(score)} 分"
    else -> "成绩 —"
}

@Composable
fun ExamHistoryDetailScreen(
    viewModel: LintiViewModel,
    sessionId: Long,
    onBack: () -> Unit,
    onReviewPaper: (sessionId: Long, wrongOnly: Boolean) -> Unit = { _, _ -> },
) {
    val state by viewModel.examHistoryDetail.collectAsStateWithLifecycle()
    LaunchedEffect(sessionId) { viewModel.loadExamHistoryDetail(sessionId) }
    val current = state.takeIf { it.sessionId == sessionId }
    val detail = current?.detail

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
                title = "模考成绩",
                onBack = onBack,
            )
        }
        when {
            detail != null -> {
                item { ExamHistoryDetailScoreCard(detail) }
                item { ExamHistoryDetailMetrics(detail) }
                if (detail.typeStats.isNotEmpty()) {
                    item { SectionHeader(title = "分题型统计", trailing = "完全答对 / 题数") }
                    item {
                        LintiCard {
                            ExamHistoryDetailTypeStats(detail)
                        }
                    }
                }
                // 旧场次没有原卷快照：保留成绩页，隐藏回看入口；
                // 有原卷则「全部试题」恒可看，有错题再加「本场错题」
                if (detail.hasPaper) {
                    item {
                        OutlinedActionButton(
                            text = "全部试题",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onReviewPaper(detail.sessionId, false) },
                        )
                    }
                    if (detail.wrongCount > 0) {
                        item {
                            OutlinedActionButton(
                                text = "本场错题",
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { onReviewPaper(detail.sessionId, true) },
                            )
                        }
                    }
                }
            }
            current == null || current.loading -> item {
                ExamHistoryStatusCard(title = "正在载入")
            }
            current?.failed == true -> item {
                ExamHistoryStatusCard(
                    title = "读取失败",
                    action = {
                        OutlinedActionButton(
                            text = "重试",
                            onClick = { viewModel.loadExamHistoryDetail(sessionId) },
                        )
                    },
                )
            }
            else -> item {
                ExamHistoryStatusCard(
                    title = "没有找到这场记录",
                    message = "记录可能已被清理。",
                    action = {
                        OutlinedActionButton(text = "返回列表", onClick = onBack)
                    },
                )
            }
        }
    }
}

@Composable
private fun ExamHistoryDetailScoreCard(detail: ExamHistoryDetail) {
    val palette = LocalLintiPalette.current
    val passColor = when (detail.passed) {
        true -> palette.success
        false -> palette.error
        null -> palette.textPrimary
    }
    LintiCard(containerColor = palette.primaryPale, borderColor = palette.primarySoft) {
        Column {
            Text(
                text = "${detail.bankName} · ${PracticeMode.EXAM.label}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = detail.score?.let(::formatScore) ?: "—",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = MaterialTheme.typography.headlineLarge.fontSize * 2.2f,
                    ),
                    color = passColor,
                )
                Text(
                    text = " / ${detail.maxScore?.let(::formatScore) ?: "—"} 分",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
            detail.passed?.let { passed ->
                Spacer(Modifier.height(4.dp))
                StatusPill(
                    text = if (detail.passScore != null) {
                        if (passed) {
                            "及格 · 及格线 ${formatScore(detail.passScore)} 分"
                        } else {
                            "未达及格线 ${formatScore(detail.passScore)} 分"
                        }
                    } else {
                        if (passed) "及格" else "未及格"
                    },
                    color = if (passed) palette.success else palette.error,
                    background = if (passed) palette.successSoft else palette.errorSoft,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "开始 ${formatDateTime(detail.startedAt)} · 交卷 ${formatDateTime(detail.submittedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = listOfNotNull(
                    "用时 " + (detail.durationSeconds?.let(::formatDuration) ?: "—"),
                    detail.totalQuestions?.let { "共 $it 题" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
            )
        }
    }
}

@Composable
private fun ExamHistoryDetailMetrics(detail: ExamHistoryDetail) {
    val palette = LocalLintiPalette.current
    MetricStrip(
        items = listOf(
            MetricItem("${detail.correctCount}", "正确", palette.success),
            MetricItem("${detail.wrongCount}", "错误", palette.error),
            MetricItem("${detail.unansweredCount}", "未做", palette.textSecondary),
            MetricItem(detail.accuracy?.let(::formatPercent) ?: "—", "正确率", palette.primary),
        ),
    )
}

@Composable
private fun ExamHistoryDetailTypeStats(detail: ExamHistoryDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        detail.typeStats.filter { it.total > 0 }.forEach { stats ->
            TypeStatsRow(stats)
        }
    }
}
