package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.domain.AppearanceMode
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
class PastPaperScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Before fun recordOnly() { assumeTrue(System.getProperty("roborazzi.test.record") == "true") }
    @Test fun yearGroups() {
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            PastPaperListContent(fictionalPapers, {}, {}, {})
        } } }
        capture("past_paper_years")
    }
    @Test fun paperDetail() {
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            PastPaperDetailContent(fictionalPapers[1], fictionalItems, fictionalQuestions, {}, {}, {}, {}, { _, _ -> }, {}, { _, _ -> }, {})
        } } }
        capture("past_paper_detail")
    }
    @Test fun newPaperForm() {
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            PastPaperFormContent(null, {}) { _, _, _, _ -> }
        } } }
        capture("past_paper_form")
    }
    private fun capture(name: String) { compose.waitForIdle(); compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/$name.png") }
}
