package com.xjb.linti.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xjb.linti.ui.theme.LintiMotion
import com.xjb.linti.ui.theme.LocalLintiPalette
import com.xjb.linti.ui.theme.LocalLintiHeaderScroll

/** The expanded header is part of the list; a compact title appears as it leaves the viewport. */
@Composable
fun LintiCollapsingPage(
    title: String,
    state: LazyListState,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val collapsed by remember(state) { derivedStateOf { state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 90 } }
    Box(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalLintiHeaderScroll provides null) { content() }
        AnimatedVisibility(visible = collapsed, enter = fadeIn(tween(LintiMotion.TransitionMillis)), exit = fadeOut(tween(LintiMotion.TransitionMillis)), modifier = Modifier.align(Alignment.TopCenter)) {
            LintiCompactHeader(title, onBack, modifier = Modifier.background(LocalLintiPalette.current.surface), actions = actions)
        }
    }
}

/** Centers the title while reserving equal space for the controls on either side. */
@Composable
internal fun LintiCompactHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    titleColor: Color = Color.Unspecified,
    actions: @Composable RowScope.() -> Unit = {},
) {
    var leftWidth by remember { mutableIntStateOf(0) }
    var rightWidth by remember { mutableIntStateOf(0) }
    val inset = with(LocalDensity.current) { maxOf(leftWidth, rightWidth).toDp() }
    Box(modifier.fillMaxWidth().height(56.dp)) {
        Text(title, Modifier.align(Alignment.Center).padding(horizontal = inset), textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
            color = if (titleColor == Color.Unspecified) LocalLintiPalette.current.textPrimary else titleColor)
        Row(Modifier.align(Alignment.CenterStart).onSizeChanged { leftWidth = it.width }, verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.size(48.dp).clip(CircleShape).background(LocalLintiPalette.current.background)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = LocalLintiPalette.current.textPrimary)
            }
        }
        Row(Modifier.align(Alignment.CenterEnd).onSizeChanged { rightWidth = it.width }, verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}
