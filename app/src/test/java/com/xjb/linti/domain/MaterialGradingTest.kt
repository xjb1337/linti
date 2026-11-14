package com.xjb.linti.domain

import org.junit.Assert.*
import org.junit.Test

class MaterialGradingTest {
    private fun quiz(type: QuestionType, answer: String, alternatives: List<List<String>> = emptyList()) = QuizQuestion(1, 1, "", type, "虚构题", emptyList(), answer, "解析", acceptedAnswers = alternatives)
    @Test fun fillAlternativesAndLegacy() {
        val fill = quiz(QuestionType.FILL, "甲；乙", listOf(listOf("甲", "甲同义"), listOf("乙", "乙同义")))
        assertEquals(QuestionOutcome.CORRECT, Grader.gradeQuestion(fill, "甲同义；乙", MultiSelectRule.LESS_NO_SCORE).outcome)
        assertEquals(QuestionOutcome.WRONG, Grader.gradeQuestion(fill, "甲同义；丙", MultiSelectRule.LESS_NO_SCORE).outcome)
        val old = quiz(QuestionType.FILL, "旧答案")
        assertEquals(QuestionOutcome.CORRECT, Grader.gradeQuestion(old, "旧答案", MultiSelectRule.LESS_NO_SCORE).outcome)
    }
    @Test fun essayRequiresSelfAssessmentAndIsEligibleForExamAndFlashcards() {
        val essay = quiz(QuestionType.ESSAY, "虚构要点")
        assertEquals(QuestionOutcome.UNANSWERED, Grader.gradeQuestion(essay, "用户文字", MultiSelectRule.LESS_NO_SCORE).outcome)
        assertEquals(QuestionOutcome.CORRECT, Grader.gradeQuestion(essay, EssayAnswer.encode("", true), MultiSelectRule.LESS_NO_SCORE).outcome)
        assertEquals(QuestionOutcome.WRONG, Grader.gradeQuestion(essay, EssayAnswer.encode("虚构", false), MultiSelectRule.LESS_NO_SCORE).outcome)
        val question = Question(id = 1, type = QuestionType.ESSAY, stem = "虚构问题", answer = "虚构要点")
        assertTrue(ExamEligibility.isEligible(question))
        assertNull(ExamBuilder().build(ExamConfig(essayCount = 1, passScore = 0.0), listOf(question)).error)
        assertTrue(resolveFlashcardAnswer(question) is FlashcardAnswer.Resolved)
    }
}
