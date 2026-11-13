package com.xjb.linti.data.db

import com.xjb.linti.domain.BankSummary
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.FavoriteQuestionItem
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.NoteQuestionItem
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuestionType
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
    options = options,
    answer = AnswerNormalizer.normalizeForStorage(type, answer),
    explanation = explanation.trim(),
    tags = tags,
    knowledgePoint = knowledgePoint,
    confidence = confidence,
    needsReview = needsReview,
    reviewReasons = reviewReasons,
    sourceFileName = sourceFileName.ifBlank { fallbackSourceFileName },
    rawSnippet = rawSnippet,
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
)

fun WrongItemState.toEntity(): WrongItemEntity = WrongItemEntity(
    questionId = questionId,
    bankId = bankId,
    wrongCount = wrongCount,
    lastWrongAt = lastWrongAt,
    corrected = corrected,
    pendingReview = pendingReview,
)

fun questionTypeFromStorage(value: String): QuestionType =
    QuestionType.entries.firstOrNull { it.name == value } ?: QuestionType.SINGLE
