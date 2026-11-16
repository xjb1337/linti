package com.xjb.linti.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.ColorTheme

fun ColorTheme.palette(dark: Boolean): LintiPalette {
    val neutral = if (dark) DefaultDarkPalette else DefaultLightPalette
    val selected = when (this) {
        ColorTheme.DEFAULT, ColorTheme.DYNAMIC -> neutral
        ColorTheme.JADE -> if (dark) JadeDarkPalette else JadeLightPalette
        ColorTheme.INDIGO -> if (dark) IndigoDarkPalette else IndigoLightPalette
        ColorTheme.ROSE -> if (dark) RoseDarkPalette else RoseLightPalette
        ColorTheme.AMBER -> if (dark) AmberDarkPalette else AmberLightPalette
    }
    val tint = if (this == ColorTheme.DEFAULT || this == ColorTheme.DYNAMIC) 0f else 0.22f
    return selected.copy(
        background = lerp(neutral.background, selected.background, tint),
        backgroundTop = lerp(neutral.backgroundTop, selected.background, tint),
        backgroundBottom = lerp(neutral.backgroundBottom, selected.background, tint),
        surface = lerp(neutral.surface, selected.surface, tint * 0.6f),
        textPrimary = neutral.textPrimary,
        textSecondary = neutral.textSecondary,
        textTertiary = neutral.textTertiary,
        line = lerp(neutral.line, selected.line, tint),
        surfaceLowest = neutral.surfaceLowest,
        surfaceLow = neutral.surfaceLow,
        surfaceContainer = lerp(neutral.surfaceContainer, selected.surface, tint * 0.4f),
        surfaceHigh = neutral.surfaceHigh,
        surfaceHighest = neutral.surfaceHighest,
    )
}

private fun colorSchemeFor(palette: LintiPalette, night: Boolean): ColorScheme {
    val base = if (night) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primarySoft,
        onPrimaryContainer = palette.primary,
        inversePrimary = palette.primary,
        secondary = palette.textSecondary,
        onSecondary = palette.surface,
        secondaryContainer = palette.primarySoft,
        onSecondaryContainer = palette.primary,
        tertiary = palette.aiAccent,
        onTertiary = palette.surface,
        tertiaryContainer = palette.aiSoft,
        onTertiaryContainer = palette.aiAccent,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.background,
        surfaceBright = palette.surface,
        surfaceDim = palette.background,
        onSurfaceVariant = palette.textSecondary,
        surfaceContainerLowest = palette.surfaceLowest,
        surfaceContainerLow = palette.surfaceLow,
        surfaceContainer = palette.surfaceContainer,
        surfaceContainerHigh = palette.surfaceHigh,
        surfaceContainerHighest = palette.surfaceHighest,
        outline = palette.textSecondary,
        outlineVariant = palette.line,
        error = palette.error,
        onError = palette.onPrimary,
        errorContainer = palette.errorSoft,
        onErrorContainer = palette.error,
        inverseSurface = palette.textPrimary,
        inverseOnSurface = palette.surface,
        surfaceTint = Color.Transparent,
        scrim = Color(0xFF101722),
    )
}

private val LintiTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 27.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 27.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
)

private val LintiShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private fun Context.componentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current != null) {
        if (current is ComponentActivity) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

@Composable
fun LintiTheme(
    appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    colorTheme: ColorTheme = ColorTheme.DEFAULT,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val dark = when (appearanceMode) {
        AppearanceMode.SYSTEM -> isSystemInDarkTheme()
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
    }
    val palette = colorTheme.palette(dark)
    val scheme = colorSchemeFor(palette, dark)
    DisposableEffect(appearanceMode, colorTheme, dark) {
        val activity = context.componentActivity()
        if (activity != null) {
            val style = if (dark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                )
            }
            activity.enableEdgeToEdge(style, style)
        }
        onDispose { }
    }
    CompositionLocalProvider(LocalLintiPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = LintiTypography,
            shapes = LintiShapes,
            content = content,
        )
    }
}

/** Shared page backdrop: the content remains transparent so the gradient can show through. */
@Composable
fun LintiPageBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val palette = LocalLintiPalette.current
    val distance = with(LocalDensity.current) { 64.dp.toPx() }
    val headerScroll = remember(distance) { LintiHeaderScrollState(distance) }
    val connection = remember(headerScroll) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                headerScroll.onScroll(consumed.y)
                return Offset.Zero
            }
        }
    }
    CompositionLocalProvider(LocalContentColor provides palette.textPrimary, LocalLintiHeaderScroll provides headerScroll) {
        Box(modifier.fillMaxSize().nestedScroll(connection).background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom))), content = content)
    }
}
