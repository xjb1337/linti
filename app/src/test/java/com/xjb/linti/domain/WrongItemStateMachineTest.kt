package com.xjb.linti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrongItemStateMachineTest {
    @Test
    fun `wrong enters book then correct resolves and another wrong reopens`() {
        val firstWrong = WrongItemStateMachine.onWrong(
            current = null,
            questionId = 11,
            bankId = 7,
            now = 1000,
        )

        assertEquals(1, firstWrong.wrongCount)
        assertTrue(firstWrong.pendingReview)
        assertFalse(firstWrong.corrected)

        val corrected = WrongItemStateMachine.onCorrect(firstWrong)!!
        assertTrue(corrected.corrected)
        assertFalse(corrected.pendingReview)
        assertEquals(1, corrected.wrongCount)

        val again = WrongItemStateMachine.onWrong(
            current = corrected,
            questionId = 11,
            bankId = 7,
            now = 2000,
        )
        assertEquals(2, again.wrongCount)
        assertEquals(2000, again.lastWrongAt)
        assertFalse(again.corrected)
        assertTrue(again.pendingReview)
    }

    @Test
    fun `correct without a wrong record does nothing`() {
        assertNull(WrongItemStateMachine.onCorrect(null))
    }
}
