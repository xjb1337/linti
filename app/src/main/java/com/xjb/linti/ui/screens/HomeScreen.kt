package com.xjb.linti.ui.screens

import com.xjb.linti.ui.theme.LocalLintiPalette
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
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
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.FeatureTile
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
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
    onOpenBank: (Long) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val recentImports by viewModel.recentImports.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var method by remember { mutableStateOf(ImportMethod.LOCAL_ONLY) }
    var pendingKind by remember { mutableStateOf(DocumentKind.TXT) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun importUri(uri: Uri, kind: DocumentKind) {
        scope.launch {
            if (viewModel.importFromUri(uri, kind, method)) {
                onReview()
            }
        }
    }

    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            importUri(uri, resolveDocumentKind(context, uri, pendingKind))
        }
    }
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) importUri(uri, DocumentKind.IMAGE)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = cameraUri
        if (success && uri != null) importUri(uri, DocumentKind.CAMERA)
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
        cameraLauncher.launch(uri)
    }

    LazyColumn(
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
                subtitle = "自用刷题 · 导入即练",
                actions = {
                    StatusPill("本机存储", color = LocalLintiPalette.current.success, background = LocalLintiPalette.current.successSoft)
                },
            )
        }
        item {
            Column {
                Text(
                    text = "下午好，开始导入一份题集吧",
                    style = MaterialTheme.typography.headlineMedium,
                    color = LocalLintiPalette.current.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "所有功能免费，题库与学习记录只保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
        }
        item {
            RecognitionSegment(
                method = method,
                onMethodChange = { method = it },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (method == ImportMethod.LOCAL_ONLY) {
                        Icons.Outlined.PhoneAndroid
                    } else {
                        Icons.Outlined.AutoAwesome
                    },
                    contentDescription = null,
                    tint = if (method == ImportMethod.LOCAL_ONLY) LocalLintiPalette.current.primary else LocalLintiPalette.current.aiAccent,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (method == ImportMethod.LOCAL_ONLY) {
                        "本地优先，不耗额度；结果仍可逐题校对"
                    } else {
                        "AI 只在你主动选择时调用，结果必须人工确认"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
        }
        item {
            LintiCard {
                Column {
                    SectionHeader(
                        title = "导入文件",
                        trailing = "6 种格式 · 免费",
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
                            iconColor = Color(0xFFC35B3E),
                            iconBackground = Color(0xFFFEF0EB),
                            modifier = Modifier.weight(1f),
                            onClick = { openDocument(DocumentKind.PDF) },
                        )
                        FormatEntry(
                            icon = Icons.Outlined.Description,
                            label = "Word",
                            iconColor = Color(0xFF426DA4),
                            iconBackground = Color(0xFFEDF4FD),
                            modifier = Modifier.weight(1f),
                            onClick = { openDocument(DocumentKind.DOCX) },
                        )
                        FormatEntry(
                            icon = Icons.Outlined.TableChart,
                            label = "Excel",
                            iconColor = Color(0xFF378656),
                            iconBackground = Color(0xFFEBF6EE),
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
                            iconColor = LocalLintiPalette.current.textSecondary,
                            iconBackground = Color(0xFFF0F2F6),
                            modifier = Modifier.weight(1f),
                            onClick = { openDocument(DocumentKind.TXT) },
                        )
                        FormatEntry(
                            icon = Icons.Outlined.Image,
                            label = "图片",
                            iconColor = Color(0xFF7B69A9),
                            iconBackground = LocalLintiPalette.current.aiSoft,
                            modifier = Modifier.weight(1f),
                            onClick = { imageLauncher.launch(arrayOf("image/*")) },
                        )
                        FormatEntry(
                            icon = Icons.Outlined.PhotoCamera,
                            label = "拍照",
                            iconColor = LocalLintiPalette.current.aiAccent,
                            iconBackground = LocalLintiPalette.current.aiSoft,
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
                            text = "试题/答案分文件",
                            icon = Icons.Outlined.CallSplit,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                scope.launch {
                                    viewModel.startManualEntry()
                                    viewModel.showMessage("分文件配对本批尚未完善，已打开统一校对入口")
                                    onReview()
                                }
                            },
                        )
                    }
                    if (method == ImportMethod.AI_ONLY &&
                        (!settings.hasApiKey || settings.baseUrl.isBlank())
                    ) {
                        Spacer(Modifier.height(9.dp))
                        Text(
                            text = "尚未配置 AI 服务。仍可打开校对页，但识别结果可能为空。",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalLintiPalette.current.warning,
                        )
                    }
                }
            }
        }
        item {
            SectionHeader(title = "最近导入", trailing = "本机记录")
        }
        if (recentImports.isEmpty()) {
            item {
                LintiCard(containerColor = LocalLintiPalette.current.primaryPale) {
                    EmptyState(
                        title = "还没有导入记录",
                        message = "选择 TXT、Word、Excel 或 PDF 后，会在这里保留识别统计。",
                    )
                }
            }
        } else {
            items(recentImports.take(5), key = { "job-${it.id}" }) { job ->
                RecentImportCard(
                    job = job,
                    onClick = {
                        if (job.targetBankId != null) {
                            onOpenBank(job.targetBankId)
                        } else {
                            viewModel.showMessage("该导入还没有关联题库，请重新选择文件校对入库")
                        }
                    },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = LocalLintiPalette.current.textSecondary,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "无账号 · 无会员 · 无广告 · 无付费解锁",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalLintiPalette.current.textSecondary,
                )
            }
        }
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
            .background(Color(0xFFE8EEEB))
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
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) LocalLintiPalette.current.surface else Color.Transparent)
            .clickable(onClick = onClick)
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
        "pdf" -> Color(0xFFC35B3E) to Color(0xFFFEF0EB)
        "docx" -> Color(0xFF426DA4) to Color(0xFFEDF4FD)
        "xlsx", "csv" -> Color(0xFF378656) to Color(0xFFEBF6EE)
        "image", "camera" -> LocalLintiPalette.current.aiAccent to LocalLintiPalette.current.aiSoft
        else -> LocalLintiPalette.current.textSecondary to Color(0xFFF0F2F6)
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
