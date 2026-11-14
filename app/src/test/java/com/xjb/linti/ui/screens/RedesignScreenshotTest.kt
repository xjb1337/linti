package com.xjb.linti.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.xjb.linti.data.aigeneration.AiDraftFields
import com.xjb.linti.data.aigeneration.AiQuestionDraft
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.ui.components.LintiPageHeader
import com.xjb.linti.ui.theme.LintiPageBackground
import com.xjb.linti.ui.theme.LintiTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Screens use a transient in-memory database and never send network requests. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class RedesignScreenshotTest {
    @get:Rule val compose = createComposeRule()
    private var fixture: QuizToggleTestFixture? = null
    private var rendering by mutableStateOf(true)
    private var mode by mutableStateOf(AppearanceMode.LIGHT)
    private val vm get() = requireNotNull(fixture).viewModel
    private val sample = Question(id = 1, stem = "虚构植物需要什么生长条件？", type = QuestionType.SINGLE,
        options = listOf(QuestionOption("A", "充足光照"), QuestionOption("B", "完全避光")),
        answer = "A", explanation = "虚构解析：植物需要光照。")

    @Before fun setup() = runBlocking {
        assumeTrue("截图仅在录制模式运行", System.getProperty("roborazzi.test.record") == "true")
        fixture = QuizToggleTestFixture().also { it.setup() }
    }
    @After fun tearDown() {
        if (fixture != null) {
            compose.runOnIdle { rendering = false }
            compose.waitForIdle()
            fixture?.close()
        }
    }
    private fun show(content: @Composable () -> Unit) {
        compose.setContent { if (rendering) LintiTheme(appearanceMode = mode) {
            LintiPageBackground { content() }
        } }
    }
    private fun waitFor(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String, modal: Boolean = false) {
        compose.waitForIdle()
        val root = if (modal) compose.onAllNodes(isRoot())[1] else compose.onRoot()
        root.captureRoboImage(filePath = "build/outputs/roborazzi/redesign/$name.png")
        compose.runOnIdle { mode = AppearanceMode.DARK }
        compose.waitForIdle()
        val darkRoot = if (modal) compose.onAllNodes(isRoot())[1] else compose.onRoot()
        darkRoot.captureRoboImage(filePath = "build/outputs/roborazzi/redesign/${name}_dark.png")
    }

    @Test fun home() {
        show { HomeScreen(vm, {}, {}, {}, {}, {}) }
        waitFor("临题")
        capture("home")
    }
    @Test fun bankList() {
        show { BanksScreen(vm, {}, {}) }
        waitFor("虚构题库")
        capture("bank_list")
    }
    @Test fun bankDetail() {
        show { BankHomeScreen(vm, 1, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        waitFor("虚构题库")
        capture("bank_detail")
    }
    @Test fun practiceHub() {
        show { PracticeHubScreen(vm, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
        waitFor("虚构题库")
        capture("practice_hub")
    }
    @Test fun quiz() = runBlocking {
        requireNotNull(fixture).startPractice()
        show { QuizScreen(vm, {}, {}) }
        waitFor("虚构题干")
        capture("quiz")
    }
    @Test fun recite() {
        show { ReciteScreen(vm, 1, {}) }
        waitFor("虚构题干")
        capture("recite")
    }
    @Test fun examSetup() {
        show { ExamSetupScreen(vm, 1, {}, {}) }
        waitFor("模拟考试")
        capture("exam_setup")
    }
    @Test fun examReview() = runBlocking {
        val sessionId = requireNotNull(fixture).createReview()
        show { ExamPaperReviewScreen(vm, sessionId, false, {}) }
        waitFor("虚构原卷题干")
        capture("exam_review")
    }
    @Test fun wrongBook() {
        show { WrongBookContent(masteryItems(), listOf(masteryBank()), null, 2, {}, {}, {}, now = 1000) }
        capture("wrong_book")
    }
    @Test fun flashcard() {
        show { FlashcardContent(listOf(sample), 0, true, {}, {}, {}) }
        capture("flashcard")
    }
    @Test fun pastPapers() {
        show { PastPaperListContent(fictionalPapers, {}, {}, {}) }
        capture("past_papers")
    }
    @Test fun aiQuestion() {
        show { Column { LintiPageHeader("AI 出题", modifier = Modifier.padding(horizontal = 16.dp), onBack = {}); AiQuestionFormContent(true, "虚构植物的生长条件", emptySet(), QuestionType.SINGLE, 3, emptyList(), false, null, {}, {}, {}, {}, {}, {}, {}) } }
        capture("ai_question")
    }
    @Test fun materialQuestion() {
        show { Column { LintiPageHeader("材料出题", modifier = Modifier.padding(horizontal = 16.dp), onBack = {}); MaterialQuestionFormContent("虚构材料：植物需要光照。", mapOf(QuestionType.SINGLE to 2), false, emptyList(), null, null, {}, { _, _ -> }, {}, {}, {}, {}, {}) } }
        capture("material_question")
    }
    @Test fun aiReview() {
        val fields = AiDraftFields("SINGLE", sample.stem, sample.options, sample.answer, sample.explanation)
        show { Column { LintiPageHeader("待校对", modifier = Modifier.padding(horizontal = 16.dp), onBack = {}); AiDraftListContent(listOf(AiQuestionDraft(1, 1, fields, emptyList(), 1)), {}, {}, {}, {}) } }
        capture("ai_review")
    }
    @Test fun importFlow() {
        show { SplitImportScreen(vm, {}, {}) }
        capture("import_flow")
    }
    @Test fun importReview() = runBlocking {
        vm.startManualEntry()
        val draft = vm.importState.value.drafts.first()
        vm.updateImportDraft(0, draft.copy(stem = sample.stem, options = sample.options, answer = sample.answer))
        show { ImportReviewScreen(vm, {}, {}) }
        capture("import_review")
    }
    @Test fun questionEdit() {
        show { QuestionEditScreen(vm, 1, {}) }
        waitFor("虚构题干")
        capture("question_edit")
    }
    @Test fun questionDetail() {
        show { QuestionExplanationDetail(1, vm, {}) }
        waitFor("虚构题干")
        capture("question_detail", modal = true)
    }
    @Test fun settings() {
        show { SettingsScreen(vm) }
        capture("settings")
    }
    @Test fun aiSettings() {
        show { SettingsScreen(vm) }
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("API Key"))
        capture("ai_settings")
    }
}
