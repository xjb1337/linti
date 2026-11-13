package com.xjb.linti.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class LintiPalette(
    val primary: Color,
    val onPrimary: Color,
    val primarySoft: Color,
    val primaryPale: Color,
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val line: Color,
    val success: Color,
    val successSoft: Color,
    val error: Color,
    val errorSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val aiAccent: Color,
    val aiSoft: Color,
)

val DayPalette = LintiPalette(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFFFFFF),
    primarySoft = Color(0xFFCCFBF1),
    primaryPale = Color(0xFFF0FDFA),
    background = Color(0xFFF4F7F6),
    surface = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF64748B),
    line = Color(0xFFE2E8F0),
    success = Color(0xFF059669),
    successSoft = Color(0xFFECFDF5),
    error = Color(0xFFDC2626),
    errorSoft = Color(0xFFFEF2F2),
    warning = Color(0xFFD97706),
    warningSoft = Color(0xFFFFFBEB),
    aiAccent = Color(0xFF4F46E5),
    aiSoft = Color(0xFFEEF2FF),
)

val NightPalette = LintiPalette(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primarySoft = Color(0xFF134E4A),
    primaryPale = Color(0xFF0B3A37),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    textPrimary = Color(0xFFF1F5F9),
    textSecondary = Color(0xFF94A3B8),
    line = Color(0xFF334155),
    success = Color(0xFF34D399),
    successSoft = Color(0xFF064E3B),
    error = Color(0xFFF87171),
    errorSoft = Color(0xFF4C1D1D),
    warning = Color(0xFFFBBF24),
    warningSoft = Color(0xFF4A3208),
    aiAccent = Color(0xFFA5B4FC),
    aiSoft = Color(0xFF1E1B4B),
)

val EyeCarePalette = LintiPalette(
    primary = Color(0xFF0F766E),
    onPrimary = Color(0xFFFBF6E6),
    primarySoft = Color(0xFFD6E9E2),
    primaryPale = Color(0xFFE8F2EC),
    background = Color(0xFFF5EFD9),
    surface = Color(0xFFFBF6E6),
    textPrimary = Color(0xFF3B3226),
    textSecondary = Color(0xFF6B5F4B),
    line = Color(0xFFE0D4B0),
    success = Color(0xFF2F7A55),
    successSoft = Color(0xFFE0EFE2),
    error = Color(0xFFB23B34),
    errorSoft = Color(0xFFF6E2DE),
    warning = Color(0xFF9A6A16),
    warningSoft = Color(0xFFF6ECD0),
    aiAccent = Color(0xFF4F46E5),
    aiSoft = Color(0xFFE8E5F5),
)

val LocalLintiPalette = staticCompositionLocalOf { DayPalette }
