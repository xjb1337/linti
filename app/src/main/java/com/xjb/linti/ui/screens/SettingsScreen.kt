package com.xjb.linti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.TaskAlt
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.BuildConfig
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.StudySummary
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiMenuRow
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.formatPercent
import com.xjb.linti.ui.nav.Routes
import com.xjb.linti.ui.theme.LocalLintiPalette

@Composable
fun SettingsScreen(viewModel: LintiViewModel, onNavigate: (String) -> Unit = {}) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val appearanceMode by viewModel.appearanceMode.collectAsStateWithLifecycle()
    val colorTheme by viewModel.colorTheme.collectAsStateWithLifecycle()
    val palette = LocalLintiPalette.current
    var summary by remember { mutableStateOf<StudySummary?>(null) }

    LaunchedEffect(banks.size, banks.sumOf { it.sequenceAnsweredCount }) {
        summary = viewModel.studySummary()
    }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("我的", pageListState) {
        LazyColumn(
            state = pageListState,
            modifier = Modifier.testTag("settings-list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LintiPageHeader(title = "我的")
            }
            item {
                LintiCard(
                    containerColor = palette.primaryPale,
                    borderColor = palette.primary.copy(alpha = 0.25f),
                    shapeRadius = 20.dp,
                    onClick = { onNavigate(Routes.SettingsStats) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "学习概览",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            StatusPill(
                                text = "连续 ${summary?.streakDays ?: 0} 天",
                                color = palette.primary,
                                background = palette.primarySoft,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProfileStatCell(
                                value = "${summary?.totalAnswered ?: 0}",
                                label = "累计答题",
                                color = palette.primary,
                                modifier = Modifier.weight(1f),
                            )
                            ProfileStatCell(
                                value = summary?.accuracy?.let(::formatPercent) ?: "0%",
                                label = "正确率",
                                color = palette.success,
                                modifier = Modifier.weight(1f),
                            )
                            ProfileStatCell(
                                value = "${banks.size}",
                                label = "题库数",
                                color = palette.textPrimary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(title = "学习与外观")
                    LintiCard(shapeRadius = 20.dp) {
                        Column {
                            LintiMenuRow(
                                icon = Icons.Outlined.QueryStats,
                                title = "学习统计",
                                subtitle = "${summary?.totalAnswered ?: 0} 次答题 · 正确率 ${summary?.accuracy?.let(::formatPercent) ?: "0%"}",
                                onClick = { onNavigate(Routes.SettingsStats) },
                            )
                            ThinDivider()
                            LintiMenuRow(
                                icon = Icons.Outlined.Palette,
                                title = "外观",
                                subtitle = "${appearanceMode.label} · ${colorTheme.label}",
                                onClick = { onNavigate(Routes.SettingsAppearance) },
                            )
                            ThinDivider()
                            LintiMenuRow(
                                icon = Icons.Outlined.TaskAlt,
                                title = "练习设置",
                                subtitle = "掌握判定 · 每日新卡",
                                onClick = { onNavigate(Routes.SettingsStudy) },
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(title = "智能服务")
                    LintiCard(shapeRadius = 20.dp) {
                        Column {
                            LintiMenuRow(
                                icon = Icons.Outlined.AutoAwesome,
                                title = "AI 服务",
                                subtitle = if (settings.hasApiKey) "已保存 Key · deepseek-flash" else "未配置",
                                onClick = { onNavigate(Routes.SettingsAi) },
                            )
                            ThinDivider()
                            LintiMenuRow(
                                icon = Icons.Outlined.PhotoCamera,
                                title = "OCR 识别",
                                subtitle = if (settings.hasOcrApiKey) "已保存独立 Key" else "未配置或复用 AI Key",
                                onClick = { onNavigate(Routes.SettingsOcr) },
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader(title = "数据与关于")
                    LintiCard(shapeRadius = 20.dp) {
                        Column {
                            LintiMenuRow(
                                icon = Icons.Outlined.Storage,
                                title = "数据备份",
                                subtitle = "导出 · 恢复 · 示例题库",
                                onClick = { onNavigate(Routes.SettingsData) },
                            )
                            ThinDivider()
                            LintiMenuRow(
                                icon = Icons.Outlined.Info,
                                title = "关于与更新",
                                subtitle = "v${BuildConfig.VERSION_NAME}",
                                onClick = { onNavigate(Routes.SettingsAbout) },
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    text = "密钥本机加密存储，识别与生成仅走 HTTPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileStatCell(
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLintiPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(palette.surface.copy(alpha = 0.72f))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
            color = color,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.textSecondary,
        )
    }
}