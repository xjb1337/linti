package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.import.ConfirmImportResult
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.MetricItem
import com.xjb.linti.ui.components.MetricStrip
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.components.WarningNote
import com.xjb.linti.ui.components.QuestionDraftEditor
import kotlinx.coroutines.launch

@Composable
fun ImportReviewScreen(
    viewModel: LintiViewModel,
    onBack: () -> Unit,
    onImported: (Long) -> Unit,
) {
    val state by viewModel.importState.collectAsStateWithLifecycle()
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var expandedConfirmed by remember(state.jobId, state.fileName) {
        mutableStateOf<Set<Int>>(emptySet())
    }

    if (state.drafts.isEmpty() && state.error == null) {
        Column(modifier = Modifier.fillMaxSize()) {
            LintiPageHeader(
                title = "校对并入库",
                subtitle = state.fileName.ifBlank { null },
                onBack = onBack,
            )
            LintiCard(
                modifier = Modifier.padding(20.dp),
                containerColor = LocalLintiPalette.current.primaryPale,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        text = "暂无可校对题目。请返回首页重新选择文件，或使用手动录入。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                    if (state.warnings.isNotEmpty()) {
                        state.warnings.forEach { warning ->
                            WarningNote(message = warning)
                        }
                    }
                }
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LintiPageHeader(
            title = "校对并入库",
            subtitle = state.fileName,
            onBack = onBack,
            actions = {
                StatusPill(
                    text = if (state.method == com.xjb.linti.domain.ImportMethod.AI_ONLY) {
                        "AI 识别"
                    } else {
                        "本地优先"
                    },
                    color = if (state.method == com.xjb.linti.domain.ImportMethod.AI_ONLY) {
                        LocalLintiPalette.current.aiAccent
                    } else {
                        LocalLintiPalette.current.primary
                    },
                    background = if (state.method == com.xjb.linti.domain.ImportMethod.AI_ONLY) {
                        LocalLintiPalette.current.aiSoft
                    } else {
                        LocalLintiPalette.current.primaryPale
                    },
                )
            },
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                Text(
                    text = "识别结果由你最终确认。待确认题允许先入库，但会保留待确认状态。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
            item {
                MetricStrip(
                    items = listOf(
                        MetricItem(state.recognizedCount.toString(), "识别"),
                        MetricItem(
                            state.confirmedCount.toString(),
                            "已确认",
                            LocalLintiPalette.current.success,
                        ),
                        MetricItem(
                            state.pendingCount.toString(),
                            "待确认",
                            LocalLintiPalette.current.warning,
                        ),
                        MetricItem(state.failedCount.toString(), "失败"),
                    ),
                )
            }
            if (state.warnings.isNotEmpty()) {
                item {
                    WarningNote(
                        message = state.warnings.joinToString("；"),
                    )
                }
            }
            if (state.error != null) {
                item {
                    WarningNote(message = state.error.orEmpty())
                }
            }

            val confirmed = state.drafts.mapIndexedNotNull { index, draft ->
                if (!draft.needsReview) index to draft else null
            }
            val pending = state.drafts.mapIndexedNotNull { index, draft ->
                if (draft.needsReview) index to draft else null
            }

            if (confirmed.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader(
                            title = "已确认 ${confirmed.size} 题",
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = {
                                viewModel.showMessage("已确认题可点开继续修改")
                            },
                        ) {
                            Text("折叠显示", color = LocalLintiPalette.current.textSecondary)
                        }
                    }
                }
                items(confirmed, key = { "confirmed-${it.first}" }) { pair ->
                    val index = pair.first
                    val draft = state.drafts[index]
                    val expanded = index in expandedConfirmed
                    ConfirmedDraftRow(
                        draft = draft,
                        expanded = expanded,
                        onToggle = {
                            expandedConfirmed = if (expanded) {
                                expandedConfirmed - index
                            } else {
                                expandedConfirmed + index
                            }
                        },
                    )
                    if (expanded) {
                        QuestionDraftEditor(
                            draft = draft,
                            index = index,
                            onChange = { viewModel.updateImportDraft(index, it) },
                        )
                    }
                }
            }

            if (pending.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "待确认 ${pending.size} 题",
                        trailing = "点“确认此题”后归入已确认",
                        trailingColor = LocalLintiPalette.current.warning,
                        onTrailingClick = null,
                    )
                }
                items(pending, key = { "pending-${it.first}" }) { pair ->
                    val index = pair.first
                    QuestionDraftEditor(
                        draft = state.drafts[index],
                        index = index,
                        onChange = { viewModel.updateImportDraft(index, it) },
                    )
                }
            }

            item {
                TargetBankSection(
                    selectedBankId = state.targetBankId,
                    newBankName = state.newBankName,
                    banks = banks.map { it.id to it.name },
                    onSelectBank = viewModel::updateImportTargetBank,
                    onNewBankNameChange = viewModel::updateImportNewBankName,
                )
            }

            item {
                LintiCard(containerColor = LocalLintiPalette.current.background, borderColor = LocalLintiPalette.current.line) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = LocalLintiPalette.current.primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "入库后可继续在题库中编辑；原文件片段会随题保存，便于回查。",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.textSecondary,
                        )
                    }
                }
            }
        }

        Surface(
            color = LocalLintiPalette.current.surface,
            shadowElevation = 8.dp,
        ) {
            Column {
                ThinDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LocalLintiPalette.current.background,
                            contentColor = LocalLintiPalette.current.textPrimary,
                        ),
                    ) {
                        Text("返回修改")
                    }
                    PrimaryActionButton(
                        text = if (state.isConfirming) {
                            "正在入库…"
                        } else {
                            "确认入库 ${state.drafts.size} 题"
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isConfirming &&
                            state.drafts.isNotEmpty() &&
                            (state.targetBankId != null || state.newBankName.isNotBlank()),
                        onClick = {
                            scope.launch {
                                when (val result = viewModel.confirmImport()) {
                                    is ConfirmImportResult.Success -> {
                                        onImported(result.value.bankId)
                                    }
                                    is ConfirmImportResult.Failure -> Unit
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmedDraftRow(
    draft: DraftQuestion,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    LintiCard(
        shapeRadius = 12.dp,
        containerColor = LocalLintiPalette.current.surface,
        onClick = onToggle,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = draft.sourceNumber.ifBlank { "—" },
                style = MaterialTheme.typography.labelMedium,
                color = LocalLintiPalette.current.textSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.stem.ifBlank { "（空题干）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalLintiPalette.current.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${draft.type.label} · 答案 ${draft.answer.ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
            StatusPill("已确认", color = LocalLintiPalette.current.success, background = LocalLintiPalette.current.successSoft)
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.ExpandLess
                } else {
                    Icons.Outlined.ExpandMore
                },
                contentDescription = if (expanded) "收起" else "展开",
                tint = LocalLintiPalette.current.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}


@Composable
private fun TargetBankSection(
    selectedBankId: Long?,
    newBankName: String,
    banks: List<Pair<Long, String>>,
    onSelectBank: (Long?) -> Unit,
    onNewBankNameChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(title = "目标题库", trailing = "必须选择或新建")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            banks.forEach { (id, name) ->
                FilterChip(
                    selected = selectedBankId == id,
                    onClick = { onSelectBank(id) },
                    label = {
                        Text(
                            text = name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            FilterChip(
                selected = selectedBankId == null,
                onClick = { onSelectBank(null) },
                label = { Text("新建题库") },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
        if (selectedBankId == null) {
            OutlinedTextField(
                value = newBankName,
                onValueChange = onNewBankNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新题库名称") },
                placeholder = { Text("例如：内科学复习题集") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(12.dp),
            )
        } else {
            LintiCard(containerColor = LocalLintiPalette.current.primaryPale, borderColor = LocalLintiPalette.current.primarySoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = LocalLintiPalette.current.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "确认后将追加到现有题库，不会覆盖原有题目。",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                }
            }
        }
    }
}

