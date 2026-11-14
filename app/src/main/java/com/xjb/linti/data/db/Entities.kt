package com.xjb.linti.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "question_banks")
data class QuestionBankEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val note: String? = null,
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(entity = QuestionBankEntity::class, parentColumns = ["id"], childColumns = ["bank_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bank_id"), Index(value = ["bank_id", "name"], unique = true)],
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bank_id") val bankId: Long,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bank_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapter_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [
        Index("bank_id"),
        Index("chapter_id"),
        Index("needs_review"),
    ],
)
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bank_id") val bankId: Long,
    @ColumnInfo(name = "source_number") val sourceNumber: String,
    val type: String,
    val stem: String,
    @ColumnInfo(name = "options_json") val optionsJson: String,
    val answer: String,
    val explanation: String,
    val tags: String? = null,
    @ColumnInfo(name = "knowledge_point") val knowledgePoint: String? = null,
    val confidence: Double = 1.0,
    @ColumnInfo(name = "needs_review") val needsReview: Boolean = false,
    @ColumnInfo(name = "review_reasons_json") val reviewReasonsJson: String = "[]",
    @ColumnInfo(name = "source_file_name") val sourceFileName: String,
    @ColumnInfo(name = "raw_snippet") val rawSnippet: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "chapter_id") val chapterId: Long? = null,
    @ColumnInfo(defaultValue = "'IMPORTED'") val origin: String = "IMPORTED",
)

@Entity(
    tableName = "ai_question_drafts",
    foreignKeys = [ForeignKey(entity = QuestionBankEntity::class, parentColumns = ["id"], childColumns = ["bank_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bank_id")],
)
data class AiQuestionDraftEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bank_id") val bankId: Long,
    val payload: String,
    @ColumnInfo(name = "problems_json") val problemsJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "ai_explanations",
    foreignKeys = [ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = ForeignKey.CASCADE)],
)
data class AiExplanationEntity(
    @PrimaryKey @ColumnInfo(name = "question_id") val questionId: Long,
    val content: String,
    val model: String,
    val provider: String,
    @ColumnInfo(name = "question_updated_at") val questionUpdatedAt: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "past_papers",
    foreignKeys = [ForeignKey(entity = QuestionBankEntity::class, parentColumns = ["id"], childColumns = ["bank_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bank_id")],
)
data class PastPaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bank_id") val bankId: Long,
    val year: Int,
    val name: String,
    @ColumnInfo(name = "source_category", defaultValue = "'UNVERIFIED'") val sourceCategory: String = "UNVERIFIED",
    @ColumnInfo(name = "source_note") val sourceNote: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "past_paper_items",
    primaryKeys = ["paper_id", "question_id"],
    foreignKeys = [
        ForeignKey(entity = PastPaperEntity::class, parentColumns = ["id"], childColumns = ["paper_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["paper_id", "original_number"], unique = true), Index("question_id")],
)
data class PastPaperItemEntity(
    @ColumnInfo(name = "paper_id") val paperId: Long,
    @ColumnInfo(name = "question_id") val questionId: Long,
    val position: Int,
    @ColumnInfo(name = "original_number") val originalNumber: String,
)

@Entity(
    tableName = "import_jobs",
    foreignKeys = [
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["target_bank_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("target_bank_id"),
        Index("created_at"),
    ],
)
data class ImportJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "file_name") val fileName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    val kind: String,
    val method: String,
    val status: String,
    @ColumnInfo(name = "recognized_count") val recognizedCount: Int,
    @ColumnInfo(name = "confirmed_count") val confirmedCount: Int,
    @ColumnInfo(name = "pending_count") val pendingCount: Int,
    @ColumnInfo(name = "failed_count") val failedCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "target_bank_id") val targetBankId: Long? = null,
    @ColumnInfo(name = "warnings_json") val warningsJson: String = "[]",
)

@Entity(
    tableName = "favorites",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("question_id")],
)
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "question_id") val questionId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("question_id")],
)
data class NoteEntity(
    @PrimaryKey @ColumnInfo(name = "question_id") val questionId: Long,
    val content: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "wrong_items",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bank_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("bank_id"),
        Index("pending_review"),
        Index("last_wrong_at"),
    ],
)
data class WrongItemEntity(
    @PrimaryKey @ColumnInfo(name = "question_id") val questionId: Long,
    @ColumnInfo(name = "bank_id") val bankId: Long,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int,
    @ColumnInfo(name = "last_wrong_at") val lastWrongAt: Long,
    val corrected: Boolean,
    @ColumnInfo(name = "pending_review") val pendingReview: Boolean,
    @ColumnInfo(name = "correct_streak", defaultValue = "0") val correctStreak: Int = 0,
    @ColumnInfo(name = "mastered_at") val masteredAt: Long? = null,
)

@Entity(
    tableName = "flashcard_reviews",
    foreignKeys = [ForeignKey(entity = QuestionEntity::class, parentColumns = ["id"], childColumns = ["question_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("bank_id"), Index("due_at")],
)
data class FlashcardReviewEntity(
    @PrimaryKey @ColumnInfo(name = "question_id") val questionId: Long,
    @ColumnInfo(name = "bank_id") val bankId: Long,
    val step: Int,
    @ColumnInfo(name = "due_at") val dueAt: Long,
    @ColumnInfo(name = "first_reviewed_at") val firstReviewedAt: Long,
    @ColumnInfo(name = "last_reviewed_at") val lastReviewedAt: Long,
    @ColumnInfo(name = "review_count") val reviewCount: Int,
    @ColumnInfo(name = "lapse_count") val lapseCount: Int,
)

@Entity(
    tableName = "practice_progress",
    primaryKeys = ["bank_id", "mode"],
    foreignKeys = [
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bank_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("bank_id")],
)
data class PracticeProgressEntity(
    @ColumnInfo(name = "bank_id") val bankId: Long,
    val mode: String,
    @ColumnInfo(name = "current_index") val currentIndex: Int,
    @ColumnInfo(name = "answered_count") val answeredCount: Int,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long,
    @ColumnInfo(name = "round_started_at", defaultValue = "0") val roundStartedAt: Long = 0,
)

@Entity(
    tableName = "quiz_sessions",
    foreignKeys = [
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bank_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("bank_id"),
        Index("started_at"),
    ],
)
data class QuizSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bank_id") val bankId: Long?,
    @ColumnInfo(name = "bank_name") val bankName: String,
    val mode: String,
    @ColumnInfo(name = "config_json") val configJson: String,
    @ColumnInfo(name = "question_ids_json") val questionIdsJson: String,
    @ColumnInfo(name = "answers_json") val answersJson: String,
    @ColumnInfo(name = "flags_json") val flagsJson: String,
    @ColumnInfo(name = "bookmarks_json") val bookmarksJson: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
    val score: Double? = null,
    val passed: Boolean? = null,
    @ColumnInfo(name = "stats_json") val statsJson: String? = null,
    val status: String,
    @ColumnInfo(name = "answered_count") val answeredCount: Int = 0,
    @ColumnInfo(name = "correct_count") val correctCount: Int = 0,
    @ColumnInfo(name = "wrong_count") val wrongCount: Int = 0,
    @ColumnInfo(name = "unanswered_count") val unansweredCount: Int = 0,
)

@Entity(
    tableName = "exam_drafts",
    foreignKeys = [
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bank_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bank_id"], unique = true)],
)
data class ExamDraftEntity(
    @PrimaryKey @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "bank_id") val bankId: Long,
    val revision: Long,
)

@Entity(
    tableName = "exam_draft_chunks",
    primaryKeys = ["draft_id", "kind", "ordinal", "part"],
    foreignKeys = [
        ForeignKey(
            entity = ExamDraftEntity::class,
            parentColumns = ["draft_id"],
            childColumns = ["draft_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExamDraftChunkEntity(
    @ColumnInfo(name = "draft_id") val draftId: String,
    val kind: String,
    val ordinal: Int,
    val part: Int,
    val content: String,
)

data class ExamDraftSummaryRow(
    @ColumnInfo(name = "draft_id") val draftId: String,
    @ColumnInfo(name = "bank_id") val bankId: Long,
)

// 已交卷模考的原卷快照：只关联 quiz_sessions，删除会话时级联清理；
// 不关联 questions 或题库，改题、删题、删题库后原卷仍可回看。
@Entity(
    tableName = "exam_papers",
    foreignKeys = [
        ForeignKey(
            entity = QuizSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExamPaperEntity(
    @PrimaryKey @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "format_version") val formatVersion: Int,
    @ColumnInfo(name = "question_count") val questionCount: Int,
)

// 原卷内容按题分块存储，长题干/长解析沿用考试草稿的分块方案；
// 历史列表只读 exam_papers 头行，不加载全文。
@Entity(
    tableName = "exam_paper_chunks",
    primaryKeys = ["session_id", "kind", "ordinal", "part"],
    foreignKeys = [
        ForeignKey(
            entity = ExamPaperEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ExamPaperChunkEntity(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    val kind: String,
    val ordinal: Int,
    val part: Int,
    val content: String,
)

@Entity(
    tableName = "question_attempts",
    foreignKeys = [
        ForeignKey(
            entity = QuestionEntity::class,
            parentColumns = ["id"],
            childColumns = ["question_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = QuestionBankEntity::class,
            parentColumns = ["id"],
            childColumns = ["bank_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("question_id"),
        Index("bank_id"),
        Index("answered_at"),
        Index(value = ["submission_id"], unique = true),
    ],
)
data class QuestionAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "question_id") val questionId: Long,
    @ColumnInfo(name = "bank_id") val bankId: Long,
    val mode: String,
    val answer: String,
    @ColumnInfo(name = "is_correct") val isCorrect: Boolean,
    @ColumnInfo(name = "answered_at") val answeredAt: Long,
    @ColumnInfo(name = "session_id") val sessionId: Long? = null,
    @ColumnInfo(name = "submission_id") val submissionId: String? = null,
    @ColumnInfo(name = "answer_viewed", defaultValue = "0") val answerViewed: Boolean = false,
)
