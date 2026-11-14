package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.SplitSlotUiState
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.WarningNote
import kotlinx.coroutines.launch

@Composable
fun SplitImportScreen(
    viewModel: LintiViewModel,
    onBack: () -> Unit,
    onMatched: () -> Unit,
) {
    val split by viewModel.splitImportState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { viewModel.onSplitScreenLeft() }
    }
    BackHandler(onBack = onBack)

    val questionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.selectSplitQuestionFile(uri) }
    val answerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> viewModel.selectSplitAnswerFile(uri) }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        LintiPageHeader(
            title = "试题/答案分文件",
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SplitFileSlotCard(
                title = "试题文件",
                hint = "支持 TXT、DOCX、PDF（需含可提取文字）",
                slot = split.question,
                icon = Icons.Outlined.Description,
                onSelect = {
                    questionLauncher.launch(
                        arrayOf(
                            "text/plain",
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/msword",
                        ),
                    )
                },
            )
            SplitFileSlotCard(
                title = "答案文件",
                hint = "支持 TXT、CSV、XLSX（首个工作表）",
                slot = split.answer,
                icon = Icons.Outlined.TableChart,
                onSelect = {
                    answerLauncher.launch(
                        arrayOf(
                            "text/plain",
                            "text/csv",
                            "text/comma-separated-values",
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel",
                        ),
                    )
                },
            )
            if (split.matchError != null) {
                WarningNote(message = split.matchError.orEmpty())
            }
            Spacer(Modifier.height(4.dp))
            PrimaryActionButton(
                text = if (split.isMatching) "正在匹配…" else "匹配并校对",
                enabled = split.canMatch,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        if (viewModel.matchSplitFiles()) {
                            onMatched()
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun SplitFileSlotCard(
    title: String,
    hint: String,
    slot: SplitSlotUiState,
    icon: ImageVector,
    onSelect: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    LintiCard {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.width(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (slot.isSelected) {
                    StatusPill(
                        text = "已选择",
                        color = palette.success,
                        background = palette.successSoft,
                    )
                }
            }
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = palette.textSecondary,
            )
            if (slot.isSelected) {
                Text(
                    text = slot.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (slot.error != null) {
                WarningNote(message = slot.error.orEmpty())
            }
            OutlinedActionButton(
                text = if (slot.isSelected) "更换文件" else "选择文件",
                icon = if (slot.isSelected) Icons.Outlined.UploadFile else Icons.Outlined.CallSplit,
                modifier = Modifier.fillMaxWidth(),
                onClick = onSelect,
            )
        }
    }
}
