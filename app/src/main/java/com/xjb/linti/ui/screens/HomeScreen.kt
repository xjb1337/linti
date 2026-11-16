package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.PdfImportDecision
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.FeatureTile
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiChoiceSheet
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.formatDateTime
import java.io.File
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: LintiViewModel,
    onReview: () -> Unit,
    onSplitImport: () -> Unit,
    onImageOcr: () -> Unit,
    onPdfOcr: () -> Unit,
    onOpenBank: (Long) -> Unit,
    onAiQuestions: () -> Unit = {},
    onQuiz: () -> Unit = {},
    onSearch: () -> Unit = {},
    onExamSetup: (Long) -> Unit = {},
    onOpenBanks: () -> Unit = {},
    onWrongBook: () -> Unit = {},
    onFavorites: () -> Unit = {},
    onFlashcards: () -> Unit = {},
    onRecite: () -> Unit = {},
    onTopicPractice: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentImports by viewModel.recentImports.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lastPractice by viewModel.lastPracticeSession.collectAsStateWithLifecycle()
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val pendingWrongTotal = banks.sumOf { it.pendingWrongCount }
    val favoriteTotal = banks.sumOf { it.favoriteCount }
    var showExamPicker by remember { mutableStateOf(false) }
    var method by remember { mutableStateOf(ImportMethod.LOCAL_ONLY) }
    var pendingKind by remember { mutableStateOf(DocumentKind.TXT) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    // 拍照临时原件：复制进 OCR 预处理后立即删除（成功/失败/取消都清），选入的用户原文件不动
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val palette = LocalLintiPalette.current

    // 入口分流：图片/拍照 → DeepSeek 在线 OCR 预览页；含扫描页的 PDF → 确认页；
    // 其余沿用既有本地导入。选档/拍照本身不触发任何网络请求。
    fun handleUri(uri: Uri, kind: DocumentKind) {
        scope.launch {
            when (kind) {
                DocumentKind.IMAGE, DocumentKind.CAMERA -> {
                    if (viewModel.prepareImageOcr(uri, kind)) onImageOcr()
                }
                DocumentKind.PDF -> {
                    when (viewModel.importPdfFromUri(uri, method)) {
                        PdfImportDecision.ProceedLocal -> onReview()
                        PdfImportDecision.NeedsDecision -> onPdfOcr()
                        is PdfImportDecision.Failed -> Unit
                    }
                }
                else -> {
                    if (viewModel.importFromUri(uri, kind, method)) onReview()
                }
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            handleUri(uri, resolveDocumentKind(context, uri, pendingKind))
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) handleUri(uri, DocumentKind.IMAGE)
    }
    val anyDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            handleUri(uri, resolveDocumentKind(context, uri, DocumentKind.TXT))
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        val photo = cameraFile
        cameraFile = null
        scope.launch {
            if (success && uri != null) {
                if (viewModel.prepareImageOcr(uri, DocumentKind.CAMERA)) {
                    onImageOcr()
                }
            }
            // 无论成功/失败/取消，拍照临时原件在复制（或放弃）后即清理
            photo?.delete()
        }
    }

    fun openDocument(kind: DocumentKind) {
        pendingKind = kind
        val mimeTypes = when (kind) {
            DocumentKind.PDF -> arrayOf("application/pdf")
            DocumentKind.DOCX -> arrayOf(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/msword",
            )
            DocumentKind.XLSX -> arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel",
                "text/csv",
                "text/comma-separated-values",
            )
            DocumentKind.CSV -> arrayOf("text/csv", "text/comma-separated-values", "text/plain")
            DocumentKind.TXT -> arrayOf("text/plain")
            DocumentKind.IMAGE -> arrayOf("image/*")
            DocumentKind.CAMERA -> arrayOf("image/*")
        }
        documentLauncher.launch(mimeTypes)
    }

    fun openCamera() {
        val cameraDirectory = File(context.cacheDir, "camera").apply { mkdirs() }
        val photo = File.createTempFile("linti_camera_", ".jpg", cameraDirectory)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photo,
        )
        cameraUri = uri
        cameraFile = photo
        cameraLauncher.launch(uri)
    }

    val pageListState = rememberLazyListState()
    LintiCollapsingPage("临题", pageListState) {
        LazyColumn(
            state = pageListState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                LintiPageHeader(
                    title = "临题",
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(palette.surface)
                        .clickable(onClick = onSearch)
                        .semantics {
                            contentDescription = "搜索全部题库题目"
                            role = Role.Button
                        }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "搜索题干、答案、解析或知识点",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FeatureTile(
                        icon = Icons.Outlined.PhotoCamera,
                        label = "拍照导题",
                        onClick = ::openCamera,
                        modifier = Modifier.weight(1f),
                        iconColor = palette.primary,
                        iconBackground = palette.primaryPale,
                    )
                    FeatureTile(
                        icon = Icons.Outlined.UploadFile,
                        label = "上传题库",
                        onClick = {
                            anyDocumentLauncher.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/plain", "text/csv", "image/*"))
                        },
                        modifier = Modifier.weight(1f),
                        iconColor = palette.primary,
                        iconBackground = palette.primaryPale,
                    )
                    FeatureTile(
                        icon = Icons.Outlined.AutoAwesome,
                        label = "AI 出题",
                        onClick = onAiQuestions,
                        modifier = Modifier.weight(1f),
                        iconColor = palette.primary,
                        iconBackground = palette.primaryPale,
                    )
                    FeatureTile(
                        icon = Icons.Outlined.TaskAlt,
                        label = "发起考试",
                        onClick = {
                            when {
                                banks.isEmpty() -> viewModel.showMessage("先导入题库，再发起考试")
                                banks.size == 1 -> onExamSetup(banks.first().id)
                                else -> showExamPicker = true
                            }
                        },
                        modifier = Modifier.weight(1f),
                        iconColor = palette.primary,
                        iconBackground = palette.primaryPale,
                    )
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FeatureTile(
                        icon = Icons.Outlined.ErrorOutline, label = "错题本", onClick = onWrongBook, modifier = Modifier.weight(1f),
                        iconColor = palette.error, iconBackground = palette.errorSoft,
                        badge = pendingWrongTotal.takeIf { it > 0 }?.let { if (it > 99) "99+" else it.toString() },
                        contentDesc = pendingWrongTotal.takeIf { it > 0 }?.let { "错题本，待复习 $it 题" },
                    )
                    FeatureTile(
                        icon = Icons.Outlined.BookmarkBorder, label = "收藏", onClick = onFavorites, modifier = Modifier.weight(1f),
                        iconColor = palette.warning, iconBackground = palette.warningSoft,
                        badge = favoriteTotal.takeIf { it > 0 }?.let { if (it > 99) "99+" else it.toString() },
                        badgeColor = palette.warning, badgeBackground = palette.warningSoft,
                        contentDesc = favoriteTotal.takeIf { it > 0 }?.let { "收藏，共 $it 题" },
                    )
                    FeatureTile(
                        icon = Icons.Outlined.Style, label = "闪卡", onClick = onFlashcards, modifier = Modifier.weight(1f),
                        iconColor = palette.aiAccent, iconBackground = palette.aiSoft,
                    )
                    FeatureTile(
                        icon = Icons.Outlined.MenuBook, label = "背题", onClick = onRecite, modifier = Modifier.weight(1f),
                        iconColor = palette.success, iconBackground = palette.successSoft,
                    )
                    FeatureTile(
                        icon = Icons.Outlined.Tune, label = "专项", onClick = onTopicPractice, modifier = Modifier.weight(1f),
                        iconColor = palette.primary, iconBackground = palette.primaryPale,
                    )
                }
            }
            val session = lastPractice
            if (session != null) {
                item(key = "resume-practice") {
                    LintiCard(
                        containerColor = palette.primaryPale,
                        borderColor = palette.primarySoft,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "继续上次练习",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.textPrimary,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = if (session.totalCount > 0) {
                                        "${session.bankName} · ${session.mode.label} · 第 ${minOf(session.currentIndex + 1, session.totalCount)}/${session.totalCount} 题"
                                    } else {
                                        "${session.bankName} · ${session.mode.label}"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            PrimaryActionButton(
                                text = "继续",
                                onClick = {
                                    scope.launch {
                                        val result = viewModel.resumeLastPractice()
                                        if (result.started) onQuiz()
                                        else viewModel.showMessage(result.message ?: "无法继续练习")
                                    }
                                },
                            )
                            IconButton(onClick = viewModel::dismissLastPractice) {
                                Icon(
                                    Icons.Outlined.Close,
                                    contentDescription = "移除继续练习入口",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
            item {
                RecognitionSegment(
                    method = method,
                    onMethodChange = { method = it },
                )
            }
            item {
                LintiCard {
                    Column {
                        SectionHeader(
                            title = "导入文件",
                            trailing = "6 种格式",
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FormatEntry(
                                icon = Icons.Outlined.PictureAsPdf,
                                label = "PDF",
                                iconColor = palette.textSecondary,
                                iconBackground = palette.background,
                                modifier = Modifier.weight(1f),
                                onClick = { openDocument(DocumentKind.PDF) },
                            )
                            FormatEntry(
                                icon = Icons.Outlined.Description,
                                label = "Word",
                                iconColor = palette.textSecondary,
                                iconBackground = palette.background,
                                modifier = Modifier.weight(1f),
                                onClick = { openDocument(DocumentKind.DOCX) },
                            )
                            FormatEntry(
                                icon = Icons.Outlined.TableChart,
                                label = "Excel",
                                iconColor = palette.textSecondary,
                                iconBackground = palette.background,
                                modifier = Modifier.weight(1f),
                                onClick = { openDocument(DocumentKind.XLSX) },
                            )
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            FormatEntry(
                                icon = Icons.Outlined.TextSnippet,
                                label = "TXT",
                                iconColor = palette.textSecondary,
                                iconBackground = palette.background,
                                modifier = Modifier.weight(1f),
                                onClick = { openDocument(DocumentKind.TXT) },
                            )
                            FormatEntry(
                                icon = Icons.Outlined.Image,
                                label = "图片",
                                iconColor = palette.textSecondary,
                                iconBackground = palette.background,
                                modifier = Modifier.weight(1f),
                                onClick = { imageLauncher.launch(arrayOf("image/*")) },
                            )
                            FormatEntry(
                                icon = Icons.Outlined.PhotoCamera,
                                label = "拍照",
                                iconColor = palette.textSecondary,
                                iconBackground = palette.background,
                                modifier = Modifier.weight(1f),
                                onClick = ::openCamera,
                            )
                        }
                        Spacer(Modifier.height(13.dp))
                        ThinDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            OutlinedActionButton(
                                text = "手动录入",
                                icon = Icons.Outlined.Edit,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        viewModel.startManualEntry()
                                        onReview()
                                    }
                                },
                            )
                            OutlinedActionButton(
                                text = "分文件导入",
                                icon = Icons.Outlined.CallSplit,
                                modifier = Modifier.weight(1f),
                                onClick = onSplitImport,
                            )
                        }
                        if (method == ImportMethod.AI_ONLY && !settings.hasApiKey) {
                            Spacer(Modifier.height(9.dp))
                            Text(
                                text = "未配置 AI 服务",
                                style = MaterialTheme.typography.labelSmall,
                                color = palette.warning,
                            )
                        }
                    }
                }
            }
            val recentBanks = banks.filter { it.lastOpenedAt != null }
                .sortedByDescending { it.lastOpenedAt }
                .take(3)
            if (recentBanks.isNotEmpty()) {
                item {
                    SectionHeader(title = "最近练习", trailing = "更多", onTrailingClick = onOpenBanks)
                }
                item {
                    LintiCard(shapeRadius = 20.dp) {
                        Column {
                            recentBanks.forEachIndexed { index, bank ->
                                if (index > 0) ThinDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenBank(bank.id) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = bank.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = "已练 ${bank.sequenceAnsweredCount}/${bank.questionCount} · 待复习错题 ${bank.pendingWrongCount}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = palette.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Icon(
                                        Icons.Outlined.ChevronRight,
                                        contentDescription = null,
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionHeader(title = "最近导入")
            }
            if (recentImports.isEmpty()) {
                item {
                    LintiCard {
                        EmptyState(
                            title = "还没有导入记录",
                            action = {
                                OutlinedActionButton(
                                    text = "选择文件",
                                    onClick = {
                                        anyDocumentLauncher.launch(
                                            arrayOf(
                                                "application/pdf",
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                "text/plain",
                                                "text/csv",
                                                "image/*",
                                            ),
                                        )
                                    },
                                )
                            },
                        )
                    }
                }
            } else {
                items(recentImports.take(3), key = { "job-${it.id}" }) { job ->
                    RecentImportCard(
                        job = job,
                        onClick = {
                            if (job.targetBankId != null) {
                                onOpenBank(job.targetBankId)
                            } else {
                                viewModel.showMessage("该导入尚未关联题库")
                            }
                        },
                    )
                }
            }
        }
    }

    if (showExamPicker && banks.isNotEmpty()) {
        LintiChoiceSheet(
            title = "发起考试",
            values = banks,
            selected = banks.first(),
            label = { it.name },
            onSelect = {
                showExamPicker = false
                onExamSetup(it.id)
            },
            onDismiss = { showExamPicker = false },
        )
    }
}

@Composable
private fun RecognitionSegment(
    method: ImportMethod,
    onMethodChange: (ImportMethod) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(LocalLintiPalette.current.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RecognitionOption(
            text = "本地直接识别",
            selected = method == ImportMethod.LOCAL_ONLY,
            modifier = Modifier.weight(1f),
            onClick = { onMethodChange(ImportMethod.LOCAL_ONLY) },
        )
        RecognitionOption(
            text = "AI 识别",
            selected = method == ImportMethod.AI_ONLY,
            modifier = Modifier.weight(1f),
            onClick = { onMethodChange(ImportMethod.AI_ONLY) },
        )
    }
}

@Composable
private fun RecognitionOption(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) LocalLintiPalette.current.primarySoft else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) LocalLintiPalette.current.primary else LocalLintiPalette.current.textSecondary,
        )
    }
}

@Composable
private fun FormatEntry(
    icon: ImageVector,
    label: String,
    iconColor: Color,
    iconBackground: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    FeatureTile(
        icon = icon,
        label = label,
        onClick = onClick,
        modifier = modifier,
        iconColor = iconColor,
        iconBackground = iconBackground,
    )
}

@Composable
private fun RecentImportCard(
    job: ImportJob,
    onClick: () -> Unit,
) {
    val kindColor = when (job.kind) {
        "pdf" -> LocalLintiPalette.current.error to LocalLintiPalette.current.errorSoft
        "docx" -> LocalLintiPalette.current.primary to LocalLintiPalette.current.primaryPale
        "xlsx", "csv" -> LocalLintiPalette.current.success to LocalLintiPalette.current.successSoft
        "image", "camera" -> LocalLintiPalette.current.aiAccent to LocalLintiPalette.current.aiSoft
        else -> LocalLintiPalette.current.textSecondary to LocalLintiPalette.current.background
    }
    val statusColor = when (job.status) {
        ImportStatus.REVIEW -> LocalLintiPalette.current.warning
        ImportStatus.CONFIRMED -> LocalLintiPalette.current.success
        ImportStatus.FAILED -> LocalLintiPalette.current.error
    }
    val statusBackground = when (job.status) {
        ImportStatus.REVIEW -> LocalLintiPalette.current.warningSoft
        ImportStatus.CONFIRMED -> LocalLintiPalette.current.successSoft
        ImportStatus.FAILED -> LocalLintiPalette.current.errorSoft
    }
    LintiCard(shapeRadius = 16.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(kindColor.second),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when (job.kind) {
                        "pdf" -> Icons.Outlined.PictureAsPdf
                        "docx" -> Icons.Outlined.Description
                        "xlsx", "csv" -> Icons.Outlined.TableChart
                        else -> Icons.Outlined.UploadFile
                    },
                    contentDescription = null,
                    tint = kindColor.first,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    color = LocalLintiPalette.current.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusPill(
                        text = job.status.label,
                        color = statusColor,
                        background = statusBackground,
                    )
                    Text(
                        text = "${job.method.label} · ${formatDateTime(job.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalLintiPalette.current.textSecondary,
                    )
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "识别 ${job.recognizedCount} · 待确认 ${job.pendingCount} · 失败 ${job.failedCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = LocalLintiPalette.current.textSecondary,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}

private fun resolveDocumentKind(
    context: android.content.Context,
    uri: Uri,
    requested: DocumentKind,
): DocumentKind {
    if (requested == DocumentKind.CAMERA) return requested
    val displayName = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull()
    return displayName?.let(DocumentKind::fromFileName) ?: requested
}
