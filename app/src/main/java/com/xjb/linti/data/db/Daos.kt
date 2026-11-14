package com.xjb.linti.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionBankDao {
    @Insert
    suspend fun insert(bank: QuestionBankEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(banks: List<QuestionBankEntity>)

    @Update
    suspend fun update(bank: QuestionBankEntity)

    @Query("SELECT * FROM question_banks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QuestionBankEntity?

    @Query("SELECT * FROM question_banks ORDER BY updated_at DESC, id DESC")
    suspend fun getAll(): List<QuestionBankEntity>

    @Query("SELECT * FROM question_banks ORDER BY updated_at DESC, id DESC")
    fun observeAll(): Flow<List<QuestionBankEntity>>

    @Query(
        """
        SELECT
            b.id AS id,
            b.name AS name,
            b.note AS note,
            b.created_at AS createdAt,
            b.updated_at AS updatedAt,
            (SELECT COUNT(*) FROM questions q WHERE q.bank_id = b.id) AS questionCount,
            (SELECT COUNT(*) FROM wrong_items w WHERE w.bank_id = b.id) AS wrongCount,
            (SELECT COUNT(*) FROM wrong_items w WHERE w.bank_id = b.id AND w.pending_review = 1) AS pendingWrongCount,
            (SELECT COUNT(*) FROM wrong_items w WHERE w.bank_id = b.id AND w.corrected = 1) AS correctedWrongCount,
            (SELECT COUNT(*) FROM favorites f INNER JOIN questions q ON q.id = f.question_id WHERE q.bank_id = b.id) AS favoriteCount,
            (SELECT COUNT(*) FROM notes n INNER JOIN questions q ON q.id = n.question_id WHERE q.bank_id = b.id) AS noteCount,
            COALESCE((SELECT p.current_index FROM practice_progress p WHERE p.bank_id = b.id AND p.mode = 'SEQUENCE'), 0) AS sequenceIndex,
            COALESCE((SELECT p.answered_count FROM practice_progress p WHERE p.bank_id = b.id AND p.mode = 'SEQUENCE'), 0) AS sequenceAnsweredCount,
            (SELECT p.last_opened_at FROM practice_progress p WHERE p.bank_id = b.id AND p.mode = 'SEQUENCE') AS lastOpenedAt
        FROM question_banks b
        ORDER BY b.updated_at DESC, b.id DESC
        """,
    )
    fun observeBankStats(): Flow<List<BankStatsRow>>

    @Query("UPDATE question_banks SET updated_at = :timestamp WHERE id = :bankId")
    suspend fun touch(bankId: Long, timestamp: Long)

    @Query("SELECT COUNT(*) FROM question_banks")
    suspend fun count(): Int

    @Query("DELETE FROM question_banks")
    suspend fun deleteAll()
}

@Dao
interface ChapterDao {
    @Insert suspend fun insert(chapter: ChapterEntity): Long
    @Update suspend fun update(chapter: ChapterEntity)
    @Query("SELECT * FROM chapters WHERE id = :id") suspend fun getById(id: Long): ChapterEntity?
    @Query("SELECT * FROM chapters WHERE bank_id = :bankId ORDER BY sort_order, id") suspend fun getByBank(bankId: Long): List<ChapterEntity>
    @Query("SELECT * FROM chapters ORDER BY bank_id, sort_order, id") suspend fun getAll(): List<ChapterEntity>
    @Query("SELECT * FROM chapters WHERE bank_id = :bankId ORDER BY sort_order, id") fun observeByBank(bankId: Long): Flow<List<ChapterEntity>>
    @Query("DELETE FROM chapters WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface QuestionDao {
    @Insert
    suspend fun insert(question: QuestionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>): List<Long>

    @Update
    suspend fun update(question: QuestionEntity)

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QuestionEntity?

    @Query("SELECT * FROM questions WHERE bank_id = :bankId ORDER BY id ASC")
    suspend fun getByBank(bankId: Long): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE bank_id = :bankId ORDER BY id ASC")
    fun observeByBank(bankId: Long): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY id ASC")
    suspend fun getAll(): List<QuestionEntity>

    @Query(
        """
        SELECT * FROM questions
        WHERE bank_id = :bankId
          AND (
            stem LIKE '%' || :query || '%'
            OR answer LIKE '%' || :query || '%'
            OR explanation LIKE '%' || :query || '%'
            OR options_json LIKE '%' || :query || '%'
            OR COALESCE(tags, '') LIKE '%' || :query || '%'
            OR COALESCE(knowledge_point, '') LIKE '%' || :query || '%'
            OR source_number LIKE '%' || :query || '%'
          )
        ORDER BY id ASC
        """,
    )
    suspend fun searchInBank(bankId: Long, query: String): List<QuestionEntity>

    @Query(
        """
        SELECT * FROM questions
        WHERE stem LIKE '%' || :query || '%'
           OR answer LIKE '%' || :query || '%'
           OR explanation LIKE '%' || :query || '%'
           OR options_json LIKE '%' || :query || '%'
           OR COALESCE(tags, '') LIKE '%' || :query || '%'
           OR COALESCE(knowledge_point, '') LIKE '%' || :query || '%'
           OR source_number LIKE '%' || :query || '%'
        ORDER BY bank_id ASC, id ASC
        """,
    )
    suspend fun searchAll(query: String): List<QuestionEntity>

    @Query("UPDATE questions SET chapter_id = :chapterId, updated_at = :updatedAt WHERE id IN (:ids) AND bank_id = :bankId")
    suspend fun moveToChapter(ids: List<Long>, bankId: Long, chapterId: Long?, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM questions WHERE bank_id = :bankId")
    suspend fun countByBank(bankId: Long): Int

    @Query("DELETE FROM questions WHERE bank_id = :bankId")
    suspend fun deleteByBank(bankId: Long)

    @Query("DELETE FROM questions")
    suspend fun deleteAll()
}

@Dao
interface ImportJobDao {
    @Insert
    suspend fun insert(job: ImportJobEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(jobs: List<ImportJobEntity>)

    @Update
    suspend fun update(job: ImportJobEntity)

    @Query("SELECT * FROM import_jobs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ImportJobEntity?

    @Query("SELECT * FROM import_jobs ORDER BY created_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<ImportJobEntity>>

    @Query("SELECT * FROM import_jobs ORDER BY created_at DESC, id DESC")
    suspend fun getAll(): List<ImportJobEntity>

    @Query("DELETE FROM import_jobs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM import_jobs")
    suspend fun deleteAll()
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE question_id = :questionId")
    suspend fun delete(questionId: Long)

    @Query("SELECT * FROM favorites WHERE question_id = :questionId LIMIT 1")
    suspend fun get(questionId: Long): FavoriteEntity?

    @Query("SELECT COUNT(*) FROM favorites WHERE question_id = :questionId")
    suspend fun count(questionId: Long): Int

    @Query(
        """
        SELECT
            f.question_id AS questionId,
            q.bank_id AS bankId,
            b.name AS bankName,
            q.type AS type,
            q.stem AS stem,
            q.answer AS answer,
            f.created_at AS createdAt
        FROM favorites f
        INNER JOIN questions q ON q.id = f.question_id
        INNER JOIN question_banks b ON b.id = q.bank_id
        ORDER BY f.created_at DESC
        """,
    )
    fun observeRows(): Flow<List<FavoriteQuestionRow>>

    @Query("SELECT * FROM favorites")
    suspend fun getAll(): List<FavoriteEntity>

    @Query(
        """
        SELECT f.question_id FROM favorites f
        INNER JOIN questions q ON q.id = f.question_id
        WHERE q.bank_id = :bankId
        """,
    )
    suspend fun getIdsForBank(bankId: Long): List<Long>

    @Query("SELECT question_id FROM favorites")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()
}

@Dao
interface NoteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<NoteEntity>)

    @Query("SELECT * FROM notes WHERE question_id = :questionId LIMIT 1")
    suspend fun get(questionId: Long): NoteEntity?

    @Query("DELETE FROM notes WHERE question_id = :questionId")
    suspend fun delete(questionId: Long)

    @Query(
        """
        SELECT
            n.question_id AS questionId,
            q.bank_id AS bankId,
            b.name AS bankName,
            q.type AS type,
            q.stem AS stem,
            n.content AS content,
            n.created_at AS createdAt,
            n.updated_at AS updatedAt
        FROM notes n
        INNER JOIN questions q ON q.id = n.question_id
        INNER JOIN question_banks b ON b.id = q.bank_id
        ORDER BY n.updated_at DESC
        """,
    )
    fun observeRows(): Flow<List<NoteQuestionRow>>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}

@Dao
interface WrongItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WrongItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WrongItemEntity>)

    @Query("SELECT * FROM wrong_items WHERE question_id = :questionId LIMIT 1")
    suspend fun getByQuestion(questionId: Long): WrongItemEntity?

    @Query(
        """
        SELECT
            w.question_id AS questionId,
            w.bank_id AS bankId,
            b.name AS bankName,
            w.wrong_count AS wrongCount,
            w.last_wrong_at AS lastWrongAt,
            w.corrected AS corrected,
            w.pending_review AS pendingReview,
            w.correct_streak AS correctStreak,
            w.mastered_at AS masteredAt,
            q.source_number AS sourceNumber,
            q.type AS type,
            q.stem AS stem
        FROM wrong_items w
        INNER JOIN questions q ON q.id = w.question_id
        INNER JOIN question_banks b ON b.id = w.bank_id
        ORDER BY w.pending_review DESC, w.last_wrong_at DESC
        """,
    )
    fun observeRows(): Flow<List<WrongQuestionRow>>

    @Query(
        """
        SELECT
            w.question_id AS questionId,
            w.bank_id AS bankId,
            b.name AS bankName,
            w.wrong_count AS wrongCount,
            w.last_wrong_at AS lastWrongAt,
            w.corrected AS corrected,
            w.pending_review AS pendingReview,
            w.correct_streak AS correctStreak,
            w.mastered_at AS masteredAt,
            q.source_number AS sourceNumber,
            q.type AS type,
            q.stem AS stem
        FROM wrong_items w
        INNER JOIN questions q ON q.id = w.question_id
        INNER JOIN question_banks b ON b.id = w.bank_id
        ORDER BY w.pending_review DESC, w.last_wrong_at DESC
        """,
    )
    suspend fun getRows(): List<WrongQuestionRow>

    @Query("SELECT * FROM wrong_items")
    suspend fun getAll(): List<WrongItemEntity>

    @Query("DELETE FROM wrong_items")
    suspend fun deleteAll()
}

@Dao
interface PracticeProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: PracticeProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(progress: List<PracticeProgressEntity>)

    @Query("SELECT * FROM practice_progress WHERE bank_id = :bankId AND mode = :mode LIMIT 1")
    suspend fun get(bankId: Long, mode: String): PracticeProgressEntity?

    @Query("SELECT * FROM practice_progress")
    suspend fun getAll(): List<PracticeProgressEntity>

    @Query("DELETE FROM practice_progress")
    suspend fun deleteAll()
}

@Dao
interface QuizSessionDao {
    @Insert
    suspend fun insert(session: QuizSessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<QuizSessionEntity>)

    @Update
    suspend fun update(session: QuizSessionEntity)

    @Query("SELECT * FROM quiz_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QuizSessionEntity?

    @Query("SELECT * FROM quiz_sessions ORDER BY started_at DESC, id DESC")
    suspend fun getAll(): List<QuizSessionEntity>

    @Query(
        """
        SELECT * FROM quiz_sessions
        WHERE bank_id = :bankId AND status = 'COMPLETED'
        ORDER BY ended_at DESC, id DESC
        """,
    )
    suspend fun getCompletedForBank(bankId: Long): List<QuizSessionEntity>

    @Query(
        """
        SELECT * FROM quiz_sessions
        WHERE mode = 'EXAM' AND status = 'COMPLETED'
        ORDER BY ended_at DESC, id DESC
        """,
    )
    suspend fun getCompletedExamSessions(): List<QuizSessionEntity>

    @Query(
        """
        SELECT * FROM quiz_sessions
        WHERE status = 'COMPLETED'
        ORDER BY ended_at DESC, id DESC
        LIMIT :limit
        """,
    )
    suspend fun getRecentCompleted(limit: Int): List<QuizSessionEntity>

    @Query("DELETE FROM quiz_sessions")
    suspend fun deleteAll()
}

@Dao
interface ExamDraftDao {
    @Insert
    suspend fun insert(draft: ExamDraftEntity)

    @Insert
    suspend fun insertChunks(chunks: List<ExamDraftChunkEntity>)

    @Query("SELECT * FROM exam_drafts WHERE bank_id = :bankId LIMIT 1")
    suspend fun getByBank(bankId: Long): ExamDraftEntity?

    @Query("SELECT draft_id, bank_id FROM exam_drafts ORDER BY bank_id")
    fun observeAll(): Flow<List<ExamDraftSummaryRow>>

    @Query("UPDATE exam_drafts SET revision = :revision WHERE draft_id = :draftId AND revision < :revision")
    suspend fun updateIfNewer(draftId: String, revision: Long): Int

    @Query("SELECT * FROM exam_draft_chunks WHERE draft_id = :draftId ORDER BY kind, ordinal, part")
    suspend fun getChunks(draftId: String): List<ExamDraftChunkEntity>

    @Query("DELETE FROM exam_draft_chunks WHERE draft_id = :draftId AND kind = 'progress'")
    suspend fun deleteProgress(draftId: String)

    @Query("DELETE FROM exam_drafts WHERE draft_id = :draftId")
    suspend fun deleteById(draftId: String): Int

    @Query("DELETE FROM exam_drafts")
    suspend fun deleteAll()
}

@Dao
interface ExamPaperDao {
    @Insert
    suspend fun insert(paper: ExamPaperEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(papers: List<ExamPaperEntity>)

    @Insert
    suspend fun insertChunks(chunks: List<ExamPaperChunkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllChunks(chunks: List<ExamPaperChunkEntity>)

    @Query("SELECT * FROM exam_papers WHERE session_id = :sessionId LIMIT 1")
    suspend fun getBySession(sessionId: Long): ExamPaperEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM exam_papers WHERE session_id = :sessionId)")
    suspend fun hasPaper(sessionId: Long): Boolean

    @Query("SELECT * FROM exam_paper_chunks WHERE session_id = :sessionId ORDER BY kind, ordinal, part")
    suspend fun getChunks(sessionId: Long): List<ExamPaperChunkEntity>

    @Query("SELECT * FROM exam_papers ORDER BY session_id")
    suspend fun getAll(): List<ExamPaperEntity>

    @Query("SELECT * FROM exam_paper_chunks ORDER BY session_id, kind, ordinal, part")
    suspend fun getAllChunks(): List<ExamPaperChunkEntity>

    @Query("DELETE FROM exam_papers")
    suspend fun deleteAll()
}

@Dao
interface QuestionAttemptDao {
    @Insert
    suspend fun insert(attempt: QuestionAttemptEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfNew(attempt: QuestionAttemptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(attempts: List<QuestionAttemptEntity>)

    @Query("SELECT * FROM question_attempts ORDER BY answered_at DESC, id DESC")
    suspend fun getAll(): List<QuestionAttemptEntity>

    @Query(
        """
        SELECT * FROM question_attempts
        WHERE bank_id = :bankId AND mode = 'SEQUENCE' AND answered_at >= :since
        ORDER BY answered_at DESC, id DESC
        """,
    )
    suspend fun sequenceAttemptsSince(bankId: Long, since: Long): List<QuestionAttemptEntity>

    @Query("SELECT DISTINCT question_id FROM question_attempts WHERE bank_id = :bankId")
    suspend fun answeredQuestionIds(bankId: Long): List<Long>

    @Query(
        """
        SELECT COUNT(DISTINCT a.question_id) FROM question_attempts a
        INNER JOIN questions q ON q.id = a.question_id
        WHERE a.bank_id = :bankId AND a.mode = 'SEQUENCE' AND a.answered_at >= :since
        """,
    )
    suspend fun countSequenceAnswered(bankId: Long, since: Long): Int

    @Query(
        """
        SELECT question_id AS questionId,
               COUNT(*) AS totalCount,
               SUM(CASE WHEN is_correct = 1 THEN 1 ELSE 0 END) AS correctCount
        FROM question_attempts
        WHERE question_id IN (:questionIds)
        GROUP BY question_id
        """,
    )
    suspend fun statsFor(questionIds: List<Long>): List<AttemptStatRow>

    @Query("DELETE FROM question_attempts")
    suspend fun deleteAll()
}
