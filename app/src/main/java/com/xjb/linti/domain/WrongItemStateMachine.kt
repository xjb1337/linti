package com.xjb.linti.domain

object WrongItemStateMachine {
    fun onWrong(current: WrongItemState?, questionId: Long, bankId: Long, now: Long): WrongItemState =
        WrongItemState(
            questionId = questionId,
            bankId = bankId,
            wrongCount = (current?.wrongCount ?: 0) + 1,
            lastWrongAt = now,
            corrected = false,
            pendingReview = true,
            correctStreak = 0,
            masteredAt = null,
        )

    fun onCorrect(current: WrongItemState?, threshold: Int?, now: Long): WrongItemState? {
        current ?: return null
        val streak = current.correctStreak + 1
        val masteredAt = current.masteredAt ?: if (threshold != null && streak >= threshold) now else null
        return current.copy(
            correctStreak = streak,
            masteredAt = masteredAt,
            corrected = masteredAt != null,
            pendingReview = masteredAt == null,
        )
    }

    fun onAnswer(
        current: WrongItemState?, questionId: Long, bankId: Long, mode: PracticeMode,
        isCorrect: Boolean, answerViewed: Boolean, threshold: Int?, now: Long,
    ): WrongItemState? {
        if (mode == PracticeMode.RECITE || mode == PracticeMode.FLASHCARD) return current
        return if (isCorrect) {
            if (answerViewed) current else onCorrect(current, threshold, now)
        } else {
            onWrong(current, questionId, bankId, now)
        }
    }

    fun markMastered(current: WrongItemState, now: Long): WrongItemState =
        current.copy(corrected = true, pendingReview = false, masteredAt = current.masteredAt ?: now)
}
