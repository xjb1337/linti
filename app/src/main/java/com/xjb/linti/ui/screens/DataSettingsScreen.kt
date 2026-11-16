package com.xjb.linti.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiConfirmationSheet
import com.xjb.linti.ui.components.LintiMenuRow
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.ThinDivider
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun DataSettingsScreen(viewModel: LintiViewModel, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) scope.launch {
            runCatching { viewModel.exportBackup(uri) }
                .onSuccess { viewModel.showMessage("备份已导出") }
                .onFailure { viewModel.showMessage("导出失败：${it.message ?: "无法写入文件"}") }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingRestoreUri = uri }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("数据备份", pageListState, onBack = onBack) {
        LazyColumn(
            state = pageListState,
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LintiPageHeader(title = "数据备份", onBack = onBack)
            }
            item {
                LintiCard(shapeRadius = 20.dp) {
                    Column {
                        LintiMenuRow(
                            icon = Icons.Outlined.Upload,
                            title = "导出备份",
                            subtitle = "题库、作答、错题、收藏、笔记、进度",
                            onClick = {
                                exportLauncher.launch("linti-backup-${LocalDate.now()}.zip")
                            },
                        )
                        ThinDivider()
                        LintiMenuRow(
                            icon = Icons.Outlined.Restore,
                            title = "恢复备份",
                            subtitle = "替换本机数据，不可撤销；建议先导出",
                            onClick = {
                                restoreLauncher.launch(
                                    arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream"),
                                )
                            },
                        )
                        ThinDivider()
                        LintiMenuRow(
                            icon = Icons.Outlined.LibraryBooks,
                            title = "载入示例题库",
                            subtitle = "10 题 · 虚构教材",
                            onClick = viewModel::loadSampleData,
                        )
                    }
                }
            }
        }
    }

    val restoreUri = pendingRestoreUri
    if (restoreUri != null) {
        LintiConfirmationSheet("恢复本机数据？", "这会替换当前题库、作答、错题、收藏、笔记和进度。该操作不可撤销，建议先导出当前数据。",
            "确认恢复", danger = true, onDismiss = { pendingRestoreUri = null }, onConfirm = {
                pendingRestoreUri = null
                scope.launch { viewModel.restoreBackup(restoreUri) }
            })
    }
}