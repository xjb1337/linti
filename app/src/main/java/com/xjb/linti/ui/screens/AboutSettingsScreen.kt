package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.BuildConfig
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.SettingSwitchRow
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.theme.LocalLintiPalette

@Composable
fun AboutSettingsScreen(viewModel: LintiViewModel, onBack: () -> Unit) {
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("关于与更新", pageListState, onBack = onBack) {
        LazyColumn(
            state = pageListState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LintiPageHeader(title = "关于与更新", onBack = onBack)
            }
            item {
                LintiCard(shapeRadius = 20.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Download,
                                contentDescription = null,
                                tint = LocalLintiPalette.current.primary,
                                modifier = Modifier.size(21.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "临题 v${BuildConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = LocalLintiPalette.current.textPrimary,
                                )
                                updateState.release?.let { release ->
                                    Text(
                                        text = "新版本 v${release.version}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalLintiPalette.current.warning,
                                    )
                                }
                            }
                            OutlinedActionButton(
                                text = if (updateState.checking) "检查中…" else "检查更新",
                                enabled = !updateState.checking && !updateState.downloading,
                                onClick = { viewModel.checkForUpdates() },
                            )
                        }
                        ThinDivider()
                        SettingSwitchRow(
                            title = "启动时检查更新",
                            checked = autoCheckUpdates,
                            onCheckedChange = viewModel::setAutoCheckUpdates,
                        )
                    }
                }
            }
            item {
                Text(
                    text = "临题是一款本地优先的刷题工具：题库与作答保存在本机，AI 能力可选配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalLintiPalette.current.textSecondary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}