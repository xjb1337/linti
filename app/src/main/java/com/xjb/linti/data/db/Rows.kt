package com.xjb.linti.data.db

data class BankStatsRow(
    val id: Long,
    val name: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val questionCount: Int,
    val wrongCount: Int,
    val pendingWrongCount: Int,
    val correctedWrongCount: Int,
    val favoriteCount: Int,
    val noteCount: Int,
    val sequenceIndex: Int,
    val sequenceAnsweredCount: Int,
    val lastOpenedAt: Long?,
)

data class WrongQuestionRow(
    val questionId: Long,
    val bankId: Long,
    val bankName: String,
    val wrongCount: Int,
    val lastWrongAt: Long,
    val corrected: Boolean,
    val pendingReview: Boolean,
    val sourceNumber: String,
    val type: String,
    val stem: String,
)

data class FavoriteQuestionRow(
    val questionId: Long,
    val bankId: Long,
    val bankName: String,
    val type: String,
    val stem: String,
    val answer: String,
    val createdAt: Long,
)

data class NoteQuestionRow(
    val questionId: Long,
    val bankId: Long,
    val bankName: String,
    val type: String,
    val stem: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
