package com.xjb.linti.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.db.QuestionBankEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.QuizClock
import com.xjb.linti.ui.LintiViewModel
import com.xjb.linti.ui.theme.LintiTheme
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w360dp-h780dp-xxhdpi", application = android.app.Application::class)
class FlashcardScreenIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var db: LintiDatabase
    private lateinit var viewModel: LintiViewModel

    @Before fun setup() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, LintiDatabase::class.java).allowMainThreadQueries().build()
        db.questionBankDao().insert(QuestionBankEntity(id = 1, name = "虚构题库", createdAt = 1, updatedAt = 1))
        listOf("第一道虚构题", "第二道虚构题").forEachIndexed { index, stem ->
            db.questionDao().insert(QuestionEntity(id = index.toLong() + 1, bankId = 1, sourceNumber = "${index + 1}", type = "SINGLE", stem = stem, optionsJson = "[]", answer = "A", explanation = "", sourceFileName = "虚构来源", createdAt = 1, updatedAt = 1))
        }
        val container = AppContainer(app, db)
        container.quizPreferences.setAutoCheckUpdates(false)
        container.quizPreferences.setDailyNewCards(20)
        viewModel = LintiViewModel(app, container, QuizClock { 1_780_290_000_000 }, flashcardZone = ZoneId.of("Asia/Shanghai"))
    }

    @After fun close() { db.close() }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun forgottenCardMovesToTailAndRatingsAdvanceOnFrontInBothModes() {
        compose.setContent { LintiTheme(appearanceMode = AppearanceMode.LIGHT) { Surface(Modifier.fillMaxSize()) { FlashcardsScreen(viewModel, null, {}) } } }
        waitForText("第一道虚构题")
        compose.onNodeWithText("待复习 2").assertExists()
        compose.onNodeWithTag("flashcard-flip-button").performClick()
        compose.onNodeWithTag("flashcard-forgot").performClick()
        waitForText("第二道虚构题")
        compose.onNodeWithText("看答案").assertExists()
        compose.onNodeWithTag("flashcard-flip-button").performClick()
        compose.onNodeWithTag("flashcard-remembered").performClick()
        waitForText("第一道虚构题")
        compose.onNodeWithText("看答案").assertExists()
        compose.onNodeWithTag("flashcard-flip-button").performClick()
        compose.onNodeWithTag("flashcard-remembered").performClick()
        waitForText("今天复习完了")
        compose.onNodeWithText("全部").performClick()
        waitForText("第一道虚构题")
        compose.onNodeWithTag("flashcard-flip-button").performClick()
        compose.onNodeWithTag("flashcard-remembered").performClick()
        waitForText("第二道虚构题")
        compose.onNodeWithText("看答案").assertExists()
    }
}
