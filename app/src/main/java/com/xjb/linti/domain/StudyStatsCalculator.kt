package com.xjb.linti.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AttemptStatRecord(
    val questionId: Long,
    val bankId: Long,
    val answeredAt: Long,
    val isCorrect: Boolean,
    val sessionId: Long?,
    val answerViewed: Boolean = false,
)

data class LegacySessionRecord(
    val id: Long,
    val bankId: Long?,
    val answeredCount: Int,
    val correctCount: Int,
    val at: Long,
    val answeredQuestionIds: Set<Long>,
    val status: String,
)

data class StudyAggregate(
    val totalAnswered: Int,
    val todayAnswered: Int,
    val totalCorrect: Int,
    val streakDays: Int,
    val sessionCount: Int,
) {
    val accuracy: Double
        get() = if (totalAnswered == 0) 0.0 else totalCorrect.toDouble() / totalAnswered
}

object StudyStatsCalculator {
    private const val COMPLETED = "COMPLETED"

    fun aggregate(
        attempts: List<AttemptStatRecord>,
        sessions: List<LegacySessionRecord>,
        todayStart: Long,
        zone: ZoneId,
    ): StudyAggregate {
        val backfilledIds = attempts.mapNotNull { it.sessionId }.toSet()
        val legacySessions = sessions.filter {
            it.status == COMPLETED && it.id !in backfilledIds
        }
        var totalAnswered = 0
        var todayAnswered = 0
        var totalCorrect = 0
        val days = mutableSetOf<LocalDate>()
        attempts.forEach { attempt ->
            totalAnswered++
            if (attempt.isCorrect && !attempt.answerViewed) totalCorrect++
            if (attempt.answeredAt >= todayStart) todayAnswered++
            days += Instant.ofEpochMilli(attempt.answeredAt).atZone(zone).toLocalDate()
        }
        legacySessions.forEach { session ->
            totalAnswered += session.answeredCount
            totalCorrect += session.correctCount
            if (session.at >= todayStart) todayAnswered += session.answeredCount
            days += Instant.ofEpochMilli(session.at).atZone(zone).toLocalDate()
        }
        var streak = 0
        var expected = LocalDate.now(zone)
        for (day in days.sortedDescending()) {
            if (day == expected) {
                streak++
                expected = expected.minusDays(1)
            } else if (day < expected) {
                break
            }
        }
        return StudyAggregate(
            totalAnswered = totalAnswered,
            todayAnswered = todayAnswered,
            totalCorrect = totalCorrect,
            streakDays = streak,
            sessionCount = sessions.count { it.status == COMPLETED },
        )
    }

    fun answeredQuestionIds(
        attempts: List<AttemptStatRecord>,
        sessions: List<LegacySessionRecord>,
        bankId: Long,
    ): Set<Long> {
        val backfilledIds = attempts.mapNotNull { it.sessionId }.toSet()
        val result = attempts
            .asSequence()
            .filter { it.bankId == bankId }
            .map { it.questionId }
            .toMutableSet()
        sessions
            .asSequence()
            .filter { it.status == COMPLETED && it.bankId == bankId && it.id !in backfilledIds }
            .flatMap { it.answeredQuestionIds.asSequence() }
            .forEach { result += it }
        return result
    }
}
