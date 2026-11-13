package com.xjb.linti.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.theme.LocalLintiPalette
import java.util.Locale

@Composable
fun UpdateDialog(viewModel: LintiViewModel) {
    val state by viewModel.updateState.collectAsStateWithLifecycle()
    if (!state.dialogVisible) return
    val release = state.release
    val palette = LocalLintiPalette.current
    AlertDialog(
        onDismissRequest = {
            if (!state.downloading) viewModel.dismissUpdateDialog()
        },
        title = {
            Text(
                if (release != null) {
                    "发现新版本 v${release.version}"
                } else {
                    "检查更新"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (release != null) {
                    Text(
                        text = release.notes.ifBlank { "无更新说明" },
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textPrimary,
                    )
                }
                state.message?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.messageIsError) palette.error else palette.textPrimary,
                    )
                }
                if (state.downloading) {
                    val total = state.totalBytes
                    if (total > 0L) {
                        val fraction = (state.downloadedBytes.toFloat() / total).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = "下载中 ${(fraction * 100).toInt()}% · " +
                                "${formatBytes(state.downloadedBytes)} / ${formatBytes(total)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "下载中 ${formatBytes(state.downloadedBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                        )
                    }
                }
                if (state.permissionRequired) {
                    Text(
                        text = "需要允许安装未知应用",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.warning,
                    )
                }
            }
        },
        confirmButton = {
            when {
                state.downloading -> TextButton(onClick = viewModel::cancelUpdateDownload) {
                    Text("取消下载")
                }
                state.permissionRequired -> TextButton(
                    onClick = viewModel::openInstallPermissionSettings,
                ) {
                    Text("前往设置")
                }
                release != null -> TextButton(onClick = viewModel::downloadAndInstallUpdate) {
                    Text(if (state.message == null) "下载并安装" else "重试")
                }
                else -> TextButton(onClick = viewModel::dismissUpdateDialog) {
                    Text("知道了")
                }
            }
        },
        dismissButton = {
            if (!state.downloading && (release != null || state.permissionRequired)) {
                TextButton(onClick = viewModel::dismissUpdateDialog) {
                    Text("稍后")
                }
            }
        },
    )
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val megabytes = bytes / (1024.0 * 1024.0)
    return String.format(Locale.ROOT, "%.1f MB", megabytes)
}
