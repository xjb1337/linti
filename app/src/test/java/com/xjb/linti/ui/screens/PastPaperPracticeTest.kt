package com.xjb.linti.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.domain.PracticeMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class PastPaperPracticeTest {
    private lateinit var fixture: QuizToggleTestFixture
    @Before fun setup() = runBlocking { fixture = QuizToggleTestFixture(); fixture.setup() }
    @After fun close() = fixture.close()
    @Test fun startsInPaperOrderAndCanSwitchRecitationWithoutSequenceProgress() = runBlocking {
        val repo = fixture.container.repository
        val paper = repo.createPastPaper(1, 2025, "虚构真题卷")
        repo.addQuestionsToPastPaper(paper, listOf(3, 1, 2))
        val result = fixture.viewModel.startPastPaperQuiz(paper)
        assertTrue(result.started)
        val state = fixture.viewModel.quizState.value
        assertEquals(PracticeMode.PAPER, state.mode)
        assertEquals(listOf(3L, 1L, 2L), state.questions.map { it.questionId })
        assertEquals("虚构真题卷", state.bankName)
        assertTrue(state.canSwitchRecite)
        fixture.viewModel.setReciting(true)
        assertTrue(fixture.viewModel.quizState.value.reciting)
        fixture.viewModel.setReciting(false)
        assertFalse(fixture.viewModel.quizState.value.reciting)
        fixture.viewModel.leaveQuiz()
        assertNull(repo.getSequenceProgress(1))
    }
}
