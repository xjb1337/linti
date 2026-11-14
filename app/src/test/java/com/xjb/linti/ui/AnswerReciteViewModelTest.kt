package com.xjb.linti.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.db.QuestionBankEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.repo.PracticeCommit
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.QuizClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class AnswerReciteViewModelTest {
    private lateinit var db: LintiDatabase
    private lateinit var container: AppContainer
    private lateinit var viewModel: LintiViewModel
    private var now = 10_000L

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, LintiDatabase::class.java).allowMainThreadQueries().build()
        db.questionBankDao().insert(QuestionBankEntity(id = 1, name = "虚构题库", createdAt = 1, updatedAt = 1))
        (1L..4L).forEach { id ->
            val type = when (id) { 3L -> "SINGLE"; 4L -> "FILL"; else -> "MULTI" }
            db.questionDao().insert(QuestionEntity(
                id = id, bankId = 1, sourceNumber = "$id", type = type, stem = "虚构题$id",
                optionsJson = """[{"label":"A","text":"虚构甲"},{"label":"B","text":"虚构乙"},{"label":"C","text":"虚构丙"}]""",
                answer = when (id) { 3L -> "A"; 4L -> "虚构答案"; else -> "AC" },
                explanation = "虚构解析$id", sourceFileName = "虚构来源", createdAt = id, updatedAt = id,
            ))
        }
        container = AppContainer(app, db)
        container.quizPreferences.setAutoCheckUpdates(false)
        container.quizPreferences.setAutoNextOnCorrect(false)
        viewModel = LintiViewModel(app, container, QuizClock { now })
    }

    @After fun close() {
        viewModel.leaveQuiz()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun awaitAttempts(count: Int) {
        withTimeout(5_000) {
            while (db.questionAttemptDao().getAll().size < count) delay(10)
        }
    }

    @Test fun switchingPreservesAnswersAndPositionAndMarksEveryVisitedQuestion() = runBlocking {
        assertTrue(viewModel.startSequenceQuiz(1).started)
        viewModel.selectAnswer(1, "A")
        viewModel.goToQuestion(1)
        viewModel.selectAnswer(2, "B")
        val before = viewModel.quizState.value
        assertEquals(1, before.currentIndex)
        assertEquals(mapOf(1L to "A", 2L to "B"), before.answers)
        viewModel.setReciting(true)
        assertTrue(viewModel.isRevealed(2))
        assertFalse(viewModel.isRevealed(1))
        assertEquals(setOf(2L), viewModel.quizState.value.answerViewedIds)
        assertEquals(before.answers, viewModel.quizState.value.answers)
        assertEquals(before.currentIndex, viewModel.quizState.value.currentIndex)
        viewModel.selectAnswer(2, "C")
        viewModel.commitAnswer(2)
        assertEquals(before.answers, viewModel.quizState.value.answers)
        assertTrue(viewModel.quizState.value.committedIds.isEmpty())
        viewModel.goToQuestion(0, fromPager = true)
        assertTrue(viewModel.isRevealed(1))
        viewModel.goToQuestion(2)
        viewModel.goToQuestion(3)
        assertEquals(setOf(1L, 2L, 3L, 4L), viewModel.quizState.value.answerViewedIds)
        viewModel.setFillAnswer(4, "不会改变")
        assertFalse(viewModel.quizState.value.answers.containsKey(4))
        viewModel.goToQuestion(0)
        viewModel.setReciting(false)
        assertFalse(viewModel.isRevealed(1))
        assertEquals(0, viewModel.quizState.value.currentIndex)
        assertEquals("A", viewModel.quizState.value.answers[1])
        viewModel.selectAnswer(1, "C")
        viewModel.commitAnswer(1)
        assertEquals("AC", viewModel.quizState.value.answers[1])
        assertEquals(0, viewModel.attemptStatsFor(1).correct)
        awaitAttempts(1)
        val attempt = db.questionAttemptDao().getAll().single()
        assertTrue(attempt.isCorrect)
        assertTrue(attempt.answerViewed)
        viewModel.leaveQuiz()
        assertTrue(viewModel.startSequenceQuiz(1).started)
        assertFalse(viewModel.quizState.value.reciting)
        assertTrue(1L in viewModel.quizState.value.answerViewedIds)
        now = 20_000
        viewModel.restartSequenceRound(1)
        assertTrue(viewModel.startSequenceQuiz(1).started)
        assertFalse(viewModel.quizState.value.reciting)
        assertTrue(viewModel.quizState.value.answerViewedIds.isEmpty())
    }

    @Test fun previouslySubmittedQuestionIsOnlyViewedAndViewedWrongStillClearsStreak() = runBlocking {
        suspend fun seed(id: Long, correct: Boolean, token: String) {
            container.repository.commitPracticeAnswer(PracticeCommit(
                bankId = 1, mode = PracticeMode.WRONG, questionId = id, questionBankId = 1,
                answer = if (correct) "AC" else "B", isCorrect = correct, currentIndex = 0,
                answeredAt = 1, roundStartedAt = 0, submissionId = token,
            ))
        }
        seed(2, false, "虚构历史错题")
        seed(2, true, "虚构历史答对")
        assertEquals(1, db.wrongItemDao().getByQuestion(2)!!.correctStreak)
        assertTrue(viewModel.startSequenceQuiz(1).started)
        viewModel.goToQuestion(2)
        viewModel.selectAnswer(3, "A")
        awaitAttempts(3)
        val submitted = db.questionAttemptDao().getAll().first { it.questionId == 3L }
        assertFalse(submitted.answerViewed)
        viewModel.setReciting(true)
        assertFalse(3L in viewModel.quizState.value.answerViewedIds)
        assertEquals(submitted, db.questionAttemptDao().getAll().first { it.questionId == 3L })
        viewModel.goToQuestion(1)
        assertTrue(2L in viewModel.quizState.value.answerViewedIds)
        viewModel.setReciting(false)
        viewModel.selectAnswer(2, "B")
        viewModel.commitAnswer(2)
        awaitAttempts(4)
        val wrong = db.wrongItemDao().getByQuestion(2)!!
        assertEquals(2, wrong.wrongCount)
        assertEquals(0, wrong.correctStreak)
        assertTrue(db.questionAttemptDao().getAll().first { it.questionId == 2L && it.answerViewed }.let { !it.isCorrect })
    }

    @Test fun viewedCorrectDoesNotAdvanceAnExistingWrongStreak() = runBlocking {
        container.repository.commitPracticeAnswer(PracticeCommit(
            bankId = 1, mode = PracticeMode.WRONG, questionId = 1, questionBankId = 1,
            answer = "B", isCorrect = false, currentIndex = 0, answeredAt = 1, roundStartedAt = 0,
            submissionId = "虚构历史错题",
        ))
        assertTrue(viewModel.startSequenceQuiz(1).started)
        viewModel.setReciting(true)
        viewModel.setReciting(false)
        viewModel.selectAnswer(1, "A")
        viewModel.selectAnswer(1, "C")
        viewModel.commitAnswer(1)
        awaitAttempts(2)
        assertEquals(0, db.wrongItemDao().getByQuestion(1)!!.correctStreak)
        assertEquals(0, viewModel.attemptStatsFor(1).correct)
    }

    @Test fun examRejectsSwitchIncludingTimedAndKeepsConfiguredRevealBehavior() = runBlocking {
        for ((duration, revealAfterAnswer) in listOf(0 to false, 30 to false, 30 to true)) {
            val config = ExamConfig(singleCount = 1, durationMinutes = duration, passScore = 0.0, showAnswerAfterAnswer = revealAfterAnswer)
            val start = viewModel.startExam(1, config)
            assertTrue(start.message, start.started)
            val state = viewModel.quizState.value
            val id = state.currentQuestion!!.questionId
            assertFalse(state.canSwitchRecite)
            viewModel.setReciting(true)
            assertEquals(state, viewModel.quizState.value)
            assertFalse(viewModel.isRevealed(id))
            viewModel.selectAnswer(id, "A")
            assertEquals(revealAfterAnswer, viewModel.isRevealed(id))
            viewModel.setReciting(true)
            assertFalse(viewModel.quizState.value.reciting)
            assertEquals(revealAfterAnswer, viewModel.isRevealed(id))
            assertTrue(viewModel.discardExamDraft(state.examDraftId!!))
            viewModel.leaveQuiz()
        }
    }

    @Test fun paperReviewRejectsSwitchEvenIfPracticeStateIsStillActive() = runBlocking {
        assertTrue(viewModel.startSequenceQuiz(1).started)
        val before = viewModel.quizState.value
        viewModel.setExamPaperReviewVisible(true)
        viewModel.setReciting(true)
        assertEquals(before, viewModel.quizState.value)
        assertFalse(viewModel.isRevealed(1))
        viewModel.setExamPaperReviewVisible(false)
        viewModel.setReciting(true)
        assertTrue(viewModel.isRevealed(1))
    }
}
