package com.xjb.linti.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class LintiChoiceCardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionAndCorrectnessRemainAccessible() {
        var selected by mutableStateOf(false)
        var reveal by mutableStateOf(false)
        compose.setContent { LintiTheme { LintiChoiceCard("A", "虚构选项", selected, false, reveal, false) { selected = !selected } } }
        compose.onNodeWithContentDescription("A，虚构选项").performClick()
        compose.runOnIdle { assertTrue(selected); reveal = true }
        compose.onNodeWithContentDescription("A，虚构选项，错误选项，已选").assertExists()
    }

    @Test fun roleAndSelectedStateSurviveSemanticsMerge() {
        var selected by mutableStateOf(false)
        compose.setContent { LintiTheme { LintiChoiceCard("A", "虚构选项", selected, false, false, false) { selected = !selected } } }
        val unselected = compose.onNodeWithContentDescription("A，虚构选项").fetchSemanticsNode()
        assertEquals(Role.RadioButton, unselected.config[SemanticsProperties.Role])
        assertEquals(false, unselected.config[SemanticsProperties.Selected])
        compose.onNodeWithContentDescription("A，虚构选项").performClick()
        compose.runOnIdle { assertTrue(selected) }
        val node = compose.onNodeWithContentDescription("A，虚构选项，已选").fetchSemanticsNode()
        assertEquals(Role.RadioButton, node.config[SemanticsProperties.Role])
        assertEquals(true, node.config[SemanticsProperties.Selected])
    }
}
