package com.xjb.linti.data.repo

import androidx.room.withTransaction
import com.xjb.linti.data.backup.BackupCodec
import com.xjb.linti.data.backup.BackupPayload
import com.xjb.linti.data.db.AttemptStatRow
import com.xjb.linti.data.db.AttemptSeed
import com.xjb.linti.data.db.BankStatsRow
import com.xjb.linti.data.db.FavoriteEntity
import com.xjb.linti.data.db.ImportJobEntity
import com.xjb.linti.data.db.JsonCodec
import com.xjb.linti.data.db.LegacyBackfillData
import com.xjb.linti.data.db.LegacyProgressRow
import com.xjb.linti.data.db.LegacyQuestionRow
import com.xjb.linti.data.db.LegacySessionRow
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.db.NoteEntity
import com.xjb.linti.data.db.PracticeProgressEntity
import com.xjb.linti.data.db.QuestionAttemptEntity
import com.xjb.linti.data.db.QuizSessionEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.db.SessionBackfill
import com.xjb.linti.data.db.WrongItemEntity
import com.xjb.linti.data.db.questionTypeFromStorage
import com.xjb.linti.data.db.toDomain
import com.xjb.linti.data.db.toEntity
import com.xjb.linti.data.db.toSeed
import com.xjb.linti.data.import.ImportPersistence
import com.xjb.linti.data.sample.SampleContent
import com.xjb.linti.domain.AttemptStatRecord
import com.xjb.linti.domain.BankSummary
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.FavoriteQuestionItem
import com.xjb.linti.domain.GradingResult
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.LegacySessionRecord
import com.xjb.linti.domain.NoteQuestionItem
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuizQuestion
import com.xjb.linti.domain.StudyStatsCalculator
import com.xjb.linti.domain.WrongItemState
import com.xjb.linti.domain.WrongItemStateMachine
import com.xjb.linti.domain.WrongQuestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

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
)

data class ExamRecord(
    val questionId: Long,
    val bankId: Long,
    val answer: String,
    val isCorrect: Boolean,
)

class LintiRepository(
    private val database: LintiDatabase,
) : ImportPersistence {
    private val bankDao = database.questionBankDao()
    private val questionDao = database.questionDao()
    private val importJobDao = database.importJobDao()
    private val favoriteDao = database.favoriteDao()
    private val noteDao = database.noteDao()
    private val wrongDao = database.wrongItemDao()
    private val progressDao = database.practiceProgressDao()
    private val sessionDao = database.quizSessionDao()
    private val attemptDao = database.questionAttemptDao()

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

    suspend fun getQuestions(bankId: Long): List<Question> =
        questionDao.getByBank(bankId).map(QuestionEntity::toDomain)

    suspend fun getAllQuestions(): List<Question> =
        questionDao.getAll().map(QuestionEntity::toDomain)

    suspend fun getQuestion(questionId: Long): Question? =
        questionDao.getById(questionId)?.toDomain()

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

    suspend fun saveQuestion(question: Question): Long {
        val entity = question.toEntity()
        return if (question.id == 0L) {
            questionDao.insert(entity)
        } else {
            questionDao.update(entity)
            question.id
        }
    }

    suspend fun deleteQuestionsInBank(bankId: Long) {
        database.withTransaction {
            questionDao.deleteByBank(bankId)
            bankDao.touch(bankId, System.currentTimeMillis())
        }
    }

    suspend fun insertImportJob(job: ImportJob): Long =
        importJobDao.insert(job.toEntity())

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

    suspend fun markWrongCorrected(questionId: Long): WrongItemState? =
        database.withTransaction {
            val existing = wrongDao.getByQuestion(questionId)?.toDomain() ?: return@withTransaction null
            val updated = WrongItemStateMachine.onCorrect(existing) ?: return@withTransaction null
            wrongDao.upsert(updated.toEntity())
            updated
        }

    suspend fun getWrongRows(): List<WrongQuestionItem> =
        wrongDao.getRows().map { it.toDomain() }

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
        database.withTransaction {
            attemptDao.insert(
                QuestionAttemptEntity(
                    questionId = commit.questionId,
                    bankId = commit.questionBankId,
                    mode = commit.mode.name,
                    answer = commit.answer,
                    isCorrect = commit.isCorrect,
                    answeredAt = commit.answeredAt,
                ),
            )
            applyOutcome(
                questionId = commit.questionId,
                bankId = commit.questionBankId,
                outcome = if (commit.isCorrect) QuestionOutcome.CORRECT else QuestionOutcome.WRONG,
                mode = commit.mode,
                now = commit.answeredAt,
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
    ) {
        when {
            outcome == QuestionOutcome.WRONG -> {
                val existing = wrongDao.getByQuestion(questionId)?.toDomain()
                val updated = WrongItemStateMachine.onWrong(existing, questionId, bankId, now)
                wrongDao.upsert(updated.toEntity())
            }
            outcome == QuestionOutcome.CORRECT && mode == PracticeMode.WRONG -> {
                val existing = wrongDao.getByQuestion(questionId)?.toDomain() ?: return
                val updated = WrongItemStateMachine.onCorrect(existing) ?: return
                wrongDao.upsert(updated.toEntity())
            }
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

    suspend fun submitExamSession(
        snapshot: SessionSnapshot,
        records: List<ExamRecord>,
    ): Long = database.withTransaction {
        val sessionId = sessionDao.insert(buildSessionEntity(snapshot))
        records.forEach { record ->
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
            )
        }
        sessionId
    }

    suspend fun getRecentCompletedSessions(limit: Int = 100): List<QuizSessionEntity> =
        sessionDao.getRecentCompleted(limit)

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
                    questions = questionDao.getAll(),
                    importJobs = importJobDao.getAll(),
                    favorites = favoriteDao.getAll(),
                    notes = noteDao.getAll(),
                    wrongItems = wrongDao.getAll(),
                    progress = progressDao.getAll(),
                    sessions = sessionDao.getAll(),
                    questionAttempts = attemptDao.getAll(),
                ),
            )
        }

    suspend fun restoreBackupJson(json: String) {
        database.withTransaction {
            val payload = BackupCodec.decode(json)
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
            if (payload.questions.isNotEmpty()) questionDao.insertAll(payload.questions)
            if (payload.importJobs.isNotEmpty()) importJobDao.insertAll(payload.importJobs)
            if (payload.favorites.isNotEmpty()) favoriteDao.insertAll(payload.favorites)
            if (payload.notes.isNotEmpty()) noteDao.insertAll(payload.notes)
            if (payload.wrongItems.isNotEmpty()) wrongDao.insertAll(payload.wrongItems)

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

    override suspend fun insertImportQuestions(questions: List<Question>): List<Long> =
        questionDao.insertAll(questions.map { it.toEntity() })

    override suspend fun getImportJobForConfirm(jobId: Long): ImportJob? = getImportJob(jobId)

    override suspend fun updateImportJobForConfirm(job: ImportJob) {
        updateImportJob(job)
    }
}
