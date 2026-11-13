package com.xjb.linti.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.xjb.linti.domain.FontScale
import com.xjb.linti.domain.ReadingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class QuizPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _autoNextOnCorrect = MutableStateFlow(prefs.getBoolean(KEY_AUTO_NEXT, true))
    val autoNextOnCorrect: StateFlow<Boolean> = _autoNextOnCorrect

    private val _fontScale = MutableStateFlow(readFontScale())
    val fontScale: StateFlow<FontScale> = _fontScale

    private val _readingMode = MutableStateFlow(readReadingMode())
    val readingMode: StateFlow<ReadingMode> = _readingMode

    fun setAutoNextOnCorrect(value: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_NEXT, value).apply()
        _autoNextOnCorrect.value = value
    }

    fun setFontScale(value: FontScale) {
        prefs.edit().putString(KEY_FONT_SCALE, value.name).apply()
        _fontScale.value = value
    }

    fun setReadingMode(value: ReadingMode) {
        prefs.edit().putString(KEY_READING_MODE, value.name).apply()
        _readingMode.value = value
    }

    private fun readFontScale(): FontScale =
        FontScale.entries.firstOrNull { it.name == prefs.getString(KEY_FONT_SCALE, null) }
            ?: FontScale.STANDARD

    private fun readReadingMode(): ReadingMode =
        ReadingMode.entries.firstOrNull { it.name == prefs.getString(KEY_READING_MODE, null) }
            ?: ReadingMode.DAY

    private companion object {
        const val FILE_NAME = "linti_quiz_prefs"
        const val KEY_AUTO_NEXT = "autoNextOnCorrect"
        const val KEY_FONT_SCALE = "fontScale"
        const val KEY_READING_MODE = "readingMode"
    }
}
