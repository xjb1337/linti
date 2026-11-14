package com.xjb.linti.data.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class AiExplanationSettingsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun reset() {
        listOf("linti_secure_settings", "linti_settings").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun defaultsToHighAndEachChoiceSurvivesNewStoreAndOtherSettingsChanges() {
        assertEquals(AiExplanationReasoningEffort.HIGH, SettingsSnapshot().aiExplanationReasoningEffort)
        val store = SecureSettingsStore(context)
        assertEquals(AiExplanationReasoningEffort.HIGH, store.snapshot().aiExplanationReasoningEffort)
        for (effort in AiExplanationReasoningEffort.entries) {
            store.setAiExplanationReasoningEffort(effort)
            assertEquals(effort, store.snapshot().aiExplanationReasoningEffort)
            assertEquals(effort, SecureSettingsStore(context).snapshot().aiExplanationReasoningEffort)
        }
        store.saveAiConfiguration(AiProvider.DEEPSEEK, "fictional-key", "https://example.invalid/v1", "")
        store.setLocalFirst(false)
        assertEquals(AiExplanationReasoningEffort.MAX, SecureSettingsStore(context).snapshot().aiExplanationReasoningEffort)
    }
}
