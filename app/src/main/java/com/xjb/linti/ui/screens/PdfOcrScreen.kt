package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.import.PdfOcrMergeMode
import com.xjb.linti.data.ocr.OcrKeySource
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.OcrPhase
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.components.WarningNote
import com.xjb.linti.ui.theme.LocalLintiPalette
import kotlinx.coroutines.launch

/**
 * 扫描 PDF 在线识别确认页：本地逐页检查后，把「将上传整份文件」明确告知用户，
 * 由用户主动选择识别方式后才上传；本地解析失败时提供整份识别的尝试入口。
 */
@Composable
fun PdfOcrScreen(
    viewModel: LintiViewModel,
    onBack: () -> Unit,
    onRecognized: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.pdfOcrState.collectAsStateWithLifecycle()
    val keyState = viewModel.ocrKeyState()

    DisposableEffect(Unit) {
        onDispose { viewModel.onPdfOcrScreenLeft() }
    }

    var pendingMode by remember { mutableStateOf<PdfOcrMergeMode?>(null) }
    var showBillingConfirm by remember { mutableStateOf(false) }

    fun submit(mode: PdfOcrMergeMode, billingConfirmed: Boolean = false) {
        scope.launch {
            val recognized = viewModel.submitPdfOcr(mode, billingConfirmed)
            if (recognized) onRecognized()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LintiPageHeader(
            title = "扫描页在线识别",
            subtitle = state.fileName.ifBlank { null },
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatusPill(
                            text = "共 ${state.pageCount} 页",
                            color = LocalLintiPalette.current.primary,
                            background = LocalLintiPalette.current.primaryPale,
                        )
                        StatusPill(
                            text = "文字页 ${state.usablePageCount}",
                            color = LocalLintiPalette.current.success,
                            background = LocalLintiPalette.current.successSoft,
                        )
                        if (state.scanPages.isNotEmpty()) {
                            StatusPill(
                                text = "扫描页 ${state.scanPages.size}",
                                color = LocalLintiPalette.current.warning,
                                background = LocalLintiPalette.current.warningSoft,
                            )
                        }
                    }
                    Text(
                        text = if (state.scanPages.isNotEmpty()) {
                            "未提取到可用文字层的页面：" +
                                state.scanPages.joinToString("、") { "第 ${it + 1} 页" }
                        } else {
                            "本地未能解析出文字层（详见下方说明）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                    ThinDivider()
                    Text(
                        text = "整份 PDF 文件将上传至 DeepSeek（官方在线识别，仅转写文字，不做生成式解析）",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLintiPalette.current.warning,
                    )
                    Text(
                        text = "识别结果按页合并：可用文字页优先保留本地提取结果，扫描页采用在线识别结果",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                    Text(
                        text = "文件大小上限 100 MB、页数上限 1000 页；识别按页计费，取消不一定会停止计费",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                }
            }
            if (state.loadErrorMessage != null) {
                WarningNote(
                    message = state.loadErrorMessage.orEmpty() +
                        "；可以尝试整份在线识别（文件仍将上传至 DeepSeek），但服务端同样可能无法解析加密文件",
                )
            }
            LintiCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = "选择识别方式")
                    if (state.scanPages.isNotEmpty()) {
                        Text(
                            text = "识别后按页合并：可用文字页保留本地提取，扫描页采用在线识别",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                        PrimaryActionButton(
                            text = "识别扫描页并合并原文",
                            enabled = !state.isRunning && keyState != OcrKeySource.None,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                if (state.pendingBillingConfirm) {
                                    pendingMode = PdfOcrMergeMode.SCANS_ONLY
                                    showBillingConfirm = true
                                } else {
                                    submit(PdfOcrMergeMode.SCANS_ONLY)
                                }
                            },
                        )
                    }
                    Text(
                        text = "整份采用在线识别：全部页面以上传结果为准",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                    OutlinedActionButton(
                        text = "整份使用 OCR 结果",
                        enabled = !state.isRunning && keyState != OcrKeySource.None &&
                            (state.scanPages.isNotEmpty() || state.loadErrorMessage != null),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (state.pendingBillingConfirm) {
                                pendingMode = PdfOcrMergeMode.FULL_OCR
                                showBillingConfirm = true
                            } else {
                                submit(PdfOcrMergeMode.FULL_OCR)
                            }
                        },
                    )
                    when (keyState) {
                        OcrKeySource.Dedicated -> Text(
                            text = "使用独立配置的 DeepSeek OCR Key",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.success,
                        )
                        OcrKeySource.ReuseAi -> Text(
                            text = "复用已配置的 DeepSeek 官方 Key",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.success,
                        )
                        OcrKeySource.None -> Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "尚未配置 DeepSeek OCR Key，上传前需要先配置",
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalLintiPalette.current.warning,
                            )
                            OutlinedActionButton(
                                text = "去设置",
                                enabled = !state.isRunning,
                                onClick = onOpenSettings,
                            )
                        }
                    }
                    state.error?.let { error ->
                        WarningNote(message = error)
                    }
                    if (state.isRunning && state.statusText.isNotEmpty()) {
                        Text(
                            text = state.statusText,
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalLintiPalette.current.primary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
        Surface(color = LocalLintiPalette.current.surface) {
            Column {
                ThinDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.isRunning) {
                        OutlinedActionButton(
                            text = "取消识别",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.cancelOcr() },
                        )
                    } else {
                        OutlinedActionButton(
                            text = "返回",
                            modifier = Modifier.weight(1f),
                            onClick = onBack,
                        )
                    }
                    PrimaryActionButton(
                        text = when {
                            state.phase == OcrPhase.RUNNING -> "上传识别中…"
                            state.phase == OcrPhase.FAILED && state.pendingBillingConfirm ->
                                "重试（可能重复计费）"
                            else -> "上传并识别"
                        },
                        modifier = Modifier.weight(1.6f),
                        enabled = !state.isRunning &&
                            keyState != OcrKeySource.None &&
                            (state.scanPages.isNotEmpty() || state.loadErrorMessage != null),
                        onClick = {
                            val mode = if (state.scanPages.isNotEmpty()) {
                                PdfOcrMergeMode.SCANS_ONLY
                            } else {
                                PdfOcrMergeMode.FULL_OCR
                            }
                            if (state.pendingBillingConfirm) {
                                pendingMode = mode
                                showBillingConfirm = true
                            } else {
                                submit(mode)
                            }
                        },
                    )
                }
            }
        }
    }

    if (showBillingConfirm) {
        val mode = pendingMode ?: PdfOcrMergeMode.SCANS_ONLY
        AlertDialog(
            onDismissRequest = { showBillingConfirm = false },
            title = { Text("确认重试？") },
            text = {
                Text(
                    "上次请求可能仍在处理。重试可能对同一文件重复计费。",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showBillingConfirm = false
                        submit(mode, billingConfirmed = true)
                    },
                ) {
                    Text("仍要重试")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBillingConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }
}
