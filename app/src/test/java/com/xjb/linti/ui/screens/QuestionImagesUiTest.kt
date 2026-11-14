package com.xjb.linti.ui.screens

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.data.db.ExamDraftSnapshot
import com.xjb.linti.data.images.QuestionImageStore
import com.xjb.linti.data.repo.SessionSnapshot
import com.xjb.linti.domain.*
import com.xjb.linti.ui.components.QuestionImages
import com.xjb.linti.ui.theme.LintiTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

private fun fictionalPicture(store: QuestionImageStore, color: Int): QuestionImage {
    val bitmap = Bitmap.createBitmap(320, 200, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.rgb(239, 245, 246))
    val paint = Paint().apply { this.color = color; isAntiAlias = true }
    canvas.drawCircle(100f, 100f, 62f, paint)
    paint.color = Color.rgb(40, 80, 90)
    canvas.drawRect(185f, 56f, 265f, 145f, paint)
    val output = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    bitmap.recycle()
    return store.import(ByteArrayInputStream(output.toByteArray()))
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class QuestionImagesUiTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var store: QuestionImageStore
    private lateinit var first: QuestionImage
    private lateinit var second: QuestionImage

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        store = QuestionImageStore(File(app.filesDir, "question_images"))
        first = fictionalPicture(store, Color.RED)
        second = fictionalPicture(store, Color.BLUE)
    }
    @After fun clean() { store.file(first).delete(); store.file(second).delete() }

    @Test fun imageControlsReorderDeleteAndViewerSupportsPaging() {
        var current = listOf(first, second)
        compose.setContent {
            var images by remember { mutableStateOf(current) }
            LintiTheme { Surface(Modifier.fillMaxSize()) { Column {
                QuestionImages(images) { images = it; current = it }
            } } }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("题干图片").fetchSemanticsNodes().size == 2 }
        compose.onAllNodesWithContentDescription("图片右移")[0].performClick()
        assertEquals(listOf(second, first), current)
        compose.onAllNodesWithContentDescription("题干图片")[0].performClick()
        compose.onNodeWithContentDescription("关闭图片").assertExists()
        compose.onNodeWithText("1 / 2").assertExists()
        compose.onNodeWithTag("image-viewer-page-0").performTouchInput { swipeLeft() }
        compose.onNodeWithText("2 / 2").assertExists()
        val page = compose.onNodeWithTag("image-viewer-page-1")
        page.performTouchInput { doubleClick() }
        page.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "缩放 250%"))
        page.performTouchInput { doubleClick() }
        page.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "缩放 100%"))
        page.performTouchInput {
            down(0, center + Offset(-30f, 0f)); down(1, center + Offset(30f, 0f))
            moveTo(0, center + Offset(-90f, 0f)); moveTo(1, center + Offset(90f, 0f))
            up(1); up(0)
        }
        page.assert(SemanticsMatcher("图片已缩放") {
            val label = it.config[SemanticsProperties.StateDescription]
            label != "缩放 100%"
        })
        page.performTouchInput { swipeLeft() }
        compose.onNodeWithText("2 / 2").assertExists()
        compose.onNodeWithContentDescription("关闭图片").performClick()
        compose.onAllNodesWithContentDescription("删除图片")[0].performClick()
        assertEquals(listOf(first), current)
    }

    @Test fun missingImageShowsExplicitPlaceholder() {
        val missing = first.copy(fileName = "0".repeat(64) + ".png")
        compose.setContent { LintiTheme { QuestionImages(listOf(missing)) } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("图片已丢失").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("图片已丢失").assertExists()
        compose.onNodeWithText("图片已丢失").performClick()
        compose.onNodeWithContentDescription("关闭图片").assertExists()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class QuestionImagesScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private var fixture: QuizToggleTestFixture? = null
    private lateinit var picture: QuestionImage

    @Before fun setup() = runBlocking {
        assumeTrue(System.getProperty("roborazzi.test.record") == "true")
        fixture = QuizToggleTestFixture().also { it.setup() }
        val f = fixture!!
        picture = fictionalPicture(f.container.imageStore, Color.rgb(214, 110, 99))
        val original = f.container.repository.getQuestion(1)!!
        f.container.repository.saveQuestion(original.copy(images = listOf(picture)))
        f.container.imageStore.release(picture)
    }
    @After fun close() {
        fixture?.let { f -> f.container.imageStore.file(picture).delete(); f.close() }
    }
    private fun capture(name: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("题干图片").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/$name.png")
    }
    @Test fun editor() {
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { QuestionEditScreen(fixture!!.viewModel, 1, {}) } } }
        capture("question_images_editor")
    }
    @Test fun practice() = runBlocking {
        fixture!!.startPractice()
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { QuizScreen(fixture!!.viewModel, {}, {}) } } }
        capture("question_images_practice")
    }
    @Test fun fullscreen() = runBlocking {
        fixture!!.startPractice()
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { QuizScreen(fixture!!.viewModel, {}, {}) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("题干图片").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("题干图片").performClick()
        compose.onNodeWithContentDescription("关闭图片").assertExists()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/question_images_fullscreen.png")
    }
    @Test fun examReview() = runBlocking {
        val f = fixture!!
        val question = QuizQuestion(1, 1, "1", QuestionType.MULTI, "虚构原卷题干", listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")), "A", "虚构解析", images = listOf(picture))
        val config = ExamConfig(multiCount = 1, passScore = 0.0)
        val questions = listOf(question); val answers = mapOf(1L to "A")
        f.container.repository.createExamDraft(ExamDraftSnapshot("图题原卷", 1, "虚构题库", config, questions, answers, emptySet(), emptySet(), emptySet(), 0, 1, 0))
        val id = f.container.repository.submitExamSession(SessionSnapshot(1, "虚构题库", PracticeMode.EXAM, config, questions, answers, emptySet(), emptySet(), 1, 2, Grader.grade(questions, answers, config)), emptyList(), "图题原卷")
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { ExamPaperReviewScreen(f.viewModel, id, false, {}) } } }
        capture("question_images_exam_review")
    }
    @Test fun missing() {
        fixture!!.container.imageStore.file(picture).delete()
        compose.setContent { LintiTheme { Surface(Modifier.fillMaxSize()) { QuestionEditScreen(fixture!!.viewModel, 1, {}) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("图片已丢失").fetchSemanticsNodes().isNotEmpty() }
        compose.onRoot().captureRoboImage(filePath = "build/outputs/roborazzi/question_images_missing.png")
    }
}
