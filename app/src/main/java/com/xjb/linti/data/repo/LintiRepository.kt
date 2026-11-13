package com.xjb.linti.data.repo

import androidx.room.withTransaction
import com.xjb.linti.data.backup.BackupCodec
import com.xjb.linti.data.backup.BackupPayload
import com.xjb.linti.data.db.BankStatsRow
import com.xjb.linti.data.db.FavoriteEntity
import com.xjb.linti.data.db.ImportJobEntity
import com.xjb.linti.data.db.JsonCodec
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.db.NoteEntity
import com.xjb.linti.data.db.PracticeProgressEntity
import com.xjb.linti.data.db.QuizSessionEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.db.WrongItemEntity
import com.xjb.linti.data.db.questionTypeFromStorage
import com.xjb.linti.data.db.toDomain
import com.xjb.linti.data.db.toEntity
import com.xjb.linti.data.import.ImportPersistence
import com.xjb.linti.data.sample.SampleContent
import com.xjb.linti.domain.BankSummary
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.FavoriteQuestionItem
import com.xjb.linti.domain.GradingResult
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.NoteQuestionItem
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuizQuestion
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
        progressDao.upsert(
            PracticeProgressEntity(
                bankId = bankId,
                mode = PracticeMode.SEQUENCE.name,
                currentIndex = currentIndex.coerceAtLeast(0),
                answeredCount = answeredCount.coerceAtLeast(0),
                lastOpenedAt = now,
            ),
        )
    }

    suspend fun getSequenceProgress(bankId: Long): PracticeProgressEntity? =
        progressDao.get(bankId, PracticeMode.SEQUENCE.name)

    suspend fun saveSession(snapshot: SessionSnapshot): Long {
        val questionIds = snapshot.questions.map { it.questionId }
        val entity = QuizSessionEntity(
            bankId = snapshot.bankId,
            bankName = snapshot.bankName,
            mode = snapshot.mode.name,
            configJson = JsonCodec.encodeExamConfig(snapshot.config),
            questionIdsJson = JsonCodec.encodeLongList(questionIds),
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
        return sessionDao.insert(entity)
    }

    suspend fun getRecentCompletedSessions(limit: Int = 100): List<QuizSessionEntity> =
        sessionDao.getRecentCompleted(limit)

    suspend fun getAllSessions(): List<QuizSessionEntity> = sessionDao.getAll()

    suspend fun getAnsweredQuestionIds(bankId: Long): Set<Long> =
        sessionDao.getCompletedForBank(bankId)
            .flatMap { JsonCodec.decodeAnswers(it.answersJson).keys }
            .toSet()

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
                ),
            )
        }

    suspend fun restoreBackupJson(json: String) {
        database.withTransaction {
            val payload = BackupCodec.decode(json)
            sessionDao.deleteAll()
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
            if (payload.progress.isNotEmpty()) progressDao.insertAll(payload.progress)
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
