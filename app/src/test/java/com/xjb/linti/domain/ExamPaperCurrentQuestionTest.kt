package com.xjb.linti.domain

import org.junit.Assert.*
import org.junit.Test

class ExamPaperCurrentQuestionTest {
    private val current = Question(id = 1, type = QuestionType.MULTI, stem = "虚构题干",
        options = listOf(QuestionOption("A", "虚构甲"), QuestionOption("B", "虚构乙"), QuestionOption("C", "虚构丙")),
        answer = "AC", explanation = "虚构原解析")
    private val snapshot = ExamPaperQuestion(0, 1, "1", QuestionType.MULTI, "虚构题干",
        listOf(QuestionOption("A", "虚构丙"), QuestionOption("B", "虚构甲"), QuestionOption("C", "虚构乙")),
        "AB", "AB", "虚构原解析", 2.0, QuestionOutcome.CORRECT, 2.0, 2.0)

    @Test fun optionReorderingPreservesMeaning() {
        assertTrue(snapshot.matchesCurrentQuestion(current))
    }

    @Test fun changedQuestionAnswerExplanationOrOptionsDoNotMatch() {
        assertFalse(snapshot.matchesCurrentQuestion(current.copy(stem = "新题干")))
        assertFalse(snapshot.matchesCurrentQuestion(current.copy(answer = "AB")))
        assertFalse(snapshot.matchesCurrentQuestion(current.copy(explanation = "新解析")))
        assertFalse(snapshot.matchesCurrentQuestion(current.copy(options = current.options.dropLast(1))))
    }
}
