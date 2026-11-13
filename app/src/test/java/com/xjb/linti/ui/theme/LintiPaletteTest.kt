package com.xjb.linti.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LintiPaletteTest {

    private val palettes = listOf(
        "默认浅色" to DefaultLightPalette,
        "默认深色" to DefaultDarkPalette,
        "青绿浅色" to JadeLightPalette,
        "青绿深色" to JadeDarkPalette,
        "靛蓝浅色" to IndigoLightPalette,
        "靛蓝深色" to IndigoDarkPalette,
        "玫瑰浅色" to RoseLightPalette,
        "玫瑰深色" to RoseDarkPalette,
        "琥珀浅色" to AmberLightPalette,
        "琥珀深色" to AmberDarkPalette,
    )

    @Test
    fun `all static themes provide readable text and status colors`() {
        palettes.forEach { (name, palette) ->
            assertContrast(name, "正文/背景", palette.textPrimary, palette.background)
            assertContrast(name, "正文/卡片", palette.textPrimary, palette.surface)
            assertContrast(name, "次要文字/背景", palette.textSecondary, palette.background)
            assertContrast(name, "次要文字/卡片", palette.textSecondary, palette.surface)
            assertContrast(name, "主色/浅色底", palette.primary, palette.primaryPale)
            assertContrast(name, "主色/柔和底", palette.primary, palette.primarySoft)
            assertContrast(name, "主色按钮文字", palette.onPrimary, palette.primary)
            assertContrast(name, "禁用按钮", palette.textPrimary, palette.line)
            assertContrast(name, "成功状态", palette.success, palette.successSoft)
            assertContrast(name, "错误状态", palette.error, palette.errorSoft)
            assertContrast(name, "警告状态", palette.warning, palette.warningSoft)
            assertContrast(name, "AI 状态", palette.aiAccent, palette.aiSoft)
        }
    }

    @Test
    fun `each theme has distinct light and dark surfaces`() {
        val themes = listOf(
            DefaultLightPalette to DefaultDarkPalette,
            JadeLightPalette to JadeDarkPalette,
            IndigoLightPalette to IndigoDarkPalette,
            RoseLightPalette to RoseDarkPalette,
            AmberLightPalette to AmberDarkPalette,
        )
        themes.forEach { (light, dark) ->
            assertTrue(light.background.luminance() > dark.background.luminance())
            assertTrue(light.surface.luminance() > dark.surface.luminance())
            assertTrue(light.textPrimary.luminance() < dark.textPrimary.luminance())
        }
    }

    @Test
    fun `static themes use distinct accent colors`() {
        val accents = palettes.map { it.second.primary }
        assertEquals(accents.size, accents.toSet().size)
    }

    private fun assertContrast(name: String, pair: String, foreground: Color, background: Color) {
        val ratio = contrastRatio(foreground, background)
        assertTrue(
            "$name $pair 对比度不足：${"%.2f".format(ratio)}",
            ratio >= MIN_TEXT_CONTRAST,
        )
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val lighter = maxOf(first.luminance(), second.luminance()).toDouble()
        val darker = minOf(first.luminance(), second.luminance()).toDouble()
        return (lighter + 0.05) / (darker + 0.05)
    }

    private companion object {
        const val MIN_TEXT_CONTRAST = 4.5
    }
}
