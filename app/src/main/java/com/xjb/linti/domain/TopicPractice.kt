package com.xjb.linti.domain

import kotlin.random.Random

data class TopicCountOption(
    val count: Int,
    val isAll: Boolean,
    val enabled: Boolean,
)

sealed interface TopicDrawResult {
    data class Success(val questions: List<Question>) : TopicDrawResult
    data class Failure(val message: String) : TopicDrawResult
}

object TopicPractice {
    val fixedCounts = listOf(10, 20, 50)

    fun eligibleQuestions(
        allQuestions: List<Question>,
        bankIds: Set<Long>,
        types: Set<QuestionType>,
    ): List<Question> {
        if (bankIds.isEmpty() || types.isEmpty()) return emptyList()
        return allQuestions.filter { question ->
            question.bankId in bankIds &&
                question.type in types &&
                ExamEligibility.isEligible(question)
        }
    }

    fun countsByType(
        allQuestions: List<Question>,
        bankIds: Set<Long>,
    ): Map<QuestionType, Int> = QuestionType.entries.associateWith { type ->
        eligibleQuestions(allQuestions, bankIds, setOf(type)).size
    }

    fun countOptions(availableCount: Int): List<TopicCountOption> {
        require(availableCount >= 0) { "可练题数不能为负" }
        val fixed = fixedCounts.map { count ->
            TopicCountOption(
                count = count,
                isAll = false,
                enabled = availableCount >= count,
            )
        }
        val all = if (
            availableCount in 1 until 50 &&
            availableCount !in fixedCounts
        ) {
            listOf(
                TopicCountOption(
                    count = availableCount,
                    isAll = true,
                    enabled = true,
                ),
            )
        } else {
            emptyList()
        }
        return fixed + all
    }

    fun defaultCount(availableCount: Int): Int? =
        countOptions(availableCount)
            .filter { it.enabled && it.count <= 20 }
            .maxOfOrNull { it.count }

    fun drawQuestions(
        candidates: List<Question>,
        count: Int,
        random: Random = Random.Default,
    ): TopicDrawResult {
        if (count <= 0) return TopicDrawResult.Failure("练习题量必须大于 0")
        if (count > candidates.size) {
            return TopicDrawResult.Failure("练习题量不能超过可练题数")
        }
        val questions = candidates
            .shuffled(random)
            .take(count)
            .shuffled(random)
        return TopicDrawResult.Success(questions)
    }
}
