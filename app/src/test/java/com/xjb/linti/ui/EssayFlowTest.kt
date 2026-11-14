package com.xjb.linti.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.db.*
import com.xjb.linti.domain.*
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class EssayFlowTest {
    private lateinit var db: LintiDatabase
    private lateinit var container: AppContainer
    private lateinit var vm: LintiViewModel
    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(app, LintiDatabase::class.java).allowMainThreadQueries().build()
        db.questionBankDao().insert(QuestionBankEntity(1, "虚构题库", 1, 1))
        db.questionDao().insert(QuestionEntity(id = 1, bankId = 1, sourceNumber = "1", type = "ESSAY", stem = "虚构问题", optionsJson = "[]", answer = "虚构要点", explanation = "解析", sourceFileName = "虚构", createdAt = 1, updatedAt = 1))
        container = AppContainer(app, db)
        container.quizPreferences.setAutoNextOnCorrect(false)
        vm = LintiViewModel(app, container, QuizClock { 100_000L })
    }
    @After fun close() { vm.leaveQuiz(); db.close(); Dispatchers.resetMain() }
    private suspend fun awaitAttempts(count: Int) = withTimeout(5000) { while (db.questionAttemptDao().getAll().size < count) delay(10) }

    @Test fun practiceSelfAssessmentCreatesWrongAndAdvancesMasteryAndFlashcard() = runBlocking {
        assertTrue(vm.startSequenceQuiz(1).started)
        vm.assessEssay(1, true)
        assertTrue(vm.quizState.value.answers.isEmpty())
        vm.revealEssay(1); vm.assessEssay(1, false); awaitAttempts(1)
        assertFalse(db.questionAttemptDao().getAll().single().isCorrect)
        assertTrue(db.wrongItemDao().getByQuestion(1)!!.pendingReview)
        assertEquals(1, db.flashcardReviewDao().get(1)!!.reviewCount)
        vm.leaveQuiz()
        assertTrue(vm.startSingleQuestionQuiz(1, PracticeMode.WRONG).started)
        vm.revealEssay(1); vm.assessEssay(1, true); awaitAttempts(2)
        assertEquals(1, db.wrongItemDao().getByQuestion(1)!!.correctStreak)
        vm.setReciting(true)
        assertTrue(vm.quizState.value.reciting)
        val question = container.repository.getQuestion(1)!!
        assertEquals("虚构要点", (resolveFlashcardAnswer(question) as FlashcardAnswer.Resolved).displayAnswer)
        assertEquals(2, db.flashcardReviewDao().get(1)!!.reviewCount)
    }

    @Test fun examSelfAssessmentIsStoredAndGraded() = runBlocking {
        assertTrue(vm.startExam(1, ExamConfig(essayCount = 1, passScore = 0.0, durationMinutes = 10)).started)
        vm.setFillAnswer(1, "[自评:答对]虚构作答")
        assertEquals(0, vm.quizState.value.answeredCount)
        assertEquals(QuestionOutcome.UNANSWERED, Grader.gradeQuestion(vm.quizState.value.questions.single(), vm.quizState.value.answers[1], MultiSelectRule.LESS_NO_SCORE).outcome)
        vm.setFillAnswer(1, "虚构作答")
        vm.revealEssay(1); vm.assessEssay(1, true)
        assertEquals(1, vm.quizState.value.answeredCount)
        assertEquals(QuestionOutcome.CORRECT, Grader.gradeQuestion(vm.quizState.value.questions.single(), vm.quizState.value.answers[1], MultiSelectRule.LESS_NO_SCORE).outcome)
        vm.submitQuiz(vm.quizState.value.sessionToken)
        awaitAttempts(1)
        val attempt = db.questionAttemptDao().getAll().single()
        assertTrue(attempt.isCorrect); assertEquals("虚构作答", EssayAnswer.text(attempt.answer))
        assertEquals(1, db.flashcardReviewDao().get(1)!!.reviewCount)
    }
}
