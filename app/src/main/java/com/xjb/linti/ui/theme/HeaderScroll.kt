package com.xjb.linti.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/** Scroll position used only for the appearance of a fixed page header. */
@Stable
class LintiHeaderScrollState(private val collapseDistance: Float) {
    private var distance by mutableFloatStateOf(0f)
    val collapsed: Boolean get() = distance >= collapseDistance

    fun onScroll(consumedY: Float) {
        distance = (distance - consumedY).coerceIn(0f, collapseDistance)
    }

    fun reset() { distance = 0f }
}

val LocalLintiHeaderScroll = staticCompositionLocalOf<LintiHeaderScrollState?> { null }
