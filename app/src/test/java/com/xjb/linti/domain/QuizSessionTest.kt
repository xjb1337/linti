package com.xjb.linti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizSessionTest {

    private fun practiceConfig(): ExamConfig = ExamConfig(passScore = 0.0)
    private fun examConfig(showAnswer: Boolean = false): ExamConfig =
        ExamConfig(showAnswerAfterAnswer = showAnswer)

    private fun singleQuestion(): QuizQuestion = QuizQuestion(
        questionId = 1L,
        bankId = 1L,
        sourceNumber = "",
        type = QuestionType.SINGLE,
        stem = "",
        options = listOf(
            QuestionOption("A", "甲"),
            QuestionOption("B", "乙"),
            QuestionOption("C", "丙"),
            QuestionOption("D", "丁"),
        ),
        correctAnswer = "C",
        explanation = "",
    )

    private fun multiQuestion(): QuizQuestion = singleQuestion().copy(
        type = QuestionType.MULTI,
        correctAnswer = "ACD",
    )

    private fun judgeQuestion(): QuizQuestion = singleQuestion().copy(
        type = QuestionType.JUDGE,
        correctAnswer = "true",
    )

    private fun fillQuestion(): QuizQuestion = singleQuestion().copy(
        type = QuestionType.FILL,
        correctAnswer = "答案",
    )

    @Test
    fun `single choice commits on first selection and selects no longer change state`() {
        val question = singleQuestion()
        val state = AnswerState()
        val after = QuizSession.select(
            state, question.questionId, question.type, "C",
            PracticeMode.SEQUENCE, practiceConfig(),
        )

        assertTrue(question.questionId in after.committedIds)
        assertEquals("C", after.answers[question.questionId])
        assertTrue(QuizSession.isRevealed(after, question.questionId, PracticeMode.SEQUENCE, practiceConfig()))

        val second = QuizSession.select(
            after, question.questionId, question.type, "B",
            PracticeMode.SEQUENCE, practiceConfig(),
        )
        assertEquals(after, second)
    }

    @Test
    fun `judge commits on first selection and records outcome`() {
        val question = judgeQuestion()
        val after = QuizSession.select(
            AnswerState(), question.questionId, question.type, "正确",
            PracticeMode.WRONG, practiceConfig(),
        )
        assertTrue(question.questionId in after.committedIds)
        val grade = Grader.gradeQuestion(question, after.answers[question.questionId], MultiSelectRule.LESS_NO_SCORE)
        assertEquals(QuestionOutcome.CORRECT, grade.outcome)
    }

    @Test
    fun `multi toggles without revealing and empty selection cannot commit`() {
        val question = multiQuestion()
        val config = practiceConfig()
        var state = AnswerState()
        state = QuizSession.select(state, question.questionId, question.type, "A", PracticeMode.SEQUENCE, config)
        assertFalse(QuizSession.isRevealed(state, question.questionId, PracticeMode.SEQUENCE, config))
        assertTrue(QuizSession.needsConfirm(question.type, PracticeMode.SEQUENCE, config))
        state = QuizSession.select(state, question.questionId, question.type, "C", PracticeMode.SEQUENCE, config)
        state = QuizSession.select(state, question.questionId, question.type, "A", PracticeMode.SEQUENCE, config)
        assertEquals("C", state.answers[question.questionId])
        assertFalse(QuizSession.isRevealed(state, question.questionId, PracticeMode.SEQUENCE, config))

        val empty = AnswerState(answers = mapOf(question.questionId to ""))
        assertFalse(QuizSession.canCommit(empty, question.questionId, PracticeMode.SEQUENCE, config))
        assertNull(QuizSession.commit(empty, question.questionId, PracticeMode.SEQUENCE, config))
    }

    @Test
    fun `multi locks after confirm and partial selection is not correct`() {
        val question = multiQuestion()
        val config = practiceConfig()
        var state = AnswerState()
        state = QuizSession.select(state, question.questionId, question.type, "A", PracticeMode.SEQUENCE, config)
        state = QuizSession.select(state, question.questionId, question.type, "C", PracticeMode.SEQUENCE, config)
        val committed = QuizSession.commit(state, question.questionId, PracticeMode.SEQUENCE, config)
        assertTrue(committed != null)
        assertTrue(QuizSession.isRevealed(committed!!, question.questionId, PracticeMode.SEQUENCE, config))
        val same = QuizSession.select(
            committed, question.questionId, question.type, "D",
            PracticeMode.SEQUENCE, config,
        )
        assertEquals(committed, same)

        val grade = Grader.gradeQuestion(question, "AC", MultiSelectRule.LESS_NO_SCORE)
        assertFalse(grade.outcome == QuestionOutcome.CORRECT)
    }

    @Test
    fun `fill blank cannot commit and cannot change after commit`() {
        val question = fillQuestion()
        val config = practiceConfig()
        val blank = QuizSession.setText(AnswerState(), question.questionId, "  ", PracticeMode.SEQUENCE, config)
        assertFalse(QuizSession.canCommit(blank, question.questionId, PracticeMode.SEQUENCE, config))
        assertNull(QuizSession.commit(blank, question.questionId, PracticeMode.SEQUENCE, config))

        var state = QuizSession.setText(AnswerState(), question.questionId, "答案", PracticeMode.SEQUENCE, config)
        val committed = QuizSession.commit(state, question.questionId, PracticeMode.SEQUENCE, config)
        assertTrue(committed != null)
        val edited = QuizSession.setText(
            committed!!, question.questionId, "改答案",
            PracticeMode.SEQUENCE, config,
        )
        assertEquals(committed, edited)
    }

    @Test
    fun `exam default allows repeated changes and never reveals or needs confirm`() {
        val question = singleQuestion()
        val config = examConfig(showAnswer = false)
        var state = AnswerState()
        state = QuizSession.select(state, question.questionId, question.type, "A", PracticeMode.EXAM, config)
        state = QuizSession.select(state, question.questionId, question.type, "B", PracticeMode.EXAM, config)
        state = QuizSession.select(state, question.questionId, question.type, "C", PracticeMode.EXAM, config)
        assertEquals("C", state.answers[question.questionId])
        assertTrue(state.committedIds.isEmpty())
        assertFalse(QuizSession.isRevealed(state, question.questionId, PracticeMode.EXAM, config))
        assertFalse(QuizSession.needsConfirm(question.type, PracticeMode.EXAM, config))
        assertNull(QuizSession.commit(state, question.questionId, PracticeMode.EXAM, config))
    }

    @Test
    fun `exam show after answer locks like practice`() {
        val question = singleQuestion()
        val config = examConfig(showAnswer = true)
        val state = QuizSession.select(
            AnswerState(), question.questionId, question.type, "C",
            PracticeMode.EXAM, config,
        )
        assertTrue(QuizSession.isRevealed(state, question.questionId, PracticeMode.EXAM, config))
        val same = QuizSession.select(
            state, question.questionId, question.type, "B",
            PracticeMode.EXAM, config,
        )
        assertEquals(state, same)
    }

    @Test
    fun `retry clears answer and committed and allows committing again`() {
        val question = singleQuestion()
        val config = practiceConfig()
        val committed = QuizSession.select(
            AnswerState(), question.questionId, question.type, "C",
            PracticeMode.SEQUENCE, config,
        )
        val retried = QuizSession.retry(committed, question.questionId)
        assertFalse(retried.answers.containsKey(question.questionId))
        assertFalse(question.questionId in retried.committedIds)
        assertFalse(QuizSession.isRevealed(retried, question.questionId, PracticeMode.SEQUENCE, config))
        val again = QuizSession.select(
            retried, question.questionId, question.type, "B",
            PracticeMode.SEQUENCE, config,
        )
        assertTrue(question.questionId in again.committedIds)
        assertEquals("B", again.answers[question.questionId])
    }

    @Test
    fun `shuffled exam answer maps back to original labels and grades correctly`() {
        val original = singleQuestion()
        val labelMap = mapOf("A" to "C", "B" to "A", "C" to "D", "D" to "B")
        val displayQuestion = original.copy(
            options = listOf(
                QuestionOption("A", "丙"),
                QuestionOption("B", "甲"),
                QuestionOption("C", "丁"),
                QuestionOption("D", "乙"),
            ),
            correctAnswer = "A",
            labelMap = labelMap,
        )

        val displayAnswer = "A"
        val storedAnswer = QuizSession.mapAnswerToOriginal(displayAnswer, labelMap)
        assertEquals("C", storedAnswer)
        val grade = Grader.gradeQuestion(original, storedAnswer, MultiSelectRule.LESS_NO_SCORE)
        assertEquals(QuestionOutcome.CORRECT, grade.outcome)

        val identity = QuizSession.mapAnswerToOriginal("B", emptyMap())
        assertEquals("B", identity)
        assertEquals("A", displayQuestion.correctAnswer)
    }
}
