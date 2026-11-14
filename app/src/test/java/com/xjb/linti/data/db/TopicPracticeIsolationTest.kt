package com.xjb.linti.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.PracticeCommit
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class TopicPracticeIsolationTest {
    private lateinit var database: LintiDatabase
    private lateinit var repository: LintiRepository
    private var bankId: Long = 0
    private var firstQuestionId: Long = 0
    private var secondQuestionId: Long = 0

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            database = Room.inMemoryDatabaseBuilder(context, LintiDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            repository = LintiRepository(database)
            bankId = repository.createBank("虚构题库", now = 100)
            firstQuestionId = repository.saveQuestion(question("1", "虚构题一", "A"))
            secondQuestionId = repository.saveQuestion(question("2", "虚构题二", "B"))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `topic attempts record stats and wrong items without touching sequence progress`() = runBlocking {
        repository.saveSequenceProgress(
            bankId = bankId,
            currentIndex = 4,
            answeredCount = 3,
            now = 200,
        )
        val before = repository.getSequenceProgress(bankId)

        repository.commitPracticeAnswer(commit(firstQuestionId, isCorrect = false, answeredAt = 300))
        repository.commitPracticeAnswer(commit(firstQuestionId, isCorrect = true, answeredAt = 400))
        repository.commitPracticeAnswer(commit(secondQuestionId, isCorrect = true, answeredAt = 500))

        val stats = repository.statsFor(listOf(firstQuestionId, secondQuestionId))
        assertEquals(2, stats.getValue(firstQuestionId).totalCount)
        assertEquals(1, stats.getValue(firstQuestionId).correctCount)
        assertEquals(1, stats.getValue(secondQuestionId).totalCount)
        assertEquals(1, stats.getValue(secondQuestionId).correctCount)
        assertEquals(3, repository.getAttemptStatRecords().size)

        val wrong = repository.getWrongRows().single().wrongItem
        assertEquals(firstQuestionId, wrong.questionId)
        assertEquals(1, wrong.wrongCount)
        assertTrue(wrong.pendingReview)
        assertFalse(wrong.corrected)

        assertEquals(before, repository.getSequenceProgress(bankId))
        assertEquals(listOf(before), database.practiceProgressDao().getAll())
        assertEquals(1, database.practiceProgressDao().getAll().size)
        assertNull(repository.getExamDraft(bankId))
    }

    @Test
    fun `exam history query excludes completed topic sessions`() = runBlocking {
        database.quizSessionDao().insert(completedSession("专项场次", PracticeMode.TOPIC.name))
        database.quizSessionDao().insert(completedSession("模考场次", PracticeMode.EXAM.name))

        val history = repository.getExamHistory()

        assertEquals(1, history.size)
        assertEquals("模考场次", history.single().bankName)
        assertTrue(history.none { it.bankName == "专项场次" })
    }

    private fun commit(
        questionId: Long,
        isCorrect: Boolean,
        answeredAt: Long,
    ): PracticeCommit = PracticeCommit(
        bankId = bankId,
        mode = PracticeMode.TOPIC,
        questionId = questionId,
        questionBankId = bankId,
        answer = if (isCorrect) {
            repositoryAnswer(questionId)
        } else {
            if (questionId == firstQuestionId) "B" else "A"
        },
        isCorrect = isCorrect,
        currentIndex = 9,
        answeredAt = answeredAt,
        roundStartedAt = 777,
    )

    private fun repositoryAnswer(questionId: Long): String =
        if (questionId == firstQuestionId) "A" else "B"

    private fun question(sourceNumber: String, stem: String, answer: String): Question = Question(
        bankId = bankId,
        sourceNumber = sourceNumber,
        type = QuestionType.SINGLE,
        stem = stem,
        options = listOf(
            QuestionOption("A", "虚构选项甲"),
            QuestionOption("B", "虚构选项乙"),
        ),
        answer = answer,
        sourceFileName = "虚构来源",
        createdAt = 100,
        updatedAt = 100,
    )

    private fun completedSession(name: String, mode: String): QuizSessionEntity =
        QuizSessionEntity(
            bankId = bankId,
            bankName = name,
            mode = mode,
            configJson = "{}",
            questionIdsJson = "[]",
            answersJson = "{}",
            flagsJson = "[]",
            bookmarksJson = "[]",
            startedAt = 1_000,
            endedAt = 2_000,
            status = SessionBackfill.COMPLETED,
        )
}
