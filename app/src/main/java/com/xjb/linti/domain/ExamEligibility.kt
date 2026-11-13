package com.xjb.linti.domain

import java.text.Normalizer
import java.util.Locale

object ExamEligibility {
    fun isEligible(question: Question): Boolean {
        if (question.needsReview || question.stem.isBlank()) return false
        return when (question.type) {
            QuestionType.SINGLE, QuestionType.MULTI -> validChoiceAnswer(question)
            QuestionType.JUDGE -> validJudgeAnswer(question.answer)
            QuestionType.FILL -> AnswerNormalizer.normalizeFillForComparison(question.answer).isNotBlank()
        }
    }

    fun availableCounts(questions: List<Question>): Map<QuestionType, Int> {
        val eligible = questions.filter(::isEligible)
        return QuestionType.entries.associateWith { type -> eligible.count { it.type == type } }
    }

    private fun validChoiceAnswer(question: Question): Boolean {
        val labels = question.options.map { it.label.uppercase(Locale.ROOT) }
        if (labels.size < 2 || labels.distinct().size != labels.size ||
            labels.any { it.length != 1 || it[0] !in 'A'..'H' } ||
            question.options.any { it.text.isBlank() }
        ) return false

        val answer = Normalizer.normalize(question.answer, Normalizer.Form.NFKC)
            .uppercase(Locale.ROOT)
        if (answer.any { it !in 'A'..'H' && !it.isWhitespace() && it !in ",;、/|+.()[]{}" }) {
            return false
        }
        val letters = answer.filter { it in 'A'..'H' }
        if (letters.isEmpty() || (question.type == QuestionType.SINGLE && letters.toSet().size != 1)) {
            return false
        }
        return letters.all { it.toString() in labels }
    }

    private fun validJudgeAnswer(answer: String): Boolean {
        val token = Normalizer.normalize(answer, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)
            .trim('。', '.', '！', '!', '；', ';', ' ')
        return token in setOf(
            "错误", "错", "×", "x", "f", "false", "否", "不正确",
            "正确", "对", "√", "t", "true", "是",
        ) && AnswerNormalizer.normalizeJudge(token) != null
    }
}
