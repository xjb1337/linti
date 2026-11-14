package com.xjb.linti.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.xjb.linti.data.images.QuestionImageStore
import com.xjb.linti.domain.QuestionImage
import com.xjb.linti.ui.theme.LocalLintiPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun QuestionImages(
    images: List<QuestionImage>,
    onChange: ((List<QuestionImage>) -> Unit)? = null,
) {
    if (images.isEmpty()) return
    var selected by remember { mutableIntStateOf(-1) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        images.forEachIndexed { index, image ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ImageFile(image, Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)).clickable { selected = index })
                if (onChange != null) {
                    Row {
                        IconButton(onClick = {
                            val reordered = images.toMutableList()
                            java.util.Collections.swap(reordered, index, index - 1)
                            onChange(reordered)
                        }, enabled = index > 0, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "图片左移")
                        }
                        IconButton(onClick = { onChange(images.filterIndexed { i, _ -> i != index }) }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除图片")
                        }
                        IconButton(onClick = {
                            val reordered = images.toMutableList()
                            java.util.Collections.swap(reordered, index, index + 1)
                            onChange(reordered)
                        }, enabled = index < images.lastIndex, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "图片右移")
                        }
                    }
                }
            }
        }
    }
    if (selected in images.indices) QuestionImageViewer(images, selected) { selected = -1 }
}

@Composable
internal fun ImageFile(image: QuestionImage, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    var loading by remember(image.fileName) { mutableStateOf(true) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, image.fileName) {
        value = withContext(Dispatchers.IO) {
            if (!QuestionImageStore.FILE_NAME.matches(image.fileName)) null
            else runCatching { BitmapFactory.decodeFile(File(context.filesDir, "question_images/${image.fileName}").absolutePath) }.getOrNull()
        }
        loading = false
    }
    val loaded = bitmap
    if (loaded == null) {
        Box(modifier.background(LocalLintiPalette.current.primaryPale), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator(Modifier.size(24.dp))
            else Text("图片已丢失", style = MaterialTheme.typography.labelMedium, color = LocalLintiPalette.current.textSecondary)
        }
    } else {
        Image(loaded.asImageBitmap(), contentDescription = "题干图片", modifier = modifier, contentScale = contentScale)
    }
}

@Composable
internal fun QuestionImageViewer(images: List<QuestionImage>, initialPage: Int, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { images.size })
    var zoomed by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), userScrollEnabled = !zoomed) { page ->
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var offsetX by remember(page) { mutableFloatStateOf(0f) }
                var offsetY by remember(page) { mutableFloatStateOf(0f) }
                Box(Modifier.fillMaxSize().testTag("image-viewer-page-$page")
                    .semantics { stateDescription = "缩放 ${(scale * 100).roundToInt()}%" }.pointerInput(page) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var transforming = scale > 1f
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) transforming = true
                            if (transforming) {
                                val pan = event.calculatePan()
                                scale = (scale * event.calculateZoom()).coerceIn(1f, 5f)
                                zoomed = scale > 1f
                                if (scale == 1f) { offsetX = 0f; offsetY = 0f }
                                else { offsetX += pan.x; offsetY += pan.y }
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }.pointerInput(page) {
                    detectTapGestures(onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        zoomed = scale > 1f
                        offsetX = 0f; offsetY = 0f
                    })
                }, contentAlignment = Alignment.Center) {
                    ImageFile(images[page], Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
                    }, ContentScale.Fit)
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭图片", tint = Color.White)
            }
            Text("${pager.currentPage + 1} / ${images.size}", color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp))
        }
    }
}
