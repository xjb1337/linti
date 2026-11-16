package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.BuildConfig
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.StudySummary
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiMenuRow
import com.xjb.linti.ui.components.LintiPageHeader
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
    var summary by remember { mutableStateOf<StudySummary?>(null) }

    LaunchedEffect(banks.size, banks.sumOf { it.sequenceAnsweredCount }) {
        summary = viewModel.studySummary()
    }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("我的", pageListState) {
        LazyColumn(
            state = pageListState,
            modifier = Modifier.testTag("settings-list"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LintiPageHeader(title = "我的")
            }
            item {
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
                        ThinDivider()
                        LintiMenuRow(
                            icon = Icons.Outlined.AutoAwesome,
                            title = "AI 服务",
                            subtitle = if (settings.hasApiKey) "已保存 Key · ${settings.provider.label}" else "未配置",
                            onClick = { onNavigate(Routes.SettingsAi) },
                        )
                        ThinDivider()
                        LintiMenuRow(
                            icon = Icons.Outlined.PhotoCamera,
                            title = "OCR 识别",
                            subtitle = if (settings.hasOcrApiKey) "已保存独立 Key" else "未配置或复用 AI Key",
                            onClick = { onNavigate(Routes.SettingsOcr) },
                        )
                        ThinDivider()
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
            item {
                Text(
                    text = "密钥本机加密存储，识别与生成仅走 HTTPS",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
        }
    }
}