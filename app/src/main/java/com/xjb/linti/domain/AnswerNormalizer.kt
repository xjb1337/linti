package com.xjb.linti.domain

import java.text.Normalizer
import java.util.Locale

object AnswerNormalizer {
    private val answerLetters = 'A'..'H'

    fun normalizeLetters(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).uppercase(Locale.ROOT)
        return normalized
            .filter { it in answerLetters }
            .toList()
            .distinct()
            .sorted()
            .joinToString("")
    }

    fun normalizeJudge(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .trim()
            .lowercase(Locale.ROOT)
            .trim('。', '.', '！', '!', '；', ';', ' ')
        return when {
            value in setOf("错误", "错", "×", "x", "f", "false", "否", "不正确") -> "false"
            value in setOf("正确", "对", "√", "t", "true", "是") -> "true"
            value.contains("不正确") || value.contains("错误") || value == "错" -> "false"
            value.contains("正确") || value == "对" -> "true"
            else -> null
        }
    }

    fun normalizeFillForStorage(raw: String?): String =
        Normalizer.normalize(raw.orEmpty(), Normalizer.Form.NFKC).trim()

    fun normalizeFillForComparison(raw: String?): String =
        normalizeFillForStorage(raw)
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()

    fun normalizeForStorage(type: QuestionType, raw: String?): String = when (type) {
        QuestionType.SINGLE, QuestionType.MULTI -> normalizeLetters(raw)
        QuestionType.JUDGE -> normalizeJudge(raw).orEmpty()
        QuestionType.FILL -> normalizeFillForStorage(raw)
    }

    fun displayAnswer(type: QuestionType, raw: String): String = when (type) {
        QuestionType.JUDGE -> when (normalizeJudge(raw)) {
            "true" -> "正确"
            "false" -> "错误"
            else -> raw
        }
        else -> raw
    }

    fun lettersIn(raw: String?): Set<String> =
        normalizeLetters(raw).map { it.toString() }.toSet()

    fun judgeMatches(userAnswer: String?, correctAnswer: String): Boolean {
        val user = normalizeJudge(userAnswer) ?: return false
        val correct = normalizeJudge(correctAnswer) ?: return false
        return user == correct
    }

    fun fillMatches(userAnswer: String?, correctAnswer: String): Boolean =
        normalizeFillForComparison(userAnswer) == normalizeFillForComparison(correctAnswer)
}
