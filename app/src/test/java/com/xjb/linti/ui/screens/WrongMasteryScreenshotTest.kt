package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.ui.components.LintiCard
import com.xjb.linti.ui.theme.LintiTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class WrongMasteryScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Before fun recordOnly() { assumeTrue(System.getProperty("roborazzi.test.record") == "true") }

    @Test fun pendingAndMasteredGroups() {
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            WrongBookContent(masteryItems(), listOf(masteryBank()), null, 2, {}, {}, {}, now = 1000)
        } } }
        capture("mastery_pending")
        compose.onNodeWithText("已掌握 1").performClick()
        capture("mastery_mastered")
    }

    @Test fun thresholdSetting() {
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(20.dp)) { LintiCard { MasterySettingsContent(2) {} } }
        } } }
        capture("mastery_settings")
    }
    private fun capture(name: String) { compose.waitForIdle(); compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/$name.png") }
}
