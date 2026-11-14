package com.xjb.linti.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class DailyNewCardsPreferencesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun reset() { context.getSharedPreferences("linti_quiz_prefs", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun defaultChoicesAndUnlimitedPersist() {
        val prefs = QuizPreferences(context)
        assertEquals(20, prefs.dailyNewCards.value)
        listOf<Int?>(10, 20, 50, null).forEach { value ->
            prefs.setDailyNewCards(value)
            assertEquals(value, prefs.dailyNewCards.value)
            assertEquals(value, QuizPreferences(context).dailyNewCards.value)
        }
        assertTrue(runCatching { prefs.setDailyNewCards(30) }.isFailure)
        context.getSharedPreferences("linti_quiz_prefs", Context.MODE_PRIVATE).edit().putInt("dailyNewCards", 30).commit()
        assertEquals(20, QuizPreferences(context).dailyNewCards.value)
    }
}
