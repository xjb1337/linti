package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class SettingsAppearanceContentTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var fixture: QuizToggleTestFixture
    private var rendering by mutableStateOf(true)

    @Before fun setup() = runBlocking { fixture = QuizToggleTestFixture().also { it.setup() } }
    @After fun close() {
        compose.runOnIdle { rendering = false }
        compose.waitForIdle()
        fixture.container.quizPreferences.setAppearanceMode(AppearanceMode.SYSTEM)
        fixture.close()
    }
    private fun show() {
        compose.setContent { if (rendering) LintiTheme { Surface(Modifier.fillMaxSize()) { SettingsScreen(fixture.viewModel) } } }
    }
    @Test fun appearanceSelectionIsSaved() {
        compose.setContent {
            if (rendering) LintiTheme {
                Surface(Modifier.fillMaxSize()) { AppearanceSettingsScreen(fixture.viewModel, onBack = {}) }
            }
        }
        compose.onNodeWithText("显示模式").performClick()
        compose.onNodeWithText("深色").assertIsDisplayed().performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(AppearanceMode.DARK, fixture.container.quizPreferences.appearanceMode.value) }
        compose.onNodeWithText("深色").assertIsDisplayed()
    }
    @Test fun scrollingShowsCompactTitle() {
        show()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("关于与更新"))
        compose.onNodeWithText("我的").assertIsDisplayed()
    }
}
