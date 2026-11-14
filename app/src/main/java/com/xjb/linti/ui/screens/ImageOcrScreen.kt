package com.xjb.linti.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.data.ocr.CropRect
import com.xjb.linti.data.ocr.OcrKeySource
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.OcrPhase
import com.xjb.linti.ui.components.EmptyState
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.components.OutlinedActionButton
import com.xjb.linti.ui.components.PrimaryActionButton
import com.xjb.linti.ui.components.ThinDivider
import com.xjb.linti.ui.components.WarningNote
import com.xjb.linti.ui.theme.LocalLintiPalette
import java.io.File
import kotlinx.coroutines.launch

private enum class DragMode { NONE, MOVE, CORNER }

private enum class DragCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** 裁切区域（0..1 归一，位于旋转后预览图坐标），默认整图。 */
private data class CropFractions(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val isValid: Boolean
        get() = right - left >= MIN_CROP_FRACTION && bottom - top >= MIN_CROP_FRACTION
}

private const val MIN_CROP_FRACTION = 0.08f

/**
 * DeepSeek 在线图片识别页：EXIF 已由 VM 扶正的预览 + 拖动矩形裁切（默认整图）+ 旋转 + 重拍/更换。
 * 选档、拍照、裁切、旋转均不触发网络；只有「上传并识别」按钮会上传并识别。
 */
@Composable
fun ImageOcrScreen(
    viewModel: LintiViewModel,
    onBack: () -> Unit,
    onRecognized: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.imageOcrState.collectAsStateWithLifecycle()
    val preview by viewModel.imageOcrPreview.collectAsStateWithLifecycle()
    val keyState = viewModel.ocrKeyState()

    // 离开页面即作废进行中的请求并清理临时文件：迟到响应不会导航或覆盖状态
    DisposableEffect(Unit) {
        onDispose { viewModel.onImageOcrScreenLeft() }
    }

    var rotation by remember { mutableIntStateOf(0) }
    var crop by remember { mutableStateOf(CropFractions()) }
    var showBillingConfirm by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    // 拍照临时原件：复制进 OCR 预处理后立即删除（成功/失败/取消都清），选入的用户原文件不动
    var cameraFile by remember { mutableStateOf<File?>(null) }

    // 预览随用户旋转重画位图；旋转后裁切重置为整图
    val rotatedPreview = remember(preview, rotation) {
        val source = preview ?: return@remember null
        if (rotation == 0) {
            source
        } else {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }
    }

    fun resetCropToFull() {
        crop = CropFractions()
    }

    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                if (viewModel.prepareImageOcr(uri, DocumentKind.IMAGE)) {
                    rotation = 0
                    resetCropToFull()
                }
            }
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
                val prepared = viewModel.prepareImageOcr(uri, DocumentKind.CAMERA)
                if (prepared) {
                    rotation = 0
                    resetCropToFull()
                }
            }
            // 无论成功/失败/取消，拍照临时原件在复制（或放弃）后即清理
            photo?.delete()
        }
    }

    fun retakePhoto() {
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

    fun submit(billingConfirmed: Boolean = false) {
        val bitmap = rotatedPreview ?: return
        scope.launch {
            val recognized = viewModel.submitImageOcr(
                rotationDegrees = rotation,
                previewCropRect = CropRect(
                    left = crop.left * bitmap.width,
                    top = crop.top * bitmap.height,
                    right = crop.right * bitmap.width,
                    bottom = crop.bottom * bitmap.height,
                ),
                previewWidth = bitmap.width.toFloat(),
                previewHeight = bitmap.height.toFloat(),
                billingConfirmed = billingConfirmed,
            )
            if (recognized) onRecognized()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        LintiPageHeader(
            title = "图片在线识别",
            subtitle = state.fileName.ifBlank { null },
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            val bitmap = rotatedPreview
            if (bitmap == null) {
                LintiCard(modifier = Modifier.padding(20.dp)) {
                    EmptyState(title = "正在载入图片")
                }
            } else {
                CropPreview(
                    bitmap = bitmap,
                    crop = crop,
                    onCropChange = { crop = it },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                OutlinedActionButton(
                    text = "旋转",
                    icon = Icons.Outlined.RotateRight,
                    modifier = Modifier.weight(1f),
                    enabled = rotatedPreview != null && !state.isRunning,
                    onClick = { rotation = (rotation + 90) % 360 },
                )
                OutlinedActionButton(
                    text = "重置整图",
                    icon = Icons.Outlined.CropFree,
                    modifier = Modifier.weight(1f),
                    enabled = rotatedPreview != null && !state.isRunning,
                    onClick = ::resetCropToFull,
                )
                OutlinedActionButton(
                    text = "更换",
                    icon = Icons.Outlined.Image,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isRunning,
                    onClick = { imageLauncher.launch(arrayOf("image/*")) },
                )
                OutlinedActionButton(
                    text = "重拍",
                    icon = Icons.Outlined.AddAPhoto,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isRunning,
                    onClick = ::retakePhoto,
                )
            }
            LintiCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "文件将上传至 DeepSeek（官方在线识别，仅转写文字，不做生成式解析）",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLintiPalette.current.textSecondary,
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
                            state.phase == OcrPhase.PREPARING -> "正在准备…"
                            state.phase == OcrPhase.RUNNING -> "上传识别中…"
                            state.phase == OcrPhase.FAILED && state.pendingBillingConfirm ->
                                "重试（可能重复计费）"
                            else -> "上传并识别"
                        },
                        modifier = Modifier.weight(1.6f),
                        enabled = rotatedPreview != null &&
                            !state.isRunning &&
                            keyState != OcrKeySource.None,
                        onClick = {
                            if (state.pendingBillingConfirm) {
                                showBillingConfirm = true
                            } else {
                                submit()
                            }
                        },
                    )
                }
            }
        }
    }

    if (showBillingConfirm) {
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
                        submit(billingConfirmed = true)
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

private fun nearestCorner(
    offset: Offset,
    crop: CropFractions,
    width: Float,
    height: Float,
    thresholdPx: Float,
): DragCorner? {
    val candidates = listOf(
        DragCorner.TOP_LEFT to Offset(crop.left * width, crop.top * height),
        DragCorner.TOP_RIGHT to Offset(crop.right * width, crop.top * height),
        DragCorner.BOTTOM_LEFT to Offset(crop.left * width, crop.bottom * height),
        DragCorner.BOTTOM_RIGHT to Offset(crop.right * width, crop.bottom * height),
    )
    return candidates.firstOrNull { (_, point) ->
        val dx = offset.x - point.x
        val dy = offset.y - point.y
        dx * dx + dy * dy <= thresholdPx * thresholdPx
    }?.first
}

/**
 * 裁切预览：图片按宽度适配显示，叠加遮罩与可拖拽矩形（四角拖动、区域内平移）。
 * 裁切值归一（0..1），提交时换算到旋转后位图像素坐标。
 */
@Composable
private fun CropPreview(
    bitmap: Bitmap,
    crop: CropFractions,
    onCropChange: (CropFractions) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLintiPalette.current
    val density = LocalDensity.current
    val handleRadiusPx = with(density) { 48.dp.toPx() }

    // pointerInput 只捕获稳定引用；最新值经 rememberUpdatedState 读取
    val currentCrop by rememberUpdatedState(crop)
    val currentOnChange by rememberUpdatedState(onCropChange)
    val dragMode = remember { mutableStateOf(DragMode.NONE) }
    val dragCorner = remember { mutableStateOf<DragCorner?>(null) }
    val dragOrigin = remember { mutableStateOf(crop) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.08f)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
            val displayHeightDp = maxWidth / aspect
            Box(
                modifier = Modifier
                    .width(maxWidth)
                    .height(displayHeightDp)
                    .pointerInput(bitmap.width, bitmap.height, handleRadiusPx) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                val found = nearestCorner(
                                    offset, currentCrop, width, height, handleRadiusPx,
                                )
                                dragCorner.value = found
                                dragMode.value = when {
                                    found != null -> DragMode.CORNER
                                    else -> {
                                        val x = offset.x / width
                                        val y = offset.y / height
                                        if (x >= currentCrop.left && x <= currentCrop.right &&
                                            y >= currentCrop.top && y <= currentCrop.bottom
                                        ) {
                                            DragMode.MOVE
                                        } else {
                                            DragMode.NONE
                                        }
                                    }
                                }
                                dragOrigin.value = currentCrop
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val width = size.width.toFloat()
                                val height = size.height.toFloat()
                                when (dragMode.value) {
                                    DragMode.MOVE -> {
                                        val origin = dragOrigin.value
                                        val moveWidth = origin.right - origin.left
                                        val moveHeight = origin.bottom - origin.top
                                        val dx = dragAmount.x / width
                                        val dy = dragAmount.y / height
                                        val newLeft = (origin.left + dx).coerceIn(0f, 1f - moveWidth)
                                        val newTop = (origin.top + dy).coerceIn(0f, 1f - moveHeight)
                                        currentOnChange(
                                            CropFractions(
                                                newLeft, newTop, newLeft + moveWidth, newTop + moveHeight,
                                            ),
                                        )
                                    }
                                    DragMode.CORNER -> {
                                        val origin = dragOrigin.value
                                        val target = dragCorner.value
                                        if (target != null) {
                                            val x = (change.position.x / width).coerceIn(0f, 1f)
                                            val y = (change.position.y / height).coerceIn(0f, 1f)
                                            val updated = when (target) {
                                                DragCorner.TOP_LEFT -> CropFractions(
                                                    x, y, origin.right, origin.bottom,
                                                )
                                                DragCorner.TOP_RIGHT -> CropFractions(
                                                    origin.left, y, x, origin.bottom,
                                                )
                                                DragCorner.BOTTOM_LEFT -> CropFractions(
                                                    x, origin.top, origin.right, y,
                                                )
                                                DragCorner.BOTTOM_RIGHT -> CropFractions(
                                                    origin.left, origin.top, x, y,
                                                )
                                            }
                                            if (updated.isValid) currentOnChange(updated)
                                        }
                                    }
                                    DragMode.NONE -> Unit
                                }
                            },
                            onDragEnd = {
                                dragMode.value = DragMode.NONE
                                dragCorner.value = null
                            },
                        )
                    },
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "待识别图片预览",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val rect = Rect(
                        crop.left * size.width,
                        crop.top * size.height,
                        crop.right * size.width,
                        crop.bottom * size.height,
                    )
                    val dim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(0f, 0f, size.width, size.height))
                        addRect(rect)
                    }
                    drawPath(dim, color = Color.Black.copy(alpha = 0.45f))
                    drawRect(
                        color = palette.primary,
                        topLeft = rect.topLeft,
                        size = rect.size,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    listOf(
                        Offset(rect.left, rect.top),
                        Offset(rect.right, rect.top),
                        Offset(rect.left, rect.bottom),
                        Offset(rect.right, rect.bottom),
                    ).forEach { point ->
                        drawCircle(color = palette.primary, radius = 7.dp.toPx(), center = point)
                    }
                }
            }
        }
    }
}
