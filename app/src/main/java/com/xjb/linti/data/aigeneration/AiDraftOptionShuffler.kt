package com.xjb.linti.data.aigeneration

import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.QuestionType
import java.text.Normalizer
import kotlin.random.Random

internal object AiDraftOptionShuffler {
    // 汉字可以紧邻选项字母；拉丁字母、数字和下划线相邻时则视为单词或标识符。
    private val standaloneLetter = Regex("(?<![A-Za-z0-9_])[A-Ha-h](?![A-Za-z0-9_])")
    private val compactAnswerAfterMarker = Regex(
        "(?:正确(?:选项|答案)|答案|选择|选项|选)\\s*(?:为|是|选)?\\s*[:：]?\\s*[A-Ha-h]{2,8}(?![A-Za-z0-9_])"
    )
    private val compactAnswerBeforeMarker = Regex(
        "(?<![A-Za-z0-9_])[A-Ha-h]{2,8}(?![A-Za-z0-9_])\\s*(?:两项|[均都皆]|正确|错误|选项)"
    )

    fun shuffleIfSafe(draft: AiDraftFields, random: Random): AiDraftFields {
        if (draft.type !in listOf(QuestionType.SINGLE.name, QuestionType.MULTI.name) ||
            AiDraftValidator.validate(draft).isNotEmpty() || hasOptionReference(draft.explanation)) return draft

        val oldAnswers = AnswerNormalizer.normalizeLetters(draft.answer).toSet()
        val shuffled = draft.options.shuffled(random)
        val answer = shuffled.mapIndexedNotNull { index, option ->
            if (option.label.trim().uppercase().single() in oldAnswers) ('A' + index) else null
        }.joinToString("")
        val options = shuffled.mapIndexed { index, option -> option.copy(label = ('A' + index).toString()) }
        return draft.copy(options = options, answer = answer)
    }

    private fun hasOptionReference(explanation: String): Boolean {
        val normalized = Normalizer.normalize(explanation, Normalizer.Form.NFKC)
        return standaloneLetter.containsMatchIn(normalized) || compactAnswerAfterMarker.containsMatchIn(normalized) ||
            compactAnswerBeforeMarker.containsMatchIn(normalized)
    }
}
