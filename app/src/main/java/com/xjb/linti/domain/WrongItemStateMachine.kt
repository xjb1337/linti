package com.xjb.linti.domain

object WrongItemStateMachine {
    fun onWrong(
        current: WrongItemState?,
        questionId: Long,
        bankId: Long,
        now: Long,
    ): WrongItemState = WrongItemState(
        questionId = questionId,
        bankId = bankId,
        wrongCount = (current?.wrongCount ?: 0) + 1,
        lastWrongAt = now,
        corrected = false,
        pendingReview = true,
    )

    fun onCorrect(current: WrongItemState?): WrongItemState? =
        current?.copy(corrected = true, pendingReview = false)
}
