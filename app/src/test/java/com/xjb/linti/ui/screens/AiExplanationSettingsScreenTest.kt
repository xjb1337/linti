package com.xjb.linti.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.prefs.AiExplanationReasoningEffort
import com.xjb.linti.data.prefs.SecureSettingsStore
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.ui.theme.LintiTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class AiExplanationSettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: QuizToggleTestFixture
    private var rendering by mutableStateOf(true)

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf("linti_secure_settings", "linti_settings").forEach {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
        fixture = QuizToggleTestFixture().also { it.setup() }
    }

    @After fun close() {
        compose.runOnIdle { rendering = false }
        compose.waitForIdle()
        fixture.close()
    }

    @Test fun allFourOptionsAreSelectableAndPersistImmediately() {
        compose.setContent {
            if (rendering) LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(Modifier.fillMaxSize()) { SettingsScreen(fixture.viewModel) }
            }
        }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("reasoning-effort-high"))
        compose.onNodeWithText("思考强度").assertExists()
        compose.onNodeWithTag("reasoning-effort-high").performScrollTo().assertIsSelected()
        for (effort in AiExplanationReasoningEffort.entries) {
            val node = compose.onNodeWithTag("reasoning-effort-${effort.apiValue}")
            node.performScrollTo().performClick().assertIsSelected()
            AiExplanationReasoningEffort.entries.filter { it != effort }.forEach {
                compose.onNodeWithTag("reasoning-effort-${it.apiValue}").assertIsNotSelected()
            }
            compose.runOnIdle {
                assertEquals(effort, fixture.viewModel.settings.value.aiExplanationReasoningEffort)
                assertEquals(effort, SecureSettingsStore(ApplicationProvider.getApplicationContext()).snapshot().aiExplanationReasoningEffort)
            }
        }
    }
}
