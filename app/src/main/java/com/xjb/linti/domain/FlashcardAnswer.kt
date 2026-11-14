package com.xjb.linti.domain

import java.text.Normalizer
import java.util.Locale

sealed interface FlashcardAnswer {
    val explanation: String?

    data class Resolved(
        val highlightedLabels: Set<String> = emptySet(),
        val displayAnswer: String? = null,
        override val explanation: String? = null,
    ) : FlashcardAnswer

    data class Unresolvable(
        val raw: String,
        override val explanation: String? = null,
    ) : FlashcardAnswer
}

fun resolveFlashcardAnswer(question: Question): FlashcardAnswer {
    val explanation = question.explanation.trim().takeIf { it.isNotEmpty() }
    return when (question.type) {
        QuestionType.SINGLE, QuestionType.MULTI -> resolveChoice(question, explanation)
        QuestionType.JUDGE -> resolveJudge(question, explanation)
        QuestionType.FILL, QuestionType.ESSAY -> resolveFill(question, explanation)
    }
}

private fun resolveChoice(
    question: Question,
    explanation: String?,
): FlashcardAnswer {
    val answer = Normalizer.normalize(question.answer, Normalizer.Form.NFKC)
        .uppercase(Locale.ROOT)
    if (answer.isEmpty() || answer.any { it !in 'A'..'H' && !it.isWhitespace() && it !in CHOICE_SEPARATORS }) {
        return FlashcardAnswer.Unresolvable(question.answer, explanation)
    }

    val letters = AnswerNormalizer.normalizeLetters(answer)
        .map { it.toString() }
        .toSet()
    if (letters.isEmpty() || (question.type == QuestionType.SINGLE && letters.size != 1)) {
        return FlashcardAnswer.Unresolvable(question.answer, explanation)
    }

    val optionLabels = question.options.map { normalizeLabel(it.label) }.toSet()
    if (!optionLabels.containsAll(letters)) {
        return FlashcardAnswer.Unresolvable(question.answer, explanation)
    }
    return FlashcardAnswer.Resolved(
        highlightedLabels = letters,
        explanation = explanation,
    )
}

private fun resolveJudge(
    question: Question,
    explanation: String?,
): FlashcardAnswer = when (strictJudge(question.answer)) {
    "true" -> FlashcardAnswer.Resolved(displayAnswer = "正确", explanation = explanation)
    "false" -> FlashcardAnswer.Resolved(displayAnswer = "错误", explanation = explanation)
    else -> FlashcardAnswer.Unresolvable(question.answer, explanation)
}

private fun resolveFill(
    question: Question,
    explanation: String?,
): FlashcardAnswer {
    val answer = question.answer.trim()
    return if (answer.isEmpty()) {
        FlashcardAnswer.Unresolvable(question.answer, explanation)
    } else {
        FlashcardAnswer.Resolved(displayAnswer = answer, explanation = explanation)
    }
}

private fun normalizeLabel(label: String): String =
    Normalizer.normalize(label, Normalizer.Form.NFKC)
        .trim()
        .uppercase(Locale.ROOT)

// 只接受完整的判断词，不从「正确答案见…」这类句子里猜。
private fun strictJudge(raw: String): String? {
    val token = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        .trim()
        .lowercase(Locale.ROOT)
        .trim('。', '.', '！', '!', '；', ';', ' ')
    return if (token in JUDGE_TOKENS) AnswerNormalizer.normalizeJudge(token) else null
}

private val JUDGE_TOKENS = setOf(
    "错误", "错", "×", "x", "f", "false", "否", "不正确",
    "正确", "对", "√", "t", "true", "是",
)

private const val CHOICE_SEPARATORS = ",;、/|+.()[]{}"
