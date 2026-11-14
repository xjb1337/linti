package com.xjb.linti.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.ColorTheme

fun ColorTheme.palette(dark: Boolean): LintiPalette = when (this) {
    ColorTheme.DEFAULT, ColorTheme.DYNAMIC -> if (dark) DefaultDarkPalette else DefaultLightPalette
    ColorTheme.JADE -> if (dark) JadeDarkPalette else JadeLightPalette
    ColorTheme.INDIGO -> if (dark) IndigoDarkPalette else IndigoLightPalette
    ColorTheme.ROSE -> if (dark) RoseDarkPalette else RoseLightPalette
    ColorTheme.AMBER -> if (dark) AmberDarkPalette else AmberLightPalette
}

private fun colorSchemeFor(palette: LintiPalette, night: Boolean): ColorScheme {
    val base = if (night) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = palette.primary,
        onPrimary = palette.onPrimary,
        primaryContainer = palette.primarySoft,
        onPrimaryContainer = palette.primary,
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
        onSurfaceVariant = palette.textSecondary,
        surfaceContainerLowest = palette.surface,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surface,
        surfaceContainerHigh = palette.surface,
        surfaceContainerHighest = palette.background,
        outline = palette.textSecondary,
        outlineVariant = palette.line,
        error = palette.error,
        onError = palette.onPrimary,
        errorContainer = palette.errorSoft,
        onErrorContainer = palette.error,
        inverseSurface = palette.textPrimary,
        inverseOnSurface = palette.surface,
        surfaceTint = palette.primary,
    )
}

private fun dynamicPalette(scheme: ColorScheme, dark: Boolean): LintiPalette = LintiPalette(
    primary = scheme.primary,
    onPrimary = scheme.onPrimary,
    primarySoft = scheme.primaryContainer,
    primaryPale = scheme.surfaceVariant,
    background = scheme.background,
    surface = scheme.surface,
    textPrimary = scheme.onBackground,
    textSecondary = scheme.onSurfaceVariant,
    line = scheme.outlineVariant,
    success = if (dark) Color(0xFF34D399) else Color(0xFF047857),
    successSoft = if (dark) Color(0xFF064E3B) else Color(0xFFECFDF5),
    error = scheme.error,
    errorSoft = scheme.errorContainer,
    warning = if (dark) Color(0xFFFBBF24) else Color(0xFFB45309),
    warningSoft = if (dark) Color(0xFF4A3208) else Color(0xFFFFFBEB),
    aiAccent = scheme.tertiary,
    aiSoft = scheme.tertiaryContainer,
)

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
    val dynamic = colorTheme == ColorTheme.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = if (dynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        colorSchemeFor(colorTheme.palette(dark), dark)
    }
    val palette = if (dynamic) dynamicPalette(scheme, dark) else colorTheme.palette(dark)
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
