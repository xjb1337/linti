package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.Chapter
import com.xjb.linti.domain.QuestionType
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
class ChapterScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private val chapters = listOf(Chapter(11, 1, "基础概念", 0), Chapter(12, 1, "综合运用", 1))
    private val questions = (1L..14L).map { topicQuestion(it, 1, if (it % 2L == 0L) QuestionType.MULTI else QuestionType.SINGLE).copy(chapterId = when { it <= 6 -> 11; it <= 10 -> 12; else -> null }) }
    @Before fun recordOnly() { assumeTrue(System.getProperty("roborazzi.test.record") == "true") }
    @Test fun chapterManagementAndSelection() {
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            ChapterContent(chapters, questions, {}, {}, { _, _ -> }, {}, { _, _ -> }, { _, _ -> })
        } } }
        capture("chapter_management")
        compose.onNodeWithText("基础概念").performClick()
        compose.onNodeWithTag("chapter-question-1").performClick()
        compose.onNodeWithTag("chapter-question-2").performClick()
        capture("chapter_batch_selection")
    }
    @Test fun topicChapterFilter() {
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) {
            TopicPracticeSetupContent(listOf(topicBank(1, "虚构题库")), questions, 1, {}, { _, _, _ -> }, chapters = chapters)
        } } }
        compose.onNodeWithTag("topic-chapter-12").performClick()
        capture("chapter_topic_filter")
    }
    private fun capture(name: String) { compose.waitForIdle(); compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/$name.png") }
}
