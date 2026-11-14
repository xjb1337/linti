package com.xjb.linti.domain

import java.time.Instant
import java.time.ZoneId

data class FlashcardReview(
    val questionId: Long,
    val bankId: Long,
    val step: Int,
    val dueAt: Long,
    val firstReviewedAt: Long,
    val lastReviewedAt: Long,
    val reviewCount: Int,
    val lapseCount: Int,
)

data class FlashcardQueue(
    val questions: List<Question>,
    val nextDueAt: Long?,
    val introducedToday: Int,
)

/** 日期间隔按本地日历计算，以便跨夏令时仍保持相同的本地时间。 */
object FlashcardSchedule {
    fun rememberedDays(currentStep: Int): Int = when ((currentStep.toLong() + 1).coerceAtMost(7).toInt()) {
        1 -> 1
        2 -> 2
        3 -> 4
        4 -> 8
        5 -> 16
        6 -> 32
        else -> 60
    }

    fun review(questionId: Long, bankId: Long, previous: FlashcardReview?, remembered: Boolean, clock: QuizClock, zone: ZoneId): FlashcardReview {
        val now = clock.nowMillis()
        val nextStep = if (remembered) ((previous?.step ?: 0).toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0
        val due = if (remembered) {
            Instant.ofEpochMilli(now).atZone(zone).plusDays(rememberedDays(previous?.step ?: 0).toLong()).toInstant().toEpochMilli()
        } else now + 10 * 60_000L
        return FlashcardReview(
            questionId = questionId, bankId = bankId, step = nextStep, dueAt = due,
            firstReviewedAt = previous?.firstReviewedAt ?: now, lastReviewedAt = now,
            reviewCount = (previous?.reviewCount ?: 0) + 1,
            lapseCount = (previous?.lapseCount ?: 0) + if (remembered) 0 else 1,
        )
    }

    /** reviews 包含所有题库，以便新卡额度在题库之间共享。sessionTail 保持本次忘记的顺序。 */
    fun queue(questions: List<Question>, reviews: List<FlashcardReview>, dailyNewLimit: Int?, clock: QuizClock, zone: ZoneId, sessionTail: List<Long> = emptyList()): FlashcardQueue {
        require(dailyNewLimit == null || dailyNewLimit >= 0)
        val now = clock.nowMillis()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val introduced = reviews.count { it.firstReviewedAt >= start && it.firstReviewedAt < end }
        val byId = reviews.associateBy { it.questionId }
        val byQuestionId = questions.associateBy { it.id }
        val tailIds = sessionTail.distinct().filter { it in byQuestionId && it in byId }
        val tailSet = tailIds.toSet()
        val due = questions.filter { it.id !in tailSet && byId[it.id]?.dueAt?.let { dueAt -> dueAt < end } == true }
            .sortedBy { byId.getValue(it.id).dueAt }
        val allowance = dailyNewLimit?.let { (it - introduced).coerceAtLeast(0) } ?: Int.MAX_VALUE
        val fresh = questions.asSequence().filter { it.id !in byId }.take(allowance).toList()
        val tail = tailIds.mapNotNull(byQuestionId::get)
        val next = questions.asSequence().mapNotNull { byId[it.id]?.dueAt }.filter { it > now }.minOrNull()
        return FlashcardQueue(due + fresh + tail, next, introduced)
    }
}
