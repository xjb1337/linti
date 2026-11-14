package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
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
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.import.ConfirmImportResult
import com.xjb.linti.data.import.SplitDraftProblem
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.OcrImportSource
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.MetricItem
import com.xjb.linti.ui.components.MetricStrip
import com.xjb.linti.ui.components.OutlinedActionButton
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
    var confirmedExpanded by remember(state.jobId, state.fileName) { mutableStateOf(true) }
    // OCR 导入：查看草稿对应原文/原图/原页
    var viewingSourceIndex by remember(state.jobId, state.fileName) { mutableStateOf<Int?>(null) }

    // 离开校对页（入库成功或放弃校对）：删除 OCR 对照临时文件并重置导入状态
    DisposableEffect(Unit) {
        onDispose { viewModel.onReviewScreenLeft() }
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
                    EmptyState(
                        title = "暂无可校对题目",
                        action = {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedActionButton(text = "返回", onClick = onBack)
                                OutlinedActionButton(
                                    text = "手动录入",
                                    onClick = {
                                        scope.launch {
                                            viewModel.startManualEntry()
                                        }
                                    },
                                )
                            }
                        },
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
            subtitle = if (state.isSplitFile) {
                "试题 ${state.fileName} · 答案 ${state.answerFileName}"
            } else {
                state.fileName
            },
            onBack = onBack,
            actions = {
                val ocrSource = state.ocrSource
                StatusPill(
                    text = when {
                        state.isSplitFile -> "分文件匹配"
                        ocrSource != null -> ocrSource.label
                        state.method == com.xjb.linti.domain.ImportMethod.AI_ONLY -> "AI 识别"
                        else -> "本地优先"
                    },
                    color = if (state.isSplitFile ||
                        ocrSource != null ||
                        state.method != com.xjb.linti.domain.ImportMethod.AI_ONLY
                    ) {
                        LocalLintiPalette.current.primary
                    } else {
                        LocalLintiPalette.current.aiAccent
                    },
                    background = if (state.isSplitFile ||
                        ocrSource != null ||
                        state.method != com.xjb.linti.domain.ImportMethod.AI_ONLY
                    ) {
                        LocalLintiPalette.current.primaryPale
                    } else {
                        LocalLintiPalette.current.aiSoft
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
                                confirmedExpanded = !confirmedExpanded
                            },
                        ) {
                            Text(
                                if (confirmedExpanded) "收起" else "展开",
                                color = LocalLintiPalette.current.textSecondary,
                            )
                        }
                    }
                }
                if (confirmedExpanded) {
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
                            if (state.ocrSource != null) {
                                OcrSourceRow(
                                    source = state.ocrSource ?: return@items,
                                    page = state.sourcePages[index],
                                    snippet = draft.rawSnippet,
                                    onView = { viewingSourceIndex = index },
                                )
                            }
                        }
                    }
                }
            }

            if (pending.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "待确认 ${pending.size} 题",
                    )
                }
                items(pending, key = { "pending-${it.first}" }) { pair ->
                    val index = pair.first
                    QuestionDraftEditor(
                        draft = state.drafts[index],
                        index = index,
                        onChange = { viewModel.updateImportDraft(index, it) },
                    )
                    if (state.ocrSource != null) {
                        OcrSourceRow(
                            source = state.ocrSource ?: return@items,
                            page = state.sourcePages[index],
                            snippet = state.drafts[index].rawSnippet,
                            onView = { viewingSourceIndex = index },
                        )
                    }
                    val problem = state.splitProblems[index]
                    if (problem is SplitDraftProblem.Conflict) {
                        SplitConflictActions(
                            problem = problem,
                            onUseExternal = { viewModel.resolveSplitConflict(index, useExternalAnswer = true) },
                            onKeepInline = { viewModel.resolveSplitConflict(index, useExternalAnswer = false) },
                        )
                    }
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
                    val ocrBlocked = state.saveBlocked
                    PrimaryActionButton(
                        text = when {
                            state.isConfirming -> "正在入库…"
                            ocrBlocked -> "先处理 ${state.pendingCount} 题待核对"
                            state.targetBankId != null -> "追加 ${state.drafts.size} 题"
                            else -> "创建并入库 ${state.drafts.size} 题"
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isConfirming &&
                            state.drafts.isNotEmpty() &&
                            !ocrBlocked &&
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

    viewingSourceIndex?.let { index ->
        OcrSourceViewer(
            state = state,
            index = index,
            onDismiss = { viewingSourceIndex = null },
        )
    }
}

// 分文件冲突的逐题解决：明确选择采用答案文件的值还是保留题内答案
@Composable
private fun SplitConflictActions(
    problem: SplitDraftProblem.Conflict,
    onUseExternal: () -> Unit,
    onKeepInline: () -> Unit,
) {    LintiCard(
        shapeRadius = 12.dp,
        containerColor = LocalLintiPalette.current.warningSoft,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "题内 ${problem.inlineAnswer} · 答案文件 ${problem.externalAnswer}",
                style = MaterialTheme.typography.labelSmall,
                color = LocalLintiPalette.current.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUseExternal) {
                Text("采用外部", color = LocalLintiPalette.current.primary)
            }
            TextButton(onClick = onKeepInline) {
                Text("保留题内", color = LocalLintiPalette.current.primary)
            }
        }
    }
}

@Composable
private fun ConfirmedDraftRow(
    draft: DraftQuestion,
    expanded: Boolean,
    onToggle: () -> Unit,
) {    LintiCard(
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
        SectionHeader(title = "目标题库")
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
        }
    }
}

// OCR 导入：草稿的页码来源与原文对照入口（原文不落库，仅校对期间可见）
@Composable
private fun OcrSourceRow(
    source: OcrImportSource,
    page: Int?,
    snippet: String?,
    onView: () -> Unit,
) {
    LintiCard(shapeRadius = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (source) {
                        OcrImportSource.IMAGE -> "来源：原图"
                        OcrImportSource.PDF -> "来源：第 ${page ?: "?"} 页"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalLintiPalette.current.textSecondary,
                )
                if (!snippet.isNullOrBlank()) {
                    Text(
                        text = snippet.trim().replace("\n", " ").take(80),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalLintiPalette.current.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onView) {
                Text(
                    text = if (source == OcrImportSource.IMAGE) "查看原图" else "查看原页",
                    color = LocalLintiPalette.current.primary,
                )
            }
        }
    }
}

// 原文对照查看：图片显示上传副本，PDF 用系统 PdfRenderer 渲染指定页（临时文件，不入库）
@Composable
private fun OcrSourceViewer(
    state: com.xjb.linti.ui.ImportUiState,
    index: Int,
    onDismiss: () -> Unit,
) {
    val draft = state.drafts.getOrNull(index) ?: return
    val page = state.sourcePages[index]
    val ocrSource = state.ocrSource
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (ocrSource) {
                    OcrImportSource.IMAGE -> "原图对照 · 第 ${index + 1} 条"
                    OcrImportSource.PDF -> "原页对照 · 第 ${index + 1} 条（第 ${page ?: "?"} 页）"
                    else -> "原文对照"
                },
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (ocrSource) {
                    OcrImportSource.IMAGE -> state.originalPreviewPath?.let { path ->
                        val bitmap = remember(path) {
                            BitmapFactory.decodeFile(path)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "对照原图",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text("原图临时文件已清理，无法显示")
                        }
                    }
                    OcrImportSource.PDF -> state.pdfPreviewPath?.let { path ->
                        page?.let { page ->
                            val bitmap = remember(path, page) {
                                com.xjb.linti.data.ocr.OcrSourcePageRenderer.renderPage(path, page - 1)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "对照原页",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text("原页渲染失败，文件可能已清理或损坏")
                            }
                        }
                    }
                    else -> Unit
                }
                if (!draft.rawSnippet.isNullOrBlank()) {
                    Surface(
                        color = LocalLintiPalette.current.background,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = draft.rawSnippet.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalLintiPalette.current.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}
