package com.xjb.linti.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class FlashcardScheduleTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun instant(value: String) = ZonedDateTime.parse("$value+08:00[Asia/Shanghai]").toInstant().toEpochMilli()
    private fun clock(now: Long) = QuizClock { now }
    private fun question(id: Long, bank: Long = 1) = Question(id = id, bankId = bank, stem = "虚构题$id")
    private fun reviewed(id: Long, due: Long, first: Long, step: Int = 1, count: Int = 1) =
        FlashcardReview(id, 1, step, due, first, first, count, 0)

    @Test fun rememberedIntervalsAndCeiling() {
        val now = instant("2026-06-01T12:30:00")
        val days = listOf(1, 2, 4, 8, 16, 32, 60, 60, 60)
        var review: FlashcardReview? = null
        days.forEachIndexed { index, interval ->
            review = FlashcardSchedule.review(1, 1, review, true, clock(now), zone)
            assertEquals(index + 1, review!!.step)
            assertEquals(now + interval * 86_400_000L, review!!.dueAt)
            assertEquals(index + 1, review!!.reviewCount)
            assertEquals(now, review!!.firstReviewedAt)
            assertEquals(now, review!!.lastReviewedAt)
        }
        val high = review!!.copy(step = 100)
        assertEquals(now + 60 * 86_400_000L, FlashcardSchedule.review(1, 1, high, true, clock(now), zone).dueAt)
    }

    @Test fun forgotResetsAndRetainsFirstReview() {
        val now = instant("2026-06-01T12:30:00")
        val old = reviewed(1, now, now - 1000, step = 4, count = 3).copy(lapseCount = 1)
        val result = FlashcardSchedule.review(1, 1, old, false, clock(now), zone)
        assertEquals(0, result.step)
        assertEquals(now + 600_000L, result.dueAt)
        assertEquals(4, result.reviewCount)
        assertEquals(2, result.lapseCount)
        assertEquals(now - 1000, result.firstReviewedAt)
        assertEquals(now, result.lastReviewedAt)
        assertEquals(1, FlashcardSchedule.review(1, 1, result, true, clock(now), zone).step)
    }

    @Test fun calendarDaysRespectDaylightSaving() {
        val ny = ZoneId.of("America/New_York")
        val now = Instant.parse("2026-03-07T17:00:00Z").toEpochMilli()
        val due = FlashcardSchedule.review(1, 1, null, true, clock(now), ny).dueAt
        assertEquals(Instant.parse("2026-03-08T16:00:00Z").toEpochMilli(), due)
    }

    @Test fun localDayBoundaryAndRepeatedReviewsKeepNewCardCount() {
        val before = instant("2026-06-01T23:59:00")
        val after = instant("2026-06-02T00:01:00")
        val first = FlashcardSchedule.review(1, 1, null, false, clock(before), zone)
        val twice = FlashcardSchedule.review(1, 1, first, false, clock(before + 30_000), zone)
        val questions = listOf(question(1), question(2), question(3))
        val beforeQueue = FlashcardSchedule.queue(questions, listOf(twice), 1, clock(before), zone)
        assertEquals(1, beforeQueue.introducedToday)
        assertTrue(beforeQueue.questions.isEmpty()) // 00:09 的卡不属于今天
        val afterQueue = FlashcardSchedule.queue(questions, listOf(twice), 1, clock(after), zone)
        assertEquals(0, afterQueue.introducedToday)
        assertEquals(listOf(1L, 2L), afterQueue.questions.map { it.id })
        val afterReview = FlashcardSchedule.review(1, 1, twice, true, clock(after), zone)
        assertEquals(0, FlashcardSchedule.queue(questions, listOf(afterReview), 1, clock(after), zone).introducedToday)
    }

    @Test fun lastMillisecondOfLocalDayIsIncludedButMidnightIsNot() {
        val now = instant("2026-06-01T23:59:00")
        val midnight = instant("2026-06-02T00:00:00")
        val questions = listOf(question(1), question(2))
        val reviews = listOf(reviewed(1, midnight - 1, now - 86_400_000), reviewed(2, midnight, now - 86_400_000))
        assertEquals(listOf(1L), FlashcardSchedule.queue(questions, reviews, 0, clock(now), zone).questions.map { it.id })
        assertEquals(listOf(1L, 2L), FlashcardSchedule.queue(questions, reviews, 0, clock(midnight), zone).questions.map { it.id })
    }

    @Test fun newCardLimitsAreSharedAcrossBanksAndUnlimited() {
        val now = instant("2026-06-01T12:00:00")
        val questions = (1L..80L).map { question(it) }
        val introducedElsewhere = (100L..102L).map { reviewed(it, now + 86_400_000, now) }
        listOf(10 to 7, 20 to 17, 50 to 47).forEach { (limit, expected) ->
            assertEquals(expected, FlashcardSchedule.queue(questions, introducedElsewhere, limit, clock(now), zone).questions.size)
        }
        assertEquals(80, FlashcardSchedule.queue(questions, introducedElsewhere, null, clock(now), zone).questions.size)
    }

    @Test fun dueOrderingNewOrderingSessionTailAndCompletion() {
        val now = instant("2026-06-01T12:00:00")
        val questions = (1L..6L).map { question(it) }
        val reviews = listOf(reviewed(1, now + 5000, now - 86_400_000), reviewed(3, now - 5000, now - 86_400_000), reviewed(5, now + 86_400_000, now - 86_400_000))
        val queue = FlashcardSchedule.queue(questions, reviews, 2, clock(now), zone)
        assertEquals(listOf(3L, 1L, 2L, 4L), queue.questions.map { it.id })
        val withTail = FlashcardSchedule.queue(questions, reviews, 2, clock(now), zone, listOf(3, 5))
        assertEquals(listOf(1L, 2L, 4L, 3L, 5L), withTail.questions.map { it.id })
        val completed = FlashcardSchedule.queue(listOf(question(5)), reviews, 0, clock(now), zone)
        assertTrue(completed.questions.isEmpty())
        assertEquals(now + 86_400_000, completed.nextDueAt)
        assertNull(FlashcardSchedule.queue(emptyList(), reviews, 0, clock(now), zone).nextDueAt)
    }
}
