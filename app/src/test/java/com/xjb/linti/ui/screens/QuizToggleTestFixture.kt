package com.xjb.linti.ui.screens

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.db.QuestionBankEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.repo.ExamRecord
import com.xjb.linti.data.repo.SessionSnapshot
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.data.db.ExamDraftSnapshot
import com.xjb.linti.domain.Grader
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizClock
import com.xjb.linti.domain.QuizQuestion
import com.xjb.linti.ui.LintiViewModel

internal class QuizToggleTestFixture {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    val db: LintiDatabase = Room.inMemoryDatabaseBuilder(app, LintiDatabase::class.java).allowMainThreadQueries().build()
    val container = AppContainer(app, db)
    lateinit var viewModel: LintiViewModel

    suspend fun setup() {
        db.questionBankDao().insert(QuestionBankEntity(id = 1, name = "虚构题库", createdAt = 1, updatedAt = 1))
        (1L..3L).forEach { id ->
            db.questionDao().insert(QuestionEntity(
                id = id, bankId = 1, sourceNumber = "$id", type = "MULTI", stem = "虚构题干 $id",
                optionsJson = """[{"label":"A","text":"虚构选项甲"},{"label":"B","text":"虚构选项乙"},{"label":"C","text":"虚构选项丙"}]""",
                answer = "AC", explanation = "虚构解析 $id", sourceFileName = "虚构来源", createdAt = id, updatedAt = id,
            ))
        }
        container.quizPreferences.setAutoCheckUpdates(false)
        container.quizPreferences.setAutoNextOnCorrect(false)
        viewModel = LintiViewModel(app, container, QuizClock { 1_000_000L })
    }

    suspend fun startPractice(mode: PracticeMode = PracticeMode.SEQUENCE) {
        val result = if (mode == PracticeMode.SEQUENCE) viewModel.startSequenceQuiz(1)
        else viewModel.startSingleQuestionQuiz(1, mode)
        check(result.started) { result.message.orEmpty() }
    }

    suspend fun startExam(duration: Int = 30) {
        val result = viewModel.startExam(1, ExamConfig(multiCount = 1, durationMinutes = duration, passScore = 0.0))
        check(result.started) { result.message.orEmpty() }
    }

    suspend fun createReview(): Long {
        val question = QuizQuestion(
            questionId = 1, bankId = 1, sourceNumber = "1", type = QuestionType.MULTI,
            stem = "虚构原卷题干", options = listOf(QuestionOption("A", "虚构选项甲"), QuestionOption("B", "虚构选项乙")),
            correctAnswer = "A", explanation = "虚构原卷解析",
        )
        val questions = listOf(question)
        val config = ExamConfig(multiCount = 1, passScore = 0.0)
        val answers = mapOf(1L to "B")
        container.repository.createExamDraft(ExamDraftSnapshot(
            "虚构原卷草稿", 1, "虚构题库", config, questions, answers, emptySet(), emptySet(), emptySet(), 0, 100, 0,
        ))
        return container.repository.submitExamSession(
            SessionSnapshot(1, "虚构题库", PracticeMode.EXAM, config, questions, answers,
                emptySet(), emptySet(), 100, 200, Grader.grade(questions, answers, config)),
            listOf(ExamRecord(1, 1, "B", false)), "虚构原卷草稿",
        )
    }

    fun close() { db.close() }
}
