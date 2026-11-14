package com.xjb.linti.data.db

import com.xjb.linti.domain.BankSummary
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ExamHistoryDetail
import com.xjb.linti.domain.ExamHistoryEntry
import com.xjb.linti.domain.examAccuracy
import com.xjb.linti.domain.FavoriteQuestionItem
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.NoteQuestionItem
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuestionOrigin
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.TypeStats
import com.xjb.linti.domain.WrongItemState
import com.xjb.linti.domain.WrongQuestionItem

fun QuestionBankEntity.toDomain(): QuestionBank = QuestionBank(
    id = id,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt,
    note = note,
)

fun QuestionEntity.toDomain(): Question = Question(
    id = id,
    bankId = bankId,
    chapterId = chapterId,
    origin = QuestionOrigin.entries.firstOrNull { it.name == origin } ?: QuestionOrigin.IMPORTED,
    sourceExcerpt = sourceExcerpt,
    acceptedAnswers = JsonCodec.decodeAnswerAlternatives(acceptedAnswersJson),
    sourceNumber = sourceNumber,
    type = questionTypeFromStorage(type),
    stem = stem,
    options = JsonCodec.decodeOptions(optionsJson),
    answer = answer,
    explanation = explanation,
    tags = tags,
    knowledgePoint = knowledgePoint,
    confidence = confidence,
    needsReview = needsReview,
    reviewReasons = JsonCodec.decodeStrings(reviewReasonsJson),
    sourceFileName = sourceFileName,
    rawSnippet = rawSnippet,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Question.toEntity(): QuestionEntity = QuestionEntity(
    id = id,
    bankId = bankId,
    chapterId = chapterId,
    origin = origin.name,
    sourceExcerpt = sourceExcerpt,
    acceptedAnswersJson = JsonCodec.encodeAnswerAlternatives(acceptedAnswers),
    sourceNumber = sourceNumber,
    type = type.name,
    stem = stem,
    optionsJson = JsonCodec.encodeOptions(options),
    answer = answer,
    explanation = explanation,
    tags = tags,
    knowledgePoint = knowledgePoint,
    confidence = confidence,
    needsReview = needsReview,
    reviewReasonsJson = JsonCodec.encodeStrings(reviewReasons),
    sourceFileName = sourceFileName,
    rawSnippet = rawSnippet,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DraftQuestion.toQuestion(
    bankId: Long,
    now: Long,
    fallbackSourceFileName: String,
): Question = Question(
    bankId = bankId,
    sourceNumber = sourceNumber,
    type = type,
    stem = stem.trim(),
    options = if (type == QuestionType.ESSAY) emptyList() else options,
    answer = AnswerNormalizer.normalizeForStorage(type, answer),
    explanation = explanation.trim(),
    tags = tags,
    knowledgePoint = knowledgePoint,
    confidence = confidence,
    needsReview = needsReview,
    reviewReasons = reviewReasons,
    sourceFileName = sourceFileName.ifBlank { fallbackSourceFileName },
    rawSnippet = rawSnippet,
    acceptedAnswers = acceptedAnswers,
    images = images,
    createdAt = now,
    updatedAt = now,
)

fun ImportJobEntity.toDomain(): ImportJob = ImportJob(
    id = id,
    fileName = fileName,
    mimeType = mimeType,
    kind = kind,
    method = ImportMethod.fromStorage(method),
    status = ImportStatus.entries.firstOrNull { it.name == status } ?: ImportStatus.REVIEW,
    recognizedCount = recognizedCount,
    confirmedCount = confirmedCount,
    pendingCount = pendingCount,
    failedCount = failedCount,
    createdAt = createdAt,
    targetBankId = targetBankId,
    warnings = JsonCodec.decodeStrings(warningsJson),
)

fun ImportJob.toEntity(): ImportJobEntity = ImportJobEntity(
    id = id,
    fileName = fileName,
    mimeType = mimeType,
    kind = kind,
    method = method.storageValue,
    status = status.name,
    recognizedCount = recognizedCount,
    confirmedCount = confirmedCount,
    pendingCount = pendingCount,
    failedCount = failedCount,
    createdAt = createdAt,
    targetBankId = targetBankId,
    warningsJson = JsonCodec.encodeStrings(warnings),
)

fun BankStatsRow.toDomain(): BankSummary = BankSummary(
    id = id,
    name = name,
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt,
    questionCount = questionCount,
    wrongCount = wrongCount,
    pendingWrongCount = pendingWrongCount,
    correctedWrongCount = correctedWrongCount,
    favoriteCount = favoriteCount,
    noteCount = noteCount,
    sequenceIndex = sequenceIndex,
    sequenceAnsweredCount = sequenceAnsweredCount,
    lastOpenedAt = lastOpenedAt,
)

fun WrongQuestionRow.toDomain(): WrongQuestionItem = WrongQuestionItem(
    wrongItem = WrongItemState(
        questionId = questionId,
        bankId = bankId,
        wrongCount = wrongCount,
        lastWrongAt = lastWrongAt,
        corrected = corrected,
        pendingReview = pendingReview,
        correctStreak = correctStreak,
        masteredAt = masteredAt,
    ),
    bankName = bankName,
    sourceNumber = sourceNumber,
    type = questionTypeFromStorage(type),
    stem = stem,
)

fun FavoriteQuestionRow.toDomain(): FavoriteQuestionItem = FavoriteQuestionItem(
    questionId = questionId,
    bankId = bankId,
    bankName = bankName,
    type = questionTypeFromStorage(type),
    stem = stem,
    answer = answer,
    createdAt = createdAt,
)

fun NoteQuestionRow.toDomain(): NoteQuestionItem = NoteQuestionItem(
    questionId = questionId,
    bankId = bankId,
    bankName = bankName,
    type = questionTypeFromStorage(type),
    stem = stem,
    content = content,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun WrongItemEntity.toDomain(): WrongItemState = WrongItemState(
    questionId = questionId,
    bankId = bankId,
    wrongCount = wrongCount,
    lastWrongAt = lastWrongAt,
    corrected = corrected,
    pendingReview = pendingReview,
    correctStreak = correctStreak,
    masteredAt = masteredAt,
)

fun WrongItemState.toEntity(): WrongItemEntity = WrongItemEntity(
    questionId = questionId,
    bankId = bankId,
    wrongCount = wrongCount,
    lastWrongAt = lastWrongAt,
    corrected = corrected,
    pendingReview = pendingReview,
    correctStreak = correctStreak,
    masteredAt = masteredAt,
)

fun questionTypeFromStorage(value: String): QuestionType =
    QuestionType.entries.firstOrNull { it.name == value } ?: QuestionType.SINGLE

// 模考历史只依赖交卷时保存的快照；满分仅在分题型统计完整（条目齐全、
// 各题型题数之和与会话保存的题数一致）时求和给出，不用组卷配置的计划满分
// 代替实际满分，也不读取现有题目重新判分。
private fun QuizSessionEntity.historyTypeStats(): List<TypeStats> =
    JsonCodec.decodeTypeStats(statsJson)

private fun QuizSessionEntity.historyTotalQuestions(): Int? =
    JsonCodec.decodeLongList(questionIdsJson).takeIf { it.isNotEmpty() }?.size

private fun historyMaxScore(stats: List<TypeStats>, totalQuestions: Int?): Double? {
    if (stats.isEmpty() || totalQuestions == null) return null
    return stats.takeIf { it.sumOf { stat -> stat.total } == totalQuestions }?.sumOf { it.maxScore }
}

fun QuizSessionEntity.toExamHistoryEntry(): ExamHistoryEntry {
    val stats = historyTypeStats()
    val totalQuestions = historyTotalQuestions()
    return ExamHistoryEntry(
        sessionId = id,
        bankId = bankId,
        bankName = bankName,
        submittedAt = endedAt?.takeIf { it > 0 },
        score = score,
        maxScore = historyMaxScore(stats, totalQuestions),
        passed = passed,
        accuracy = examAccuracy(correctCount, wrongCount, unansweredCount, totalQuestions),
    )
}

fun QuizSessionEntity.toExamHistoryDetail(hasPaper: Boolean): ExamHistoryDetail {
    val stats = historyTypeStats()
    val totalQuestions = historyTotalQuestions()
    val config = JsonCodec.decodeExamConfigSnapshot(configJson)
    val start = startedAt.takeIf { it > 0 }
    val end = endedAt?.takeIf { it > 0 }
    val durationSeconds = if (start != null && end != null && end >= start) {
        val elapsedMillis = end - start
        val limitMillis = config.durationMinutes?.takeIf { it > 0 }?.times(60_000L)
        val capped = if (limitMillis != null && elapsedMillis > limitMillis) limitMillis else elapsedMillis
        capped / 1000
    } else {
        null
    }
    return ExamHistoryDetail(
        sessionId = id,
        bankId = bankId,
        bankName = bankName,
        startedAt = start,
        submittedAt = end,
        durationSeconds = durationSeconds,
        score = score,
        maxScore = historyMaxScore(stats, totalQuestions),
        passed = passed,
        passScore = config.passScore,
        totalQuestions = totalQuestions,
        correctCount = correctCount,
        wrongCount = wrongCount,
        unansweredCount = unansweredCount,
        typeStats = stats,
        hasPaper = hasPaper,
    )
}
