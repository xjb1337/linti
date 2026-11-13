package com.xjb.linti.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LintiPaletteTest {

    @Test
    fun `day palette keeps every v0 color value`() {
        assertEquals(Color(0xFF0F766E), DayPalette.primary)
        assertEquals(Color(0xFFFFFFFF), DayPalette.onPrimary)
        assertEquals(Color(0xFFCCFBF1), DayPalette.primarySoft)
        assertEquals(Color(0xFFF0FDFA), DayPalette.primaryPale)
        assertEquals(Color(0xFFF4F7F6), DayPalette.background)
        assertEquals(Color(0xFFFFFFFF), DayPalette.surface)
        assertEquals(Color(0xFF0F172A), DayPalette.textPrimary)
        assertEquals(Color(0xFF64748B), DayPalette.textSecondary)
        assertEquals(Color(0xFFE2E8F0), DayPalette.line)
        assertEquals(Color(0xFF059669), DayPalette.success)
        assertEquals(Color(0xFFECFDF5), DayPalette.successSoft)
        assertEquals(Color(0xFFDC2626), DayPalette.error)
        assertEquals(Color(0xFFFEF2F2), DayPalette.errorSoft)
        assertEquals(Color(0xFFD97706), DayPalette.warning)
        assertEquals(Color(0xFFFFFBEB), DayPalette.warningSoft)
        assertEquals(Color(0xFF4F46E5), DayPalette.aiAccent)
        assertEquals(Color(0xFFEEF2FF), DayPalette.aiSoft)
    }

    @Test
    fun `night and eye care palettes differ from day`() {
        assertNotEquals(DayPalette.background, NightPalette.background)
        assertNotEquals(DayPalette.background, EyeCarePalette.background)
        assertNotEquals(DayPalette.surface, NightPalette.surface)
        assertNotEquals(DayPalette.surface, EyeCarePalette.surface)
        assertNotEquals(DayPalette.textPrimary, NightPalette.textPrimary)
        assertNotEquals(DayPalette.textPrimary, EyeCarePalette.textPrimary)
        assertNotEquals(Color.White, EyeCarePalette.background)
        assertNotEquals(Color.White, EyeCarePalette.surface)
    }

}
