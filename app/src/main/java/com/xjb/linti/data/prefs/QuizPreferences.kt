package com.xjb.linti.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.ColorTheme
import com.xjb.linti.domain.FontScale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class QuizPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _autoNextOnCorrect = MutableStateFlow(prefs.getBoolean(KEY_AUTO_NEXT, true))
    val autoNextOnCorrect: StateFlow<Boolean> = _autoNextOnCorrect

    private val _fontScale = MutableStateFlow(readFontScale())
    val fontScale: StateFlow<FontScale> = _fontScale

    private val _appearanceMode = MutableStateFlow(readAppearanceMode())
    val appearanceMode: StateFlow<AppearanceMode> = _appearanceMode

    private val _colorTheme = MutableStateFlow(readColorTheme())
    val colorTheme: StateFlow<ColorTheme> = _colorTheme

    private val _autoCheckUpdates = MutableStateFlow(prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true))
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates

    fun setAutoNextOnCorrect(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_NEXT, value).apply()
        _autoNextOnCorrect.value = value
    }

    fun setFontScale(value: FontScale) {
        prefs.edit().putString(KEY_FONT_SCALE, value.name).apply()
        _fontScale.value = value
    }

    fun setAppearanceMode(value: AppearanceMode) {
        prefs.edit().putString(KEY_APPEARANCE_MODE, value.name).apply()
        _appearanceMode.value = value
    }

    fun setColorTheme(value: ColorTheme) {
        prefs.edit().putString(KEY_COLOR_THEME, value.name).apply()
        _colorTheme.value = value
    }

    fun setAutoCheckUpdates(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATES, value).apply()
        _autoCheckUpdates.value = value
    }

    private fun readFontScale(): FontScale =
        FontScale.entries.firstOrNull { it.name == prefs.getString(KEY_FONT_SCALE, null) }
            ?: FontScale.STANDARD

    private fun readAppearanceMode(): AppearanceMode {
        AppearanceMode.entries.firstOrNull {
            it.name == prefs.getString(KEY_APPEARANCE_MODE, null)
        }?.let { return it }
        return when (prefs.getString(KEY_LEGACY_READING_MODE, null)) {
            "NIGHT" -> AppearanceMode.DARK
            "EYE_CARE" -> AppearanceMode.LIGHT
            else -> AppearanceMode.SYSTEM
        }
    }

    private fun readColorTheme(): ColorTheme =
        ColorTheme.entries.firstOrNull { it.name == prefs.getString(KEY_COLOR_THEME, null) }
            ?: ColorTheme.DEFAULT

    private companion object {
        const val FILE_NAME = "linti_quiz_prefs"
        const val KEY_AUTO_NEXT = "autoNextOnCorrect"
        const val KEY_FONT_SCALE = "fontScale"
        const val KEY_APPEARANCE_MODE = "appearanceMode"
        const val KEY_COLOR_THEME = "colorTheme"
        const val KEY_AUTO_CHECK_UPDATES = "autoCheckUpdates"
        const val KEY_LEGACY_READING_MODE = "readingMode"
    }
}
