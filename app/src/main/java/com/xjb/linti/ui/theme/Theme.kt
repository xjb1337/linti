package com.xjb.linti.ui.theme

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjb.linti.domain.ReadingMode

private fun colorSchemeFor(palette: LintiPalette, night: Boolean) = if (night) {
    darkColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primarySoft,
        onPrimaryContainer = palette.primary,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.background,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.line,
        error = palette.error,
        onError = palette.onPrimary,
        errorContainer = palette.errorSoft,
        onErrorContainer = palette.error,
        tertiary = palette.aiAccent,
        onTertiary = palette.onPrimary,
        tertiaryContainer = palette.aiSoft,
        onTertiaryContainer = palette.aiAccent,
    )
} else {
    lightColorScheme(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primarySoft,
        onPrimaryContainer = palette.primary,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.background,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.line,
        error = palette.error,
        onError = palette.onPrimary,
        errorContainer = palette.errorSoft,
        onErrorContainer = palette.error,
        tertiary = palette.aiAccent,
        onTertiary = palette.onPrimary,
        tertiaryContainer = palette.aiSoft,
        onTertiaryContainer = palette.aiAccent,
    )
}

private val LintiTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
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
        fontSize = 15.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 21.sp,
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
        fontSize = 10.sp,
        lineHeight = 15.sp,
    ),
)

private val LintiShapes = Shapes(
    extraSmall = RoundedCornerShape(7.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp),
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
    readingMode: ReadingMode = ReadingMode.DAY,
    content: @Composable () -> Unit,
) {
    val palette = when (readingMode) {
        ReadingMode.DAY -> DayPalette
        ReadingMode.NIGHT -> NightPalette
        ReadingMode.EYE_CARE -> EyeCarePalette
    }
    val night = readingMode == ReadingMode.NIGHT
    val context = LocalContext.current
    DisposableEffect(readingMode) {
        val activity = context.componentActivity()
        if (activity != null) {
            val style = if (night) {
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
            colorScheme = colorSchemeFor(palette, night),
            typography = LintiTypography,
            shapes = LintiShapes,
            content = content,
        )
    }
}
