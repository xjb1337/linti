package com.xjb.linti.domain

import org.junit.Assert.*
import org.junit.Test

class WrongItemStateMachineTest {
    private fun wrong() = WrongItemStateMachine.onWrong(null, 11, 7, 100)
    private fun answer(item: WrongItemState?, correct: Boolean = true, threshold: Int? = 2,
        mode: PracticeMode = PracticeMode.SEQUENCE, viewed: Boolean = false, now: Long = 200) =
        WrongItemStateMachine.onAnswer(item, 11, 7, mode, correct, viewed, threshold, now)

    @Test fun streakAccumulatesAndWrongResetsMastery() {
        val first = answer(wrong())!!
        assertEquals(1, first.correctStreak)
        assertTrue(first.pendingReview)
        assertNull(first.masteredAt)
        val mastered = answer(first, now = 300)!!
        assertEquals(2, mastered.correctStreak)
        assertEquals(300L, mastered.masteredAt)
        assertTrue(mastered.corrected)
        assertFalse(mastered.pendingReview)
        val reopened = answer(mastered, correct = false, now = 400)!!
        assertEquals(0, reopened.correctStreak)
        assertEquals(2, reopened.wrongCount)
        assertEquals(400L, reopened.lastWrongAt)
        assertNull(reopened.masteredAt)
        assertTrue(reopened.pendingReview)
        assertFalse(reopened.corrected)
    }

    @Test fun thresholdsOneTwoAndFive() {
        for (threshold in listOf(1, 2, 5)) {
            var item = wrong()
            repeat(threshold) { index ->
                item = answer(item, threshold = threshold, now = 300 + index.toLong())!!
                assertEquals(index + 1, item.correctStreak)
                assertEquals(index + 1 < threshold, item.pendingReview)
            }
            assertNotNull(item.masteredAt)
        }
    }

    @Test fun disabledAccumulatesWithoutAutomaticMasteryAndManualRemovalKeepsHistory() {
        var item = wrong()
        repeat(6) { item = answer(item, threshold = null)!! }
        assertEquals(6, item.correctStreak)
        assertTrue(item.pendingReview)
        assertNull(item.masteredAt)
        val removed = WrongItemStateMachine.markMastered(item, 500)
        assertEquals(500L, removed.masteredAt)
        assertEquals(6, removed.correctStreak)
        assertEquals(1, removed.wrongCount)
        assertFalse(removed.pendingReview)
    }

    @Test fun changedThresholdAppliesOnNextCorrectWithoutRevertingMasteredItems() {
        var item = answer(wrong(), threshold = 5)!!
        item = answer(item, threshold = 5)!!
        assertTrue(item.pendingReview)
        item = answer(item, threshold = 2, now = 300)!!
        assertEquals(3, item.correctStreak)
        assertEquals(300L, item.masteredAt)
        item = answer(item, threshold = 5, now = 400)!!
        assertEquals(300L, item.masteredAt)
        assertFalse(item.pendingReview)
    }

    @Test fun onlyEligibleModesAndUnseenCorrectAnswersCount() {
        val item = wrong()
        for (mode in listOf(PracticeMode.SEQUENCE, PracticeMode.TOPIC, PracticeMode.WRONG, PracticeMode.FAVORITE, PracticeMode.EXAM)) {
            assertEquals(1, answer(item, mode = mode)!!.correctStreak)
        }
        for (mode in listOf(PracticeMode.RECITE, PracticeMode.FLASHCARD)) {
            assertEquals(item, answer(item, mode = mode))
            assertEquals(item, answer(item, correct = false, mode = mode))
            assertNull(answer(null, correct = false, mode = mode))
        }
        assertEquals(item, answer(item, viewed = true))
        val wrongAfterView = answer(item, correct = false, viewed = true)!!
        assertEquals(2, wrongAfterView.wrongCount)
        assertEquals(0, wrongAfterView.correctStreak)
        assertNull(answer(null))
    }
}
