package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
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
class FlashcardScheduleScreenshotTest {
    @get:Rule val compose = createComposeRule()
    @Before fun recordOnly() { assumeTrue(System.getProperty("roborazzi.test.record") == "true") }

    @Test fun captureScheduleStates() {
        var state by mutableIntStateOf(0)
        compose.setContent {
            LintiTheme(appearanceMode = AppearanceMode.LIGHT) {
                Surface(Modifier.fillMaxSize()) {
                    if (state == 3) {
                        Column(Modifier.fillMaxSize().padding(20.dp)) {
                            LintiCard { DailyNewCardsSettingsContent(20) {} }
                        }
                    } else {
                        FlashcardStudyContent(
                            questions = if (state == 2) emptyList() else listOf(fakeQuestion()), index = 0,
                            dueCount = if (state == 2) 0 else 1, all = false, loading = false,
                            nextDueText = "2026-06-02 10:00", showAnswer = state == 1, rememberedDays = 4,
                            saving = false, onBack = {}, onModeChange = {}, onToggleAnswer = {},
                            onPrevious = {}, onNext = {}, onReview = {},
                        )
                    }
                }
            }
        }
        capture("flashcard_schedule_front")
        compose.runOnIdle { state = 1 }; capture("flashcard_schedule_back")
        compose.runOnIdle { state = 2 }; capture("flashcard_schedule_complete")
        compose.runOnIdle { state = 3 }; capture("flashcard_schedule_settings")
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/$name.png")
    }
}
