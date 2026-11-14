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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xjb.linti.ui.theme.LintiMotion
import com.xjb.linti.ui.theme.LocalLintiPalette

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
        content()
        AnimatedVisibility(visible = collapsed, enter = fadeIn(tween(LintiMotion.TransitionMillis)), exit = fadeOut(tween(LintiMotion.TransitionMillis)), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(Modifier.fillMaxWidth().height(64.dp).background(LocalLintiPalette.current.surface)) {
                Text(title, Modifier.align(Alignment.Center).padding(horizontal = 76.dp), textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium,
                    color = LocalLintiPalette.current.textPrimary)
                if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp).size(48.dp).clip(CircleShape)) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = LocalLintiPalette.current.textPrimary)
                }
                Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, content = actions)
            }
        }
    }
}
