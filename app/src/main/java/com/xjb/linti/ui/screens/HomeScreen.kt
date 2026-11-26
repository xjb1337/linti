package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.domain.BankSummary
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.ui.ImportNavigationTarget
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.FeatureTile
import com.xjb.linti.ui.components.LintiCollapsingPage
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiChoiceSheet
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.SectionHeader
import com.xjb.linti.ui.components.SimpleProgressBar
import com.xjb.linti.ui.components.StatusPill
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.components.WarningNote
import com.xjb.linti.ui.formatDateTime
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
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
    onOpenAiSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentImports by viewModel.recentImports.collectAsStateWithLifecycle()
    val pendingReviews by viewModel.pendingImportReviews.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val lastPractice by viewModel.lastPracticeSession.collectAsStateWithLifecycle()
    val banks by viewModel.banks.collectAsStateWithLifecycle()
    val pendingWrongTotal = banks.sumOf { it.pendingWrongCount }
    val favoriteTotal = banks.sumOf { it.favoriteCount }
    var showExamPicker by rememberSaveable { mutableStateOf(false) }
    var showAllPending by rememberSaveable { mutableStateOf(false) }
    var reviewToDeleteId by rememberSaveable { mutableStateOf<Long?>(null) }
    var method by rememberSaveable { mutableStateOf(ImportMethod.LOCAL_ONLY) }
    var pendingKind by rememberSaveable { mutableStateOf(DocumentKind.TXT) }
    var cameraPath by rememberSaveable { mutableStateOf<String?>(null) }
    val palette = LocalLintiPalette.current
    val importTilesPerRow = if (LocalConfiguration.current.screenWidthDp < 360 || LocalDensity.current.fontScale >= 1.2f) 2 else 4

    fun canStartImport(): Boolean {
        val progress = viewModel.importProgress.value
        if (progress.awaitingPdfDecision) viewModel.showMessage("请先处理或放弃首页待确认的 PDF")
        return !progress.isImporting && !progress.awaitingPdfDecision
    }

    LaunchedEffect(viewModel) {
        viewModel.importNavigation.collect { target ->
            when (target) {
                ImportNavigationTarget.REVIEW -> onReview()
                ImportNavigationTarget.PDF_OCR -> onPdfOcr()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (viewModel.importProgress.value.isImporting && !viewModel.importProgress.value.isBackgrounded) {
                viewModel.backgroundImport()
            }
        }
    }

    // 入口分流：图片/拍照 → DeepSeek 在线 OCR 预览页；含扫描页的 PDF → 确认页；
    // 其余沿用既有本地/AI 导入（运行在 viewModelScope，可切至后台）。选档/拍照本身不触发任何网络请求。
    fun handleUri(uri: Uri, kind: DocumentKind) {
        if (!canStartImport()) return
        when (kind) {
            DocumentKind.IMAGE, DocumentKind.CAMERA -> {
                scope.launch {
                    if (viewModel.prepareImageOcr(uri, kind, method)) onImageOcr()
                }
            }
            else -> viewModel.startImport(uri, kind, method)
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
        val photo = cameraPath?.let(::File)
        cameraPath = null
        if (photo != null) {
            scope.launch {
                try {
                    if (success && canStartImport()) {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo)
                        if (viewModel.prepareImageOcr(uri, DocumentKind.CAMERA, method)) onImageOcr()
                    }
                } finally {
                    photo.delete()
                }
            }
        }
    }

    fun openDocument(kind: DocumentKind) {
        if (!canStartImport()) return
        if (method == ImportMethod.AI_ONLY && !settings.hasApiKey &&
            kind !in setOf(DocumentKind.PDF, DocumentKind.IMAGE, DocumentKind.CAMERA)
        ) {
            viewModel.showMessage("请先在「AI 服务」中填写 API Key")
            onOpenAiSettings()
            return
        }
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
        if (!canStartImport() || cameraPath != null) return
        try {
            val directory = File(context.cacheDir, "camera").apply { mkdirs() }
            val photo = File.createTempFile("linti_camera_", ".jpg", directory)
            cameraPath = photo.absolutePath
            cameraLauncher.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photo))
        } catch (_: android.content.ActivityNotFoundException) {
            cameraPath?.let { File(it).delete() }
            cameraPath = null
            viewModel.showMessage("没有可用的相机应用，可从文件中选择图片")
        } catch (_: java.io.IOException) {
            viewModel.showMessage("无法创建拍照文件，请检查剩余空间")
        }
    }

    val pageListState = rememberLazyListState()
    val allUploadMimeTypes = remember {
        arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain",
            "text/csv",
            "image/*",
        )
    }

    fun openAnyDocument() {
        if (!canStartImport()) return
        anyDocumentLauncher.launch(allUploadMimeTypes)
    }
    val featuredBank = remember(banks) {
        banks.maxWithOrNull(
            compareBy<BankSummary> { it.lastOpenedAt ?: Long.MIN_VALUE }
                .thenBy { it.updatedAt },
        )
    }
    LintiCollapsingPage("临题", pageListState) {
        LazyColumn(
            state = pageListState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        .heightIn(min = 50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(palette.surface)
                        .border(1.dp, palette.line.copy(alpha = 0.7f), RoundedCornerShape(15.dp))
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            .clickable(role = Role.Button, onClick = onSearch)
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "搜索题干、答案、解析或知识点",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                        )
                    }
                    IconButton(onClick = ::openCamera, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = "快速拍照导题",
                            tint = palette.primary,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }
            if (importProgress.awaitingPdfDecision) {
                item(key = "pending-pdf") {
                    LintiCard(containerColor = palette.primaryPale) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("PDF 待确认", style = MaterialTheme.typography.titleMedium)
                            Text(importProgress.fileName, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PrimaryActionButton(
                                    text = "继续处理",
                                    modifier = Modifier.weight(1f),
                                    onClick = viewModel::openPendingPdf,
                                )
                                OutlinedActionButton(
                                    text = "放弃",
                                    onClick = viewModel::dismissPendingPdf,
                                )
                            }
                        }
                    }
                }
            }
            val session = lastPractice
            if (session != null) {
                item(key = "resume-practice") {
                    val totalCount = if (session.mode == PracticeMode.SEQUENCE) {
                        banks.firstOrNull { it.id == session.bankId }?.questionCount ?: 0
                    } else {
                        session.totalCount
                    }
                    val currentNumber = (session.currentIndex + 1).coerceIn(0, totalCount)
                    LintiCard(
                        containerColor = palette.primaryPale,
                        borderColor = palette.primary.copy(alpha = 0.25f),
                        shapeRadius = 20.dp,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill("继续上次练习", color = palette.primary, background = palette.primarySoft)
                                Spacer(Modifier.weight(1f))
                                IconButton(
                                    onClick = viewModel::dismissLastPractice,
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Close,
                                        contentDescription = "移除继续练习入口",
                                        tint = palette.textSecondary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = session.bankName,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = if (totalCount > 0) {
                                            "${session.mode.label} · 第 $currentNumber/$totalCount 题"
                                        } else {
                                            session.mode.label
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                PrimaryActionButton(
                                    text = "继续",
                                    icon = Icons.Outlined.PlayArrow,
                                    onClick = {
                                        scope.launch {
                                            val result = viewModel.resumeLastPractice()
                                            if (result.started) onQuiz()
                                            else viewModel.showMessage(result.message ?: "无法继续练习")
                                        }
                                    },
                                )
                            }
                            if (totalCount > 0) {
                                SimpleProgressBar(progress = currentNumber.toFloat() / totalCount)
                            }
                        }
                    }
                }
            } else if (featuredBank != null) {
                item(key = "featured-bank") {
                    val progress = if (featuredBank.questionCount > 0) {
                        featuredBank.sequenceAnsweredCount.toFloat() / featuredBank.questionCount
                    } else {
                        0f
                    }
                    LintiCard(
                        containerColor = palette.primaryPale,
                        borderColor = palette.primary.copy(alpha = 0.25f),
                        shapeRadius = 20.dp,
                        onClick = { onOpenBank(featuredBank.id) },
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusPill("今日主推", color = palette.primary, background = palette.primarySoft)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${featuredBank.sequenceAnsweredCount}/${featuredBank.questionCount} 题",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                                    color = palette.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = featuredBank.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "顺序练习 · 错题 ${featuredBank.pendingWrongCount} · 收藏 ${featuredBank.favoriteCount}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                PrimaryActionButton(
                                    text = if (featuredBank.sequenceAnsweredCount > 0) "继续" else "开始练习",
                                    icon = Icons.Outlined.PlayArrow,
                                    onClick = {
                                        scope.launch {
                                            val result = viewModel.startSequenceQuiz(featuredBank.id)
                                            if (result.started) onQuiz()
                                            else viewModel.showMessage(result.message ?: "无法开始练习")
                                        }
                                    },
                                )
                            }
                            SimpleProgressBar(progress = progress)
                        }
                    }
                }
            } else {
                item(key = "home-onboarding") {
                    LintiCard(
                        containerColor = palette.primaryPale,
                        borderColor = palette.primary.copy(alpha = 0.25f),
                        shapeRadius = 20.dp,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatusPill("开始第一份题库", color = palette.primary, background = palette.primarySoft)
                            Text(
                                text = "导入文档、拍照识题或让 AI 自动生成题库",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.textPrimary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrimaryActionButton(
                                    text = "导入文件",
                                    icon = Icons.Outlined.UploadFile,
                                    modifier = Modifier.weight(1f),
                                    onClick = ::openAnyDocument,
                                )
                                OutlinedActionButton(
                                    text = "体验示例题库",
                                    modifier = Modifier.weight(1f),
                                    onClick = viewModel::loadSampleData,
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LintiCard(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        containerColor = if (pendingWrongTotal > 0) palette.errorSoft else palette.surface,
                        borderColor = if (pendingWrongTotal > 0) palette.error.copy(alpha = 0.28f) else palette.line.copy(alpha = 0.65f),
                        shapeRadius = 18.dp,
                        onClick = onWrongBook,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (pendingWrongTotal > 0) palette.surface.copy(alpha = 0.7f) else palette.errorSoft),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = palette.error, modifier = Modifier.size(19.dp))
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Column {
                                Text(
                                    text = pendingWrongTotal.toString(),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                                    color = if (pendingWrongTotal > 0) palette.error else palette.textPrimary,
                                )
                                Text(
                                    text = "错题本",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = palette.textPrimary,
                                )
                                Text(
                                    text = if (pendingWrongTotal > 0) "待复习 $pendingWrongTotal 题" else "暂无待复习错题",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LintiCard(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            containerColor = palette.aiSoft,
                            borderColor = palette.aiAccent.copy(alpha = 0.25f),
                            shapeRadius = 18.dp,
                            onClick = onAiQuestions,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(palette.surface.copy(alpha = 0.7f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = palette.aiAccent, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("AI 出题", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                                    Text("主题 / 材料", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                }
                            }
                        }
                        LintiCard(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            shapeRadius = 18.dp,
                            onClick = {
                                when {
                                    banks.isEmpty() -> viewModel.showMessage("先导入题库，再发起考试")
                                    banks.size == 1 -> onExamSetup(banks.first().id)
                                    else -> showExamPicker = true
                                }
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(palette.primaryPale),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = palette.primary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("发起考试", style = MaterialTheme.typography.titleMedium, color = palette.textPrimary)
                                    Text("限时全真模拟", style = MaterialTheme.typography.labelSmall, color = palette.textSecondary)
                                }
                            }
                        }
                    }
                }
            }
            item {
                LintiCard(shapeRadius = 18.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            }
            item {
                LintiCard(shapeRadius = 20.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(title = "导入与录入")
                        RecognitionSegment(
                            method = method,
                            onMethodChange = { method = it },
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            maxItemsInEachRow = importTilesPerRow,
                        ) {
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
                                onClick = ::openAnyDocument,
                                modifier = Modifier.weight(1f),
                                iconColor = palette.primary,
                                iconBackground = palette.primaryPale,
                            )
                            FeatureTile(
                                icon = Icons.Outlined.CallSplit,
                                label = "分文件导入",
                                onClick = { if (canStartImport()) onSplitImport() },
                                modifier = Modifier.weight(1f),
                                iconColor = palette.aiAccent,
                                iconBackground = palette.aiSoft,
                            )
                            FeatureTile(
                                icon = Icons.Outlined.Edit,
                                label = "手动录入",
                                onClick = {
                                    if (canStartImport()) {
                                        scope.launch {
                                            viewModel.startManualEntry()
                                            onReview()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                iconColor = palette.success,
                                iconBackground = palette.successSoft,
                            )
                        }
                        ThinDivider()
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            FormatQuickPill(Icons.Outlined.PictureAsPdf, "PDF") { openDocument(DocumentKind.PDF) }
                            FormatQuickPill(Icons.Outlined.Description, "Word") { openDocument(DocumentKind.DOCX) }
                            FormatQuickPill(Icons.Outlined.TableChart, "Excel") { openDocument(DocumentKind.XLSX) }
                            FormatQuickPill(Icons.Outlined.TextSnippet, "TXT") { openDocument(DocumentKind.TXT) }
                            FormatQuickPill(Icons.Outlined.TableChart, "CSV") { openDocument(DocumentKind.CSV) }
                            FormatQuickPill(Icons.Outlined.Image, "图片") {
                                if (canStartImport()) imageLauncher.launch(arrayOf("image/*"))
                            }
                        }
                        if (method == ImportMethod.AI_ONLY && !settings.hasApiKey) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.warningSoft)
                                    .clickable(onClick = onOpenAiSettings)
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "未配置 AI 服务，需先填写 API Key",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = palette.warning,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = "去设置",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        if (importProgress.isImporting) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.primaryPale)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = palette.primary,
                                )
                                Column(Modifier.weight(1f)) {
                                    if (importProgress.fileName.isNotBlank()) {
                                        Text(
                                            text = if (importProgress.isBackgrounded) {
                                                "后台导入中 · ${importProgress.fileName}"
                                            } else {
                                                importProgress.fileName
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = palette.textPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = importProgress.progressText.ifBlank { "正在导入题目…" },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = palette.primary,
                                    )
                                }
                                TextButton(onClick = viewModel::cancelActiveImport) {
                                    Text("取消", color = palette.textSecondary)
                                }
                            }
                        } else if (importProgress.error != null) {
                            val errorMsg = importProgress.error.orEmpty()
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                WarningNote(message = errorMsg)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    if ("AI" in errorMsg || "Key" in errorMsg || "认证" in errorMsg) {
                                        TextButton(onClick = onOpenAiSettings) {
                                            Text("前往 AI 设置")
                                        }
                                    }
                                    TextButton(onClick = viewModel::clearImportProgressError) {
                                        Text("知道了", color = palette.textSecondary)
                                    }
                                }
                            }
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
            if (pendingReviews.isNotEmpty()) {
                item(key = "pending-imports") {
                    SectionHeader(title = "待校对导入 · ${pendingReviews.size}")
                }
                items(if (showAllPending) pendingReviews else pendingReviews.take(3), key = { "pending-job-${it.id}" }) { job ->
                    RecentImportCard(
                        job = job,
                        onDelete = { reviewToDeleteId = job.id },
                        onClick = {
                            scope.launch {
                                if (viewModel.openImportJobForReview(job.id)) onReview()
                            }
                        },
                    )
                }
                if (pendingReviews.size > 3) {
                    item(key = "toggle-pending-imports") {
                        OutlinedActionButton(
                            text = if (showAllPending) "收起待校对记录" else "查看全部 ${pendingReviews.size} 条待校对记录",
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { showAllPending = !showAllPending },
                        )
                    }
                }
            }
            val completedImports = recentImports.filter { it.status != ImportStatus.REVIEW }
            item(key = "recent-imports") {
                SectionHeader(title = "最近导入")
            }
            if (completedImports.isEmpty()) {
                item {
                    LintiCard {
                        EmptyState(
                            title = if (pendingReviews.isEmpty()) "还没有导入记录" else "暂无已处理的导入记录",
                            action = {
                                OutlinedActionButton(
                                    text = "选择文件",
                                    onClick = ::openAnyDocument,
                                )
                            },
                        )
                    }
                }
            } else {
                items(completedImports.take(3), key = { "job-${it.id}" }) { job ->
                    RecentImportCard(
                        job = job,
                        onClick = {
                            when {
                                job.targetBankId != null -> onOpenBank(job.targetBankId)
                                job.status == ImportStatus.CONFIRMED -> {
                                    viewModel.showMessage("关联的题库已被删除")
                                }
                                else -> {
                                    viewModel.showMessage(job.warnings.firstOrNull() ?: "该导入尚未关联题库")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    val reviewToDelete = pendingReviews.firstOrNull { it.id == reviewToDeleteId }
    if (reviewToDelete != null) {
        AlertDialog(
            onDismissRequest = { reviewToDeleteId = null },
            title = { Text("删除待校对草稿？") },
            text = { Text("将删除「${reviewToDelete.fileName}」和这次导入的校对修改。") },
            dismissButton = { TextButton(onClick = { reviewToDeleteId = null }) { Text("取消") } },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteImportJob(reviewToDelete.id)
                    reviewToDeleteId = null
                }) { Text("删除草稿") }
            },
        )
    }

    if (importProgress.isImporting && !importProgress.isBackgrounded) {
        AlertDialog(
            onDismissRequest = { viewModel.backgroundImport() },
            title = { Text("正在导入题目") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (importProgress.fileName.isNotBlank()) {
                            Text(
                                text = importProgress.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = importProgress.progressText.ifBlank { "正在处理文件，请稍候…" },
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textSecondary,
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelActiveImport() }) {
                    Text("取消导入", color = palette.textSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.backgroundImport() }) {
                    Text("后台导入")
                }
            },
        )
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
    val palette = LocalLintiPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceContainer)
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
    val palette = LocalLintiPalette.current
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) palette.surface else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) palette.line else Color.Transparent,
                shape = RoundedCornerShape(9.dp),
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) palette.primary else palette.textSecondary,
        )
    }
}

@Composable
private fun FormatQuickPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val palette = LocalLintiPalette.current
    Row(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(palette.surfaceContainer)
            .border(1.dp, palette.line.copy(alpha = 0.65f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.textPrimary, maxLines = 1)
    }
}

@Composable
private fun RecentImportCard(
    job: ImportJob,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
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
    LintiCard(shapeRadius = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                    if (job.status == ImportStatus.FAILED && job.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = job.warnings.first(),
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onDelete == null) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = LocalLintiPalette.current.textSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除 ${job.fileName} 的草稿")
                }
            }
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
