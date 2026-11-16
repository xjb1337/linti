package com.xjb.linti.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.db.QuestionBankEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class PracticeResumeTest {
    private lateinit var app: Application
    private lateinit var db: LintiDatabase
    private lateinit var container: AppContainer
    private lateinit var viewModel: LintiViewModel
    private var now = 10_000L

    @Before fun setup() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        app = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(app, LintiDatabase::class.java).allowMainThreadQueries().build()
        db.questionBankDao().insert(QuestionBankEntity(id = 1, name = "虚构题库", createdAt = 1, updatedAt = 1))
        (1L..3L).forEach { id ->
            db.questionDao().insert(QuestionEntity(
                id = id, bankId = 1, sourceNumber = "$id", type = "MULTI", stem = "虚构题$id",
                optionsJson = """[{"label":"A","text":"虚构甲"},{"label":"B","text":"虚构乙"},{"label":"C","text":"虚构丙"}]""",
                answer = "AC", explanation = "虚构解析$id", sourceFileName = "虚构来源", createdAt = id, updatedAt = id,
            ))
        }
        container = AppContainer(app, db)
        container.quizPreferences.setAutoCheckUpdates(false)
        container.quizPreferences.setAutoNextOnCorrect(false)
        container.practiceSessionStore.clear()
        viewModel = LintiViewModel(app, container, QuizClock { now })
    }

    @After fun close() {
        viewModel.viewModelScope.cancel()
        viewModel.viewModelScope.coroutineContext[Job]?.let { job -> val waiter = Thread { runBlocking { job.join() } }; waiter.start(); waiter.join(5_000) }
        db.close()
        Dispatchers.resetMain()
    }

    private fun recreatedViewModel(): LintiViewModel = LintiViewModel(app, container, QuizClock { now })

    @Test fun topicSessionSurvivesProcessRecreationWithSameQuestionsAndIndex() = runBlocking {
        assertTrue(viewModel.startTopicQuiz(setOf(1L), setOf(QuestionType.MULTI), 3).started)
        viewModel.goToQuestion(2)
        viewModel.selectAnswer(3, "A")
        viewModel.commitAnswer(3)

        val recreated = recreatedViewModel()
        val descriptor = recreated.lastPracticeSession.value
        assertEquals(PracticeMode.TOPIC, descriptor?.mode)
        assertEquals(3, descriptor?.questionIds?.size)
        assertTrue(recreated.resumeLastPractice().started)
        val state = recreated.quizState.value
        assertTrue(state.active)
        assertEquals(PracticeMode.TOPIC, state.mode)
        assertEquals(3, state.questions.size)
        assertEquals(2, state.currentIndex)
    }

    @Test fun sequenceSessionRestoresPositionAfterRecreation() = runBlocking {
        assertTrue(viewModel.startSequenceQuiz(1).started)
        viewModel.selectAnswer(1, "A")
        viewModel.commitAnswer(1)
        viewModel.goToQuestion(1)

        val recreated = recreatedViewModel()
        assertEquals(PracticeMode.SEQUENCE, recreated.lastPracticeSession.value?.mode)
        assertTrue(recreated.resumeLastPractice().started)
        assertEquals(1, recreated.quizState.value.currentIndex)
    }

    @Test fun examDoesNotCreatePracticeDescriptor() = runBlocking {
        assertTrue(viewModel.startExam(1, ExamConfig(multiCount = 3, durationMinutes = 30, passScore = 0.0)).started)
        assertNull(viewModel.lastPracticeSession.value)
        assertNull(recreatedViewModel().lastPracticeSession.value)
    }

    @Test fun dismissClearsDescriptorPersistently() = runBlocking {
        assertTrue(viewModel.startTopicQuiz(setOf(1L), setOf(QuestionType.MULTI), 2).started)
        viewModel.dismissLastPractice()
        assertNull(viewModel.lastPracticeSession.value)
        assertNull(recreatedViewModel().lastPracticeSession.value)
        assertFalse(recreatedViewModel().resumeLastPractice().started)
    }

    @Test fun resumeFailsGracefullyWhenQuestionsDeleted() = runBlocking {
        assertTrue(viewModel.startTopicQuiz(setOf(1L), setOf(QuestionType.MULTI), 3).started)
        listOf(1L, 2L, 3L).forEach { db.questionDao().deleteById(it) }
        val recreated = recreatedViewModel()
        val result = recreated.resumeLastPractice()
        assertFalse(result.started)
        assertNull(recreated.lastPracticeSession.value)
    }
}