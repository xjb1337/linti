package com.xjb.linti.data.repo

import androidx.room.withTransaction
import com.xjb.linti.data.backup.BackupCodec
import com.xjb.linti.data.backup.BackupPayload
import com.xjb.linti.data.db.AttemptStatRow
import com.xjb.linti.data.db.AttemptSeed
import com.xjb.linti.data.db.ChapterEntity
import com.xjb.linti.data.db.BankStatsRow
import com.xjb.linti.data.db.FlashcardReviewEntity
import com.xjb.linti.data.db.FavoriteEntity
import com.xjb.linti.data.db.ExamDraftCodec
import com.xjb.linti.data.db.ExamDraftSnapshot
import com.xjb.linti.data.db.ExamPaperCodec
import com.xjb.linti.data.db.ImportJobEntity
import com.xjb.linti.data.db.JsonCodec
import com.xjb.linti.data.db.LegacyBackfillData
import com.xjb.linti.data.db.LegacyProgressRow
import com.xjb.linti.data.db.LegacyQuestionRow
import com.xjb.linti.data.db.LegacySessionRow
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.db.NoteEntity
import com.xjb.linti.data.db.PastPaperEntity
import com.xjb.linti.data.db.PastPaperItemEntity
import com.xjb.linti.data.db.PracticeProgressEntity
import com.xjb.linti.data.db.QuestionAttemptEntity
import com.xjb.linti.data.db.QuizSessionEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.db.SessionBackfill
import com.xjb.linti.data.db.WrongItemEntity
import com.xjb.linti.data.db.questionTypeFromStorage
import com.xjb.linti.data.db.toDomain
import com.xjb.linti.data.db.toEntity
import com.xjb.linti.data.db.toExamHistoryDetail
import com.xjb.linti.data.db.toExamHistoryEntry
import com.xjb.linti.data.db.toSeed
import com.xjb.linti.data.import.ImportPersistence
import com.xjb.linti.data.sample.SampleContent
import com.xjb.linti.domain.AttemptStatRecord
import com.xjb.linti.domain.Chapter
import com.xjb.linti.domain.BankSummary
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.ExamHistoryDetail
import com.xjb.linti.domain.ExamHistoryEntry
import com.xjb.linti.domain.ExamPaperReview
import com.xjb.linti.domain.FlashcardReview
import com.xjb.linti.domain.FlashcardSchedule
import com.xjb.linti.domain.QuizClock
import com.xjb.linti.domain.FavoriteQuestionItem
import com.xjb.linti.domain.GradingResult
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.LegacySessionRecord
import com.xjb.linti.domain.NoteQuestionItem
import com.xjb.linti.domain.PastPaper
import com.xjb.linti.domain.PastPaperItem
import com.xjb.linti.domain.PastPaperSource
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuizQuestion
import com.xjb.linti.domain.StudyStatsCalculator
import com.xjb.linti.domain.WrongItemState
import com.xjb.linti.domain.WrongItemStateMachine
import com.xjb.linti.domain.WrongQuestionItem
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class SessionSnapshot(
    val bankId: Long?,
    val bankName: String,
    val mode: PracticeMode,
    val config: ExamConfig,
    val questions: List<QuizQuestion>,
    val answers: Map<Long, String>,
    val flaggedIds: Set<Long>,
    val bookmarkedIds: Set<Long>,
    val startedAt: Long,
    val endedAt: Long,
    val grading: GradingResult,
)

data class PracticeCommit(
    val bankId: Long,
    val mode: PracticeMode,
    val questionId: Long,
    val questionBankId: Long,
    val answer: String,
    val isCorrect: Boolean,
    val currentIndex: Int,
    val answeredAt: Long,
    val roundStartedAt: Long,
    val answerViewed: Boolean = false,
    val submissionId: String = UUID.randomUUID().toString(),
    val reviewZone: ZoneId = ZoneId.systemDefault(),
)

data class ExamRecord(
    val questionId: Long,
    val bankId: Long,
    val answer: String,
    val isCorrect: Boolean,
)

data class ExamDraftSummary(val draftId: String, val bankId: Long)

class LintiRepository(
    private val database: LintiDatabase,
    private val masteryThreshold: () -> Int? = { 2 },
) : ImportPersistence {
    private val bankDao = database.questionBankDao()
    private val chapterDao = database.chapterDao()
    private val pastPaperDao = database.pastPaperDao()
    private val questionDao = database.questionDao()
    private val aiExplanationDao = database.aiExplanationDao()
    private val importJobDao = database.importJobDao()
    private val favoriteDao = database.favoriteDao()
    private val noteDao = database.noteDao()
    private val wrongDao = database.wrongItemDao()
    private val flashcardDao = database.flashcardReviewDao()
    private val progressDao = database.practiceProgressDao()
    private val sessionDao = database.quizSessionDao()
    private val attemptDao = database.questionAttemptDao()
    private val examDraftDao = database.examDraftDao()
    private val examPaperDao = database.examPaperDao()

    val examDrafts: Flow<List<ExamDraftSummary>> = examDraftDao.observeAll()
        .map { rows -> rows.map { ExamDraftSummary(it.draftId, it.bankId) } }
        .flowOn(Dispatchers.IO)

    val bankSummaries: Flow<List<BankSummary>> = bankDao.observeBankStats()
        .map { rows: List<BankStatsRow> -> rows.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    val recentImportJobs: Flow<List<ImportJob>> = importJobDao.observeRecent()
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    val wrongQuestions: Flow<List<WrongQuestionItem>> = wrongDao.observeRows()
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    val favoriteQuestions: Flow<List<FavoriteQuestionItem>> = favoriteDao.observeRows()
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    val noteQuestions: Flow<List<NoteQuestionItem>> = noteDao.observeRows()
        .map { rows -> rows.map { it.toDomain() } }
        .flowOn(Dispatchers.IO)

    val observableQuestionsByBank: (Long) -> Flow<List<Question>> = { bankId ->
        questionDao.observeByBank(bankId)
            .map { rows -> rows.map(QuestionEntity::toDomain) }
            .flowOn(Dispatchers.IO)
    }

    suspend fun getBanks(): List<QuestionBank> =
        bankDao.getAll().map { it.toDomain() }

    suspend fun getBank(bankId: Long): QuestionBank? =
        bankDao.getById(bankId)?.toDomain()

    suspend fun createBank(name: String, note: String? = null, now: Long = System.currentTimeMillis()): Long =
        bankDao.insert(
            com.xjb.linti.data.db.QuestionBankEntity(
                name = name.trim(),
                createdAt = now,
                updatedAt = now,
                note = note,
            ),
        )

    private fun ChapterEntity.toChapter() = Chapter(id, bankId, name, sortOrder, createdAt, updatedAt)

    suspend fun getChapters(bankId: Long): List<Chapter> = chapterDao.getByBank(bankId).map { it.toChapter() }
    suspend fun getAllChapters(): List<Chapter> = chapterDao.getAll().map { it.toChapter() }
    fun observeChapters(bankId: Long): Flow<List<Chapter>> = chapterDao.observeByBank(bankId)
        .map { rows -> rows.map { it.toChapter() } }.flowOn(Dispatchers.IO)

    suspend fun createChapter(bankId: Long, name: String, now: Long = System.currentTimeMillis()): Long = database.withTransaction {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "章节名称不能为空" }
        require(bankDao.getById(bankId) != null) { "题库不存在" }
        val chapters = chapterDao.getByBank(bankId)
        require(chapters.none { it.name == trimmed }) { "章节名称已存在" }
        chapterDao.insert(ChapterEntity(bankId = bankId, name = trimmed, sortOrder = (chapters.maxOfOrNull { it.sortOrder } ?: -1) + 1, createdAt = now, updatedAt = now))
    }

    suspend fun renameChapter(chapterId: Long, name: String, now: Long = System.currentTimeMillis()) = database.withTransaction {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "章节名称不能为空" }
        val chapter = requireNotNull(chapterDao.getById(chapterId)) { "章节不存在" }
        require(chapterDao.getByBank(chapter.bankId).none { it.id != chapterId && it.name == trimmed }) { "章节名称已存在" }
        chapterDao.update(chapter.copy(name = trimmed, updatedAt = now))
    }

    suspend fun deleteChapter(chapterId: Long) = database.withTransaction { chapterDao.deleteById(chapterId) }

    suspend fun moveChapter(chapterId: Long, direction: Int, now: Long = System.currentTimeMillis()) = database.withTransaction {
        require(direction == -1 || direction == 1)
        val chapter = requireNotNull(chapterDao.getById(chapterId)) { "章节不存在" }
        val ordered = chapterDao.getByBank(chapter.bankId).toMutableList()
        val from = ordered.indexOfFirst { it.id == chapterId }
        val to = from + direction
        if (to in ordered.indices) {
            java.util.Collections.swap(ordered, from, to)
            ordered.forEachIndexed { index, item -> chapterDao.update(item.copy(sortOrder = index, updatedAt = now)) }
        }
    }

    private fun PastPaperEntity.toPastPaper(count: Int = 0) = PastPaper(
        id, bankId, year, name, PastPaperSource.valueOf(sourceCategory), sourceNote, createdAt, updatedAt, count,
    )
    private fun PastPaperItemEntity.toPastPaperItem() = PastPaperItem(paperId, questionId, position, originalNumber)

    fun observePastPapers(bankId: Long): Flow<List<PastPaper>> = pastPaperDao.observeByBank(bankId)
        .map { rows -> rows.map { it.paper.toPastPaper(it.questionCount) } }.flowOn(Dispatchers.IO)
    fun observePastPaperItems(paperId: Long): Flow<List<PastPaperItem>> = pastPaperDao.observeItems(paperId)
        .map { rows -> rows.map { it.toPastPaperItem() } }.flowOn(Dispatchers.IO)
    suspend fun getPastPapers(bankId: Long): List<PastPaper> = pastPaperDao.getByBank(bankId).map {
        it.toPastPaper(pastPaperDao.getItems(it.id).size)
    }
    suspend fun getPastPaper(id: Long): PastPaper? = pastPaperDao.getById(id)?.let {
        it.toPastPaper(pastPaperDao.getItems(it.id).size)
    }
    suspend fun getPastPaperItems(paperId: Long): List<PastPaperItem> = pastPaperDao.getItems(paperId).map { it.toPastPaperItem() }
    suspend fun getPastPaperQuestions(paperId: Long): List<Question> = database.withTransaction {
        pastPaperDao.getItems(paperId).map { it.questionId }.chunked(500).flatMap { getQuestionsByIds(it) }
    }

    suspend fun createPastPaper(
        bankId: Long, year: Int, name: String, source: PastPaperSource = PastPaperSource.UNVERIFIED,
        sourceNote: String? = null, now: Long = System.currentTimeMillis(),
    ): Long = database.withTransaction {
        require(bankDao.getById(bankId) != null) { "题库不存在" }
        require(year > 0) { "年份无效" }
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "卷名不能为空" }
        pastPaperDao.insert(PastPaperEntity(bankId = bankId, year = year, name = trimmed,
            sourceCategory = source.name, sourceNote = sourceNote?.trim()?.ifEmpty { null }, createdAt = now, updatedAt = now))
    }

    suspend fun updatePastPaper(
        paperId: Long, year: Int, name: String, source: PastPaperSource, sourceNote: String?,
        now: Long = System.currentTimeMillis(),
    ) = database.withTransaction {
        val paper = requireNotNull(pastPaperDao.getById(paperId)) { "真题卷不存在" }
        require(year > 0) { "年份无效" }
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "卷名不能为空" }
        pastPaperDao.update(paper.copy(year = year, name = trimmed, sourceCategory = source.name,
            sourceNote = sourceNote?.trim()?.ifEmpty { null }, updatedAt = now))
    }

    suspend fun deletePastPaper(paperId: Long) = database.withTransaction { pastPaperDao.deleteById(paperId) }

    suspend fun addQuestionsToPastPaper(paperId: Long, questionIds: List<Long>) = database.withTransaction {
        val paper = requireNotNull(pastPaperDao.getById(paperId)) { "真题卷不存在" }
        require(questionIds.distinct().size == questionIds.size) { "题目重复" }
        val questions = questionIds.chunked(500).flatMap { questionDao.getByIds(it) }.associateBy { it.id }
        require(questions.size == questionIds.size && questions.values.all { it.bankId == paper.bankId }) { "题目不属于该题库" }
        require(questions.values.all { it.origin == "IMPORTED" }) { "AI 出题不能加入真题卷" }
        val existing = pastPaperDao.getItems(paperId)
        val existingIds = existing.map { it.questionId }.toSet()
        require(questionIds.none { it in existingIds }) { "题目已在卷中" }
        val numbers = existing.map { it.originalNumber }.toMutableSet()
        val added = questionIds.mapIndexed { index, id ->
            val position = existing.size + index + 1
            val number = questions.getValue(id).sourceNumber.trim().ifEmpty { position.toString() }
            require(numbers.add(number)) { "原卷题号 $number 已存在" }
            PastPaperItemEntity(paperId, id, position, number)
        }
        if (added.isNotEmpty()) pastPaperDao.insertItems(added)
    }

    private suspend fun renumberPastPaper(paperId: Long) {
        pastPaperDao.getItems(paperId).forEachIndexed { index, item ->
            if (item.position != index + 1) pastPaperDao.updateItem(item.copy(position = index + 1))
        }
    }

    suspend fun movePastPaperItem(paperId: Long, questionId: Long, direction: Int) = database.withTransaction {
        require(direction == -1 || direction == 1)
        val items = pastPaperDao.getItems(paperId).toMutableList()
        val from = items.indexOfFirst { it.questionId == questionId }
        require(from >= 0) { "题目不在卷中" }
        val to = from + direction
        if (to in items.indices) {
            java.util.Collections.swap(items, from, to)
            items.forEachIndexed { index, item -> pastPaperDao.updateItem(item.copy(position = index + 1)) }
        }
    }

    suspend fun removePastPaperItem(paperId: Long, questionId: Long) = database.withTransaction {
        pastPaperDao.deleteItem(paperId, questionId)
        renumberPastPaper(paperId)
    }

    suspend fun updatePastPaperNumber(paperId: Long, questionId: Long, originalNumber: String) = database.withTransaction {
        val number = originalNumber.trim()
        require(number.isNotEmpty()) { "原卷题号不能为空" }
        val items = pastPaperDao.getItems(paperId)
        val item = requireNotNull(items.firstOrNull { it.questionId == questionId }) { "题目不在卷中" }
        require(items.none { it.questionId != questionId && it.originalNumber == number }) { "原卷题号 $number 已存在" }
        pastPaperDao.updateItem(item.copy(originalNumber = number))
    }

    suspend fun deleteQuestion(questionId: Long) = database.withTransaction {
        val papers = pastPaperDao.paperIdsForQuestion(questionId)
        questionDao.deleteById(questionId)
        papers.forEach { renumberPastPaper(it) }
    }

    private suspend fun validateChapter(bankId: Long, chapterId: Long?) {
        if (chapterId != null) {
            require(chapterDao.getById(chapterId)?.bankId == bankId) { "章节不属于该题库" }
        }
    }

    suspend fun moveQuestions(bankId: Long, questionIds: Set<Long>, chapterId: Long?, now: Long = System.currentTimeMillis()) = database.withTransaction {
        validateChapter(bankId, chapterId)
        val rows = questionIds.toList().chunked(500).flatMap { questionDao.getByIds(it) }
        require(rows.size == questionIds.size && rows.all { it.bankId == bankId }) { "题目不属于该题库" }
        questionIds.toList().chunked(500).forEach { questionDao.moveToChapter(it, bankId, chapterId, now) }
    }

    suspend fun getQuestions(bankId: Long): List<Question> =
        questionDao.getByBank(bankId).map(QuestionEntity::toDomain)

    suspend fun getAllQuestions(): List<Question> =
        questionDao.getAll().map(QuestionEntity::toDomain)

    suspend fun getQuestion(questionId: Long): Question? =
        questionDao.getById(questionId)?.toDomain()

    fun observeQuestion(questionId: Long): Flow<Question?> =
        questionDao.observeById(questionId).map { it?.toDomain() }

    suspend fun getQuestionsByIds(ids: List<Long>): List<Question> {
        if (ids.isEmpty()) return emptyList()
        val byId = questionDao.getByIds(ids).associateBy { it.id }
        return ids.mapNotNull { byId[it]?.toDomain() }
    }

    suspend fun searchQuestions(bankId: Long?, query: String): List<Question> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return if (bankId == null) getAllQuestions() else getQuestions(bankId)
        }
        return if (bankId == null) {
            questionDao.searchAll(trimmed).map(QuestionEntity::toDomain)
        } else {
            questionDao.searchInBank(bankId, trimmed).map(QuestionEntity::toDomain)
        }
    }

    suspend fun saveQuestion(question: Question): Long = database.withTransaction {
        val old = if (question.id != 0L) questionDao.getById(question.id) else null
        val normalized = if (old != null && old.bankId != question.bankId) question.copy(chapterId = null) else question
        validateChapter(normalized.bankId, normalized.chapterId)
        if (normalized.id == 0L) questionDao.insert(normalized.toEntity()) else {
            if (old != null && old.bankId != normalized.bankId) {
                val papers = pastPaperDao.paperIdsForQuestion(normalized.id)
                papers.forEach { paperId ->
                    pastPaperDao.deleteItem(paperId, normalized.id)
                    renumberPastPaper(paperId)
                }
            }
            questionDao.update(normalized.toEntity())
            if (old != null && old.bankId != normalized.bankId) flashcardDao.updateBank(normalized.id, normalized.bankId)
            normalized.id
        }
    }

    data class FlashcardSnapshot(val questions: List<Question>, val reviews: List<FlashcardReview>)

    suspend fun flashcardSnapshot(bankId: Long?): FlashcardSnapshot = database.withTransaction {
        val questions = if (bankId == null) questionDao.getAll() else questionDao.getByBank(bankId)
        FlashcardSnapshot(questions.map(QuestionEntity::toDomain), flashcardDao.getAll().map { it.toReview() })
    }

    suspend fun reviewFlashcard(questionId: Long, remembered: Boolean, clock: QuizClock, zone: ZoneId) = database.withTransaction {
        val question = requireNotNull(questionDao.getById(questionId)) { "题目不存在" }
        val previous = flashcardDao.get(questionId)?.toReview()
        val review = FlashcardSchedule.review(questionId, question.bankId, previous, remembered, clock, zone)
        flashcardDao.upsert(review.toEntity())
    }

    suspend fun reviewFlashcardAndSnapshot(questionId: Long, remembered: Boolean, clock: QuizClock, zone: ZoneId, bankId: Long?): FlashcardSnapshot = database.withTransaction {
        reviewFlashcard(questionId, remembered, clock, zone)
        flashcardSnapshot(bankId)
    }

    private fun FlashcardReviewEntity.toReview() = FlashcardReview(questionId, bankId, step, dueAt, firstReviewedAt, lastReviewedAt, reviewCount, lapseCount)
    private fun FlashcardReview.toEntity() = FlashcardReviewEntity(questionId, bankId, step, dueAt, firstReviewedAt, lastReviewedAt, reviewCount, lapseCount)

    suspend fun deleteQuestionsInBank(bankId: Long) {
        database.withTransaction {
            questionDao.deleteByBank(bankId)
            bankDao.touch(bankId, System.currentTimeMillis())
        }
    }

    suspend fun insertImportJob(job: ImportJob): Long =
        importJobDao.insert(job.toEntity())

    suspend fun deleteImportJob(jobId: Long) {
        importJobDao.deleteById(jobId)
    }

    suspend fun updateImportJob(job: ImportJob) {
        importJobDao.update(job.toEntity())
    }

    suspend fun getImportJob(jobId: Long): ImportJob? =
        importJobDao.getById(jobId)?.toDomain()

    suspend fun recordWrong(questionId: Long, bankId: Long, now: Long): WrongItemState =
        database.withTransaction {
            val existing = wrongDao.getByQuestion(questionId)?.toDomain()
            val updated = WrongItemStateMachine.onWrong(existing, questionId, bankId, now)
            wrongDao.upsert(updated.toEntity())
            updated
        }

    suspend fun markWrongCorrected(questionId: Long, now: Long): WrongItemState? =
        database.withTransaction {
            val existing = wrongDao.getByQuestion(questionId)?.toDomain() ?: return@withTransaction null
            val updated = WrongItemStateMachine.markMastered(existing, now)
            wrongDao.upsert(updated.toEntity())
            updated
        }

    suspend fun getWrongRows(): List<WrongQuestionItem> =
        wrongDao.getRows().map { it.toDomain() }

    suspend fun getPendingWrongRows(bankId: Long? = null, questionId: Long? = null, questionIds: Set<Long>? = null): List<WrongQuestionItem> =
        getWrongRows().filter { item ->
            item.wrongItem.pendingReview &&
                (bankId == null || item.wrongItem.bankId == bankId) &&
                (questionId == null || item.wrongItem.questionId == questionId) &&
                (questionIds == null || item.wrongItem.questionId in questionIds)
        }

    suspend fun isFavorite(questionId: Long): Boolean = favoriteDao.count(questionId) > 0

    suspend fun getFavoriteIdsForBank(bankId: Long): Set<Long> =
        favoriteDao.getIdsForBank(bankId).toSet()

    suspend fun getAllFavoriteIds(): Set<Long> = favoriteDao.getAllIds().toSet()

    suspend fun toggleFavorite(questionId: Long, now: Long): Boolean =
        database.withTransaction {
            val exists = favoriteDao.count(questionId) > 0
            if (exists) {
                favoriteDao.delete(questionId)
                false
            } else {
                favoriteDao.upsert(FavoriteEntity(questionId = questionId, createdAt = now))
                true
            }
        }

    suspend fun getNote(questionId: Long): String? = noteDao.get(questionId)?.content

    suspend fun saveNote(questionId: Long, content: String, now: Long) {
        if (content.isBlank()) {
            noteDao.delete(questionId)
            return
        }
        val existing = noteDao.get(questionId)
        noteDao.upsert(
            NoteEntity(
                questionId = questionId,
                content = content.trim(),
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    suspend fun saveSequenceProgress(
        bankId: Long,
        currentIndex: Int,
        answeredCount: Int,
        now: Long,
    ) {
        val roundStartedAt = progressDao.get(bankId, PracticeMode.SEQUENCE.name)?.roundStartedAt ?: 0L
        progressDao.upsert(
            PracticeProgressEntity(
                bankId = bankId,
                mode = PracticeMode.SEQUENCE.name,
                currentIndex = currentIndex.coerceAtLeast(0),
                answeredCount = answeredCount.coerceAtLeast(0),
                lastOpenedAt = now,
                roundStartedAt = roundStartedAt,
            ),
        )
    }

    suspend fun saveSequencePosition(bankId: Long, currentIndex: Int, now: Long) {
        val existing = progressDao.get(bankId, PracticeMode.SEQUENCE.name)
        val roundStartedAt = existing?.roundStartedAt ?: 0L
        progressDao.upsert(
            PracticeProgressEntity(
                bankId = bankId,
                mode = PracticeMode.SEQUENCE.name,
                currentIndex = currentIndex.coerceAtLeast(0),
                answeredCount = attemptDao.countSequenceAnswered(bankId, roundStartedAt),
                lastOpenedAt = now,
                roundStartedAt = roundStartedAt,
            ),
        )
    }

    suspend fun startNewRound(bankId: Long, now: Long) {
        progressDao.upsert(
            PracticeProgressEntity(
                bankId = bankId,
                mode = PracticeMode.SEQUENCE.name,
                currentIndex = 0,
                answeredCount = 0,
                lastOpenedAt = now,
                roundStartedAt = now,
            ),
        )
    }

    suspend fun getSequenceProgress(bankId: Long): PracticeProgressEntity? =
        progressDao.get(bankId, PracticeMode.SEQUENCE.name)

    suspend fun getSequenceRoundAttempts(bankId: Long, since: Long): List<AttemptSeed> =
        attemptDao.sequenceAttemptsSince(bankId, since).map { it.toSeed() }

    suspend fun statsFor(questionIds: List<Long>): Map<Long, AttemptStatRow> =
        if (questionIds.isEmpty()) emptyMap()
        else attemptDao.statsFor(questionIds).associateBy { it.questionId }

    suspend fun getAttemptStatRecords(): List<AttemptStatRecord> =
        attemptDao.getAll().map {
            AttemptStatRecord(
                questionId = it.questionId,
                bankId = it.bankId,
                answeredAt = it.answeredAt,
                isCorrect = it.isCorrect,
                sessionId = it.sessionId,
                answerViewed = it.answerViewed,
            )
        }

    suspend fun getLegacySessionRecords(): List<LegacySessionRecord> =
        sessionDao.getAll().map {
            LegacySessionRecord(
                id = it.id,
                bankId = it.bankId,
                answeredCount = it.answeredCount,
                correctCount = it.correctCount,
                at = it.endedAt ?: it.startedAt,
                answeredQuestionIds = JsonCodec.decodeAnswers(it.answersJson).keys,
                status = it.status,
            )
        }

    suspend fun commitPracticeAnswer(commit: PracticeCommit) {
        require(commit.mode != PracticeMode.EXAM) { "模考须交卷后计入" }
        database.withTransaction {
            require(commit.submissionId.isNotBlank())
            val inserted = attemptDao.insertIfNew(
                QuestionAttemptEntity(
                    questionId = commit.questionId,
                    bankId = commit.questionBankId,
                    mode = commit.mode.name,
                    answer = commit.answer,
                    isCorrect = commit.isCorrect,
                    answeredAt = commit.answeredAt,
                    submissionId = commit.submissionId,
                    answerViewed = commit.answerViewed,
                ),
            )
            if (inserted == -1L) return@withTransaction
            applyOutcome(
                questionId = commit.questionId,
                bankId = commit.questionBankId,
                outcome = if (commit.isCorrect) QuestionOutcome.CORRECT else QuestionOutcome.WRONG,
                mode = commit.mode,
                now = commit.answeredAt,
                answerViewed = commit.answerViewed,
                reviewZone = commit.reviewZone,
            )
            if (commit.mode == PracticeMode.SEQUENCE) {
                val roundStartedAt = progressDao.get(commit.bankId, PracticeMode.SEQUENCE.name)
                    ?.roundStartedAt ?: commit.roundStartedAt
                progressDao.upsert(
                    PracticeProgressEntity(
                        bankId = commit.bankId,
                        mode = PracticeMode.SEQUENCE.name,
                        currentIndex = commit.currentIndex.coerceAtLeast(0),
                        answeredCount = attemptDao.countSequenceAnswered(commit.bankId, roundStartedAt),
                        lastOpenedAt = commit.answeredAt,
                        roundStartedAt = roundStartedAt,
                    ),
                )
            }
        }
    }

    private suspend fun applyOutcome(
        questionId: Long,
        bankId: Long,
        outcome: QuestionOutcome,
        mode: PracticeMode,
        now: Long,
        answerViewed: Boolean = false,
        reviewZone: ZoneId = ZoneId.systemDefault(),
        scheduleEssay: Boolean? = null,
    ) {
        if (outcome != QuestionOutcome.CORRECT && outcome != QuestionOutcome.WRONG) return
        val existing = wrongDao.getByQuestion(questionId)?.toDomain()
        val updated = WrongItemStateMachine.onAnswer(
            current = existing, questionId = questionId, bankId = bankId, mode = mode,
            isCorrect = outcome == QuestionOutcome.CORRECT, answerViewed = answerViewed,
            threshold = masteryThreshold(), now = now,
        )
        if (updated != null && updated != existing) wrongDao.upsert(updated.toEntity())
        if (!answerViewed && mode != PracticeMode.RECITE && mode != PracticeMode.FLASHCARD &&
            (scheduleEssay ?: (questionDao.getById(questionId)?.type == com.xjb.linti.domain.QuestionType.ESSAY.name))) {
            val prior = flashcardDao.get(questionId)?.toReview()
            val review = FlashcardSchedule.review(questionId, bankId, prior, outcome == QuestionOutcome.CORRECT, QuizClock { now }, reviewZone)
            flashcardDao.upsert(review.toEntity())
        }
    }

    private fun buildSessionEntity(snapshot: SessionSnapshot): QuizSessionEntity =
        QuizSessionEntity(
            bankId = snapshot.bankId,
            bankName = snapshot.bankName,
            mode = snapshot.mode.name,
            configJson = JsonCodec.encodeExamConfig(snapshot.config),
            questionIdsJson = JsonCodec.encodeLongList(snapshot.questions.map { it.questionId }),
            answersJson = JsonCodec.encodeAnswers(snapshot.answers),
            flagsJson = JsonCodec.encodeLongSet(snapshot.flaggedIds),
            bookmarksJson = JsonCodec.encodeLongSet(snapshot.bookmarkedIds),
            startedAt = snapshot.startedAt,
            endedAt = snapshot.endedAt,
            score = snapshot.grading.totalScore,
            passed = snapshot.grading.passed,
            statsJson = JsonCodec.encodeTypeStats(snapshot.grading.typeStats),
            status = "COMPLETED",
            answeredCount = snapshot.grading.answeredCount,
            correctCount = snapshot.grading.correctCount,
            wrongCount = snapshot.grading.wrongCount,
            unansweredCount = snapshot.grading.unansweredCount,
        )

    suspend fun saveSession(snapshot: SessionSnapshot): Long =
        sessionDao.insert(buildSessionEntity(snapshot))

    suspend fun createExamDraft(snapshot: ExamDraftSnapshot) {
        val (entity, chunks) = withContext(Dispatchers.Default) { ExamDraftCodec.encodeInitial(snapshot) }
        database.withTransaction {
            examDraftDao.insert(entity)
            examDraftDao.insertChunks(chunks)
        }
    }

    suspend fun hasExamDraft(bankId: Long): Boolean = examDraftDao.getByBank(bankId) != null

    suspend fun getExamDraft(bankId: Long): ExamDraftSnapshot? {
        val stored = database.withTransaction {
            val entity = examDraftDao.getByBank(bankId) ?: return@withTransaction null
            entity to examDraftDao.getChunks(entity.draftId)
        } ?: return null
        return withContext(Dispatchers.Default) { ExamDraftCodec.decode(stored.first, stored.second) }
    }

    suspend fun saveExamDraft(snapshot: ExamDraftSnapshot): Boolean {
        val progress = withContext(Dispatchers.Default) { ExamDraftCodec.encodeProgress(snapshot) }
        return database.withTransaction {
            if (examDraftDao.updateIfNewer(snapshot.draftId, snapshot.revision) == 1) {
                examDraftDao.deleteProgress(snapshot.draftId)
                examDraftDao.insertChunks(progress)
                true
            } else {
                val current = examDraftDao.getByBank(snapshot.bankId)
                current?.draftId == snapshot.draftId && current.revision >= snapshot.revision
            }
        }
    }

    suspend fun discardExamDraft(draftId: String): Boolean = examDraftDao.deleteById(draftId) == 1

    suspend fun submitExamSession(
        snapshot: SessionSnapshot,
        records: List<ExamRecord>,
        draftId: String,
        reviewZone: ZoneId = ZoneId.systemDefault(),
    ): Long = database.withTransaction {
        check(examDraftDao.deleteById(draftId) == 1) { "考试进度已不存在" }
        val sessionId = sessionDao.insert(buildSessionEntity(snapshot))
        // 原卷快照与成绩同事务写入：任一步失败整笔回滚，
        // 不会出现有成绩无原卷的半条记录，草稿仍可续考或重试。
        if (snapshot.mode == PracticeMode.EXAM) {
            val paper = ExamPaperCodec.encode(sessionId, snapshot.questions, snapshot.grading)
            examPaperDao.insert(paper.first)
            examPaperDao.insertChunks(paper.second)
        }
        val existingQuestions = if (records.isEmpty()) emptyMap() else {
            records.map { it.questionId }.distinct().chunked(500)
                .flatMap { questionDao.getByIds(it) }.associateBy { it.id }
        }
        records.filter { existingQuestions[it.questionId]?.bankId == it.bankId }.distinctBy { it.questionId }.forEach { record ->
            attemptDao.insert(
                QuestionAttemptEntity(
                    questionId = record.questionId,
                    bankId = record.bankId,
                    mode = snapshot.mode.name,
                    answer = record.answer,
                    isCorrect = record.isCorrect,
                    answeredAt = snapshot.endedAt,
                    sessionId = sessionId,
                ),
            )
            applyOutcome(
                questionId = record.questionId,
                bankId = record.bankId,
                outcome = if (record.isCorrect) QuestionOutcome.CORRECT else QuestionOutcome.WRONG,
                mode = snapshot.mode,
                now = snapshot.endedAt,
                reviewZone = reviewZone,
                scheduleEssay = snapshot.questions.firstOrNull { it.questionId == record.questionId }?.type == com.xjb.linti.domain.QuestionType.ESSAY,
            )
        }
        sessionId
    }

    suspend fun getRecentCompletedSessions(limit: Int = 100): List<QuizSessionEntity> =
        sessionDao.getRecentCompleted(limit)

    suspend fun getExamHistory(): List<ExamHistoryEntry> =
        sessionDao.getCompletedExamSessions().map { it.toExamHistoryEntry() }

    suspend fun getExamHistoryDetail(sessionId: Long): ExamHistoryDetail? =
        sessionDao.getById(sessionId)?.let { session ->
            session.toExamHistoryDetail(
                hasPaper = session.mode == PracticeMode.EXAM.name &&
                    session.status == SessionBackfill.COMPLETED &&
                    examPaperDao.hasPaper(sessionId),
            )
        }

    /**
     * 读取已交卷模考的原卷：只认 EXAM + COMPLETED 的场次，且必须有快照。
     * 快照缺块或损坏时抛错，由上层显示「试卷读取失败」；旧场次无快照返回 null。
     */
    suspend fun getExamPaperReview(sessionId: Long): ExamPaperReview? {
        val session = sessionDao.getById(sessionId) ?: return null
        if (session.mode != PracticeMode.EXAM.name || session.status != SessionBackfill.COMPLETED) {
            return null
        }
        val entity = examPaperDao.getBySession(sessionId) ?: return null
        val questions = ExamPaperCodec.decode(entity, examPaperDao.getChunks(sessionId))
        return ExamPaperReview(
            sessionId = sessionId,
            bankName = session.bankName,
            submittedAt = session.endedAt?.takeIf { it > 0 },
            questions = questions,
        )
    }

    suspend fun getAllSessions(): List<QuizSessionEntity> = sessionDao.getAll()

    suspend fun getAnsweredQuestionIds(bankId: Long): Set<Long> =
        StudyStatsCalculator.answeredQuestionIds(
            attempts = getAttemptStatRecords(),
            sessions = getLegacySessionRecords(),
            bankId = bankId,
        )

    suspend fun studyAggregate(
        todayStart: Long,
        zone: java.time.ZoneId,
    ) = StudyStatsCalculator.aggregate(
        attempts = getAttemptStatRecords(),
        sessions = getLegacySessionRecords(),
        todayStart = todayStart,
        zone = zone,
    )

    suspend fun exportBackupJson(now: Long = System.currentTimeMillis()): String =
        database.withTransaction {
            BackupCodec.encode(
                BackupPayload(
                    exportedAt = now,
                    banks = bankDao.getAll(),
                    chapters = chapterDao.getAll(),
                    pastPapers = pastPaperDao.getAll(),
                    pastPaperItems = pastPaperDao.getAllItems(),
                    questions = questionDao.getAll(),
                    aiExplanations = aiExplanationDao.getAll(),
                    importJobs = importJobDao.getAll(),
                    favorites = favoriteDao.getAll(),
                    notes = noteDao.getAll(),
                    wrongItems = wrongDao.getAll(),
                    flashcardReviews = flashcardDao.getAll(),
                    progress = progressDao.getAll(),
                    sessions = sessionDao.getAll(),
                    questionAttempts = attemptDao.getAll(),
                    examPapers = examPaperDao.getAll(),
                    examPaperChunks = examPaperDao.getAllChunks(),
                ),
            )
        }

    suspend fun restoreBackupJson(json: String) {
        database.withTransaction {
            val payload = BackupCodec.decode(json)
            // 校验先于任何删除：快照版本、归属已交卷模考、题序与分块完整性
            // 任一不通过即抛错回滚，设备原有数据原样保留，不会清空后恢复一半。
            ExamPaperCodec.validateBackup(
                papers = payload.examPapers,
                chunks = payload.examPaperChunks,
                sessions = payload.sessions,
            )
            val questionsById = payload.questions.associateBy { it.id }
            require(payload.aiExplanations.map { it.questionId }.distinct().size == payload.aiExplanations.size &&
                payload.aiExplanations.all { it.questionId in questionsById }) { "备份 AI 解析题目引用无效" }
            require(payload.flashcardReviews.map { it.questionId }.distinct().size == payload.flashcardReviews.size) { "备份闪卡引用重复" }
            require(payload.flashcardReviews.all { questionsById[it.questionId]?.bankId == it.bankId }) { "备份闪卡题目引用无效" }
            val bankIds = payload.banks.map { it.id }.toSet()
            require(bankIds.size == payload.banks.size && bankIds.all { it > 0 } &&
                questionsById.size == payload.questions.size && payload.questions.all { it.id > 0 && it.bankId in bankIds }) {
                "备份题库或题目引用无效"
            }
            val papersById = payload.pastPapers.associateBy { it.id }
            require(papersById.size == payload.pastPapers.size && payload.pastPapers.all {
                it.id > 0 && it.bankId in bankIds && it.year > 0 &&
                    it.name.isNotBlank() && it.name == it.name.trim() &&
                    PastPaperSource.entries.any { source -> source.name == it.sourceCategory }
            }) { "备份真题卷无效" }
            require(payload.pastPaperItems.all { item ->
                val paper = papersById[item.paperId]
                paper != null && questionsById[item.questionId]?.bankId == paper.bankId &&
                    questionsById[item.questionId]?.origin == "IMPORTED" &&
                    item.originalNumber.isNotBlank() && item.originalNumber == item.originalNumber.trim()
            }) { "备份真题题目引用无效" }
            payload.pastPaperItems.groupBy { it.paperId }.values.forEach { items ->
                require(items.map { it.questionId }.distinct().size == items.size &&
                    items.map { it.originalNumber }.distinct().size == items.size &&
                    items.map { it.position }.sorted() == (1..items.size).toList()) { "备份真题题号或顺序无效" }
            }
            examPaperDao.deleteAll()
            examDraftDao.deleteAll()
            sessionDao.deleteAll()
            attemptDao.deleteAll()
            progressDao.deleteAll()
            wrongDao.deleteAll()
            noteDao.deleteAll()
            favoriteDao.deleteAll()
            importJobDao.deleteAll()
            questionDao.deleteAll()
            bankDao.deleteAll()

            if (payload.banks.isNotEmpty()) bankDao.insertAll(payload.banks)
            val chapterIds = mutableMapOf<Long, Pair<Long, Long>>()
            payload.chapters.forEach { chapter ->
                require(chapter.id > 0 && chapter.id !in chapterIds) { "备份章节引用重复" }
                require(chapter.name.isNotBlank() && chapter.name == chapter.name.trim()) { "备份章节名称无效" }
                val newId = chapterDao.insert(chapter.copy(id = 0))
                chapterIds[chapter.id] = newId to chapter.bankId
            }
            val restoredQuestions = payload.questions.map { question ->
                val ref = question.chapterId
                if (ref == null) question else {
                    val (newId, bankId) = requireNotNull(chapterIds[ref]) { "备份章节引用不存在" }
                    require(question.bankId == bankId) { "备份章节不属于题目所在题库" }
                    question.copy(chapterId = newId)
                }
            }
            val restoredQuestionIds = if (restoredQuestions.isNotEmpty()) questionDao.insertAll(restoredQuestions) else emptyList()
            val questionIds = restoredQuestions.map { it.id }.zip(restoredQuestionIds).toMap()
            payload.aiExplanations.forEach { aiExplanationDao.upsert(it.copy(questionId = questionIds.getValue(it.questionId))) }
            val pastPaperIds = payload.pastPapers.associate { paper ->
                paper.id to pastPaperDao.insert(paper.copy(id = 0))
            }
            if (payload.pastPaperItems.isNotEmpty()) pastPaperDao.insertItems(payload.pastPaperItems.map {
                it.copy(paperId = pastPaperIds.getValue(it.paperId))
            })
            if (payload.importJobs.isNotEmpty()) importJobDao.insertAll(payload.importJobs)
            if (payload.favorites.isNotEmpty()) favoriteDao.insertAll(payload.favorites)
            if (payload.notes.isNotEmpty()) noteDao.insertAll(payload.notes)
            if (payload.wrongItems.isNotEmpty()) wrongDao.insertAll(payload.wrongItems)
            if (payload.flashcardReviews.isNotEmpty()) flashcardDao.insertAll(payload.flashcardReviews)

            val progressRows = if (payload.version < 2) {
                val data = LegacyBackfillData(
                    sessions = payload.sessions
                        .filter { it.status == SessionBackfill.COMPLETED && it.mode != SessionBackfill.EXAM }
                        .map {
                            LegacySessionRow(
                                id = it.id,
                                mode = it.mode,
                                startedAt = it.startedAt,
                                endedAt = it.endedAt,
                                questionIdsJson = it.questionIdsJson,
                                answersJson = it.answersJson,
                            )
                        },
                    questions = payload.questions.map {
                        LegacyQuestionRow(
                            id = it.id,
                            bankId = it.bankId,
                            type = it.type,
                            answer = it.answer,
                            optionsJson = it.optionsJson,
                        )
                    },
                    progress = payload.progress.map {
                        LegacyProgressRow(
                            bankId = it.bankId,
                            mode = it.mode,
                            roundStartedAt = it.roundStartedAt,
                        )
                    },
                )
                val (seeds, counts) = SessionBackfill.backfill(data)
                seeds.forEach { attemptDao.insert(it.toEntity()) }
                val countsByBank = counts.toMap()
                payload.progress.map { row ->
                    if (row.mode == SessionBackfill.SEQUENCE) {
                        row.copy(answeredCount = countsByBank[row.bankId] ?: row.answeredCount)
                    } else row
                }
            } else {
                payload.questionAttempts.forEach { attemptDao.insert(it) }
                payload.progress
            }
            if (progressRows.isNotEmpty()) progressDao.insertAll(progressRows)
            if (payload.sessions.isNotEmpty()) sessionDao.insertAll(payload.sessions)
            // 原卷快照在会话之后写入（外键指向 quiz_sessions）
            if (payload.examPapers.isNotEmpty()) examPaperDao.insertAll(payload.examPapers)
            if (payload.examPaperChunks.isNotEmpty()) examPaperDao.insertAllChunks(payload.examPaperChunks)
        }
    }

    suspend fun seedSampleDataIfEmpty(): Long? =
        database.withTransaction {
            if (bankDao.count() > 0) return@withTransaction null
            insertSampleContentLocked()
        }

    suspend fun loadSampleDataIfMissing(): Long? =
        database.withTransaction {
            val exists = bankDao.getAll().any { it.name == SampleContent.BANK_NAME }
            if (exists) return@withTransaction null
            insertSampleContentLocked()
        }

    private suspend fun insertSampleContentLocked(): Long {
        val now = System.currentTimeMillis()
        val bankId = bankDao.insert(
            com.xjb.linti.data.db.QuestionBankEntity(
                name = SampleContent.BANK_NAME,
                createdAt = now,
                updatedAt = now,
                note = SampleContent.BANK_NOTE,
            ),
        )
        val entities = SampleContent.questions().map { question ->
            question.copy(
                bankId = bankId,
                sourceFileName = SampleContent.SOURCE_FILE_NAME,
                createdAt = now,
                updatedAt = now,
            ).toEntity()
        }
        questionDao.insertAll(entities)
        importJobDao.insert(
            ImportJobEntity(
                fileName = SampleContent.SOURCE_FILE_NAME,
                mimeType = "text/plain",
                kind = "txt",
                method = "LOCAL",
                status = ImportStatus.CONFIRMED.name,
                recognizedCount = entities.size,
                confirmedCount = entities.size,
                pendingCount = 0,
                failedCount = 0,
                createdAt = now,
                targetBankId = bankId,
                warningsJson = "[]",
            ),
        )
        return bankId
    }

    override suspend fun <T> inImportTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }

    override suspend fun getImportBank(bankId: Long): QuestionBank? = getBank(bankId)

    override suspend fun createImportBank(name: String, now: Long): Long =
        createBank(name = name, now = now)

    override suspend fun insertImportQuestions(questions: List<Question>): List<Long> {
        questions.forEach { validateChapter(it.bankId, it.chapterId) }
        return questionDao.insertAll(questions.map { it.toEntity() })
    }

    override suspend fun getImportJobForConfirm(jobId: Long): ImportJob? = getImportJob(jobId)

    override suspend fun updateImportJobForConfirm(job: ImportJob) {
        updateImportJob(job)
    }
}
