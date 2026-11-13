package com.xjb.linti.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.import.ConfirmImportResult
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
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
import com.xjb.linti.ui.theme.Background
import com.xjb.linti.ui.theme.Line
import com.xjb.linti.ui.theme.Primary
import com.xjb.linti.ui.theme.PrimaryPale
import com.xjb.linti.ui.theme.PrimarySoft
import com.xjb.linti.ui.theme.Success
import com.xjb.linti.ui.theme.SuccessSoft
import com.xjb.linti.ui.theme.Surface
import com.xjb.linti.ui.theme.TextPrimary
import com.xjb.linti.ui.theme.TextSecondary
import com.xjb.linti.ui.theme.Warning
import com.xjb.linti.ui.theme.WarningSoft
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
                containerColor = PrimaryPale,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        text = "暂无可校对题目。请返回首页重新选择文件，或使用手动录入。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
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
                        com.xjb.linti.ui.theme.AiAccent
                    } else {
                        Primary
                    },
                    background = if (state.method == com.xjb.linti.domain.ImportMethod.AI_ONLY) {
                        com.xjb.linti.ui.theme.AiSoft
                    } else {
                        PrimaryPale
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
                    color = TextSecondary,
                )
            }
            item {
                MetricStrip(
                    items = listOf(
                        MetricItem(state.recognizedCount.toString(), "识别"),
                        MetricItem(
                            state.confirmedCount.toString(),
                            "已确认",
                            Success,
                        ),
                        MetricItem(
                            state.pendingCount.toString(),
                            "待确认",
                            Warning,
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
                            Text("折叠显示", color = TextSecondary)
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
                        trailingColor = Warning,
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
                LintiCard(containerColor = Background, borderColor = Line) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "入库后可继续在题库中编辑；原文件片段会随题保存，便于回查。",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }

        Surface(
            color = Surface,
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
                            containerColor = Background,
                            contentColor = TextPrimary,
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
        containerColor = Surface,
        onClick = onToggle,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = draft.sourceNumber.ifBlank { "—" },
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = draft.stem.ifBlank { "（空题干）" },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${draft.type.label} · 答案 ${draft.answer.ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }
            StatusPill("已确认", color = Success, background = SuccessSoft)
            Spacer(Modifier.width(3.dp))
            Icon(
                imageVector = if (expanded) {
                    Icons.Outlined.ExpandLess
                } else {
                    Icons.Outlined.ExpandMore
                },
                contentDescription = if (expanded) "收起" else "展开",
                tint = TextSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

@Composable
private fun QuestionDraftEditor(
    draft: DraftQuestion,
    index: Int,
    onChange: (DraftQuestion) -> Unit,
) {
    LintiCard(
        shapeRadius = 15.dp,
        containerColor = if (draft.needsReview) ColorWarningPale else Surface,
        borderColor = if (draft.needsReview) ColorWarningBorder else Line,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "第 ${index + 1} 条 · 原题号 ${draft.sourceNumber.ifBlank { "—" }}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (draft.needsReview) {
                    StatusPill("待核对", color = Warning, background = WarningSoft)
                } else {
                    StatusPill("已确认", color = Success, background = SuccessSoft)
                }
            }
            if (draft.reviewReasons.isNotEmpty()) {
                WarningNote(
                    message = "需核对：" + draft.reviewReasons.joinToString("；"),
                )
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QuestionType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = { onChange(draft.copy(type = type)) },
                        label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PrimarySoft,
                            selectedLabelColor = Primary,
                        ),
                    )
                }
            }
            OutlinedTextField(
                value = draft.stem,
                onValueChange = { onChange(draft.copy(stem = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("题干") },
                minLines = 2,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(10.dp),
            )
            if (draft.type == QuestionType.SINGLE || draft.type == QuestionType.MULTI) {
                draft.options.forEachIndexed { optionIndex, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = option.label.ifBlank {
                                ('A'.code + optionIndex).toChar().toString()
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextSecondary,
                            modifier = Modifier.width(22.dp),
                        )
                        OutlinedTextField(
                            value = option.text,
                            onValueChange = { text ->
                                val options = draft.options.toMutableList()
                                options[optionIndex] = option.copy(text = text)
                                onChange(draft.copy(options = options))
                            },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("选项内容", style = MaterialTheme.typography.bodySmall) },
                            singleLine = false,
                            maxLines = 3,
                            textStyle = MaterialTheme.typography.bodySmall,
                            shape = RoundedCornerShape(9.dp),
                        )
                        if (draft.options.size > 2) {
                            IconButton(
                                onClick = {
                                    val options = draft.options
                                        .filterIndexed { current, _ -> current != optionIndex }
                                        .mapIndexed { current, old ->
                                            old.copy(
                                                label = ('A'.code + current).toChar().toString(),
                                            )
                                        }
                                    onChange(draft.copy(options = options))
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "删除选项",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                if (draft.options.size < 8) {
                    TextButton(
                        onClick = {
                            val label = ('A'.code + draft.options.size).toChar().toString()
                            onChange(
                                draft.copy(
                                    options = draft.options + QuestionOption(label, ""),
                                ),
                            )
                        },
                    ) {
                        Icon(
                            Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("添加选项")
                    }
                }
            }
            if (draft.type == QuestionType.JUDGE) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.answer == "true",
                        onClick = { onChange(draft.copy(answer = "true")) },
                        label = { Text("正确") },
                    )
                    FilterChip(
                        selected = draft.answer == "false",
                        onClick = { onChange(draft.copy(answer = "false")) },
                        label = { Text("错误") },
                    )
                }
            } else {
                OutlinedTextField(
                    value = draft.answer,
                    onValueChange = { onChange(draft.copy(answer = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (draft.type == QuestionType.FILL) {
                                "标准答案（完全一致计分）"
                            } else {
                                "答案（多选如 ABD）"
                            },
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    shape = RoundedCornerShape(10.dp),
                )
            }
            OutlinedTextField(
                value = draft.explanation,
                onValueChange = { onChange(draft.copy(explanation = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("解析（可空）") },
                minLines = 2,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(10.dp),
            )
            OutlinedTextField(
                value = draft.knowledgePoint.orEmpty(),
                onValueChange = { onChange(draft.copy(knowledgePoint = it.ifBlank { null })) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("知识点 / 标签（可空）") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(10.dp),
            )
            if (draft.needsReview) {
                Button(
                    onClick = {
                        onChange(
                            draft.copy(
                                needsReview = false,
                                reviewReasons = emptyList(),
                                confidence = 1.0,
                            ),
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Success,
                        contentColor = Surface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("核对完成，确认此题")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "此题已确认，仍可在入库前修改",
                        style = MaterialTheme.typography.labelSmall,
                        color = Success,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            onChange(
                                draft.copy(
                                    needsReview = true,
                                    reviewReasons = listOf("用户重新标记为待核对"),
                                ),
                            )
                        },
                    ) {
                        Text("重新标记", color = Warning)
                    }
                }
            }
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
            LintiCard(containerColor = PrimaryPale, borderColor = PrimarySoft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "确认后将追加到现有题库，不会覆盖原有题目。",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                    )
                }
            }
        }
    }
}

private val ColorWarningPale = androidx.compose.ui.graphics.Color(0xFFFFFCF4)
private val ColorWarningBorder = androidx.compose.ui.graphics.Color(0xFFE9C887)
