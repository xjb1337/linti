package com.xjb.linti.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuestionBankEntity::class,
        ChapterEntity::class,
        QuestionEntity::class,
        PastPaperEntity::class,
        PastPaperItemEntity::class,
        ImportJobEntity::class,
        FavoriteEntity::class,
        NoteEntity::class,
        WrongItemEntity::class,
        FlashcardReviewEntity::class,
        PracticeProgressEntity::class,
        QuizSessionEntity::class,
        QuestionAttemptEntity::class,
        ExamDraftEntity::class,
        ExamDraftChunkEntity::class,
        ExamPaperEntity::class,
        ExamPaperChunkEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class LintiDatabase : RoomDatabase() {
    abstract fun questionBankDao(): QuestionBankDao
    abstract fun chapterDao(): ChapterDao
    abstract fun questionDao(): QuestionDao
    abstract fun pastPaperDao(): PastPaperDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun noteDao(): NoteDao
    abstract fun wrongItemDao(): WrongItemDao
    abstract fun flashcardReviewDao(): FlashcardReviewDao
    abstract fun practiceProgressDao(): PracticeProgressDao
    abstract fun quizSessionDao(): QuizSessionDao
    abstract fun questionAttemptDao(): QuestionAttemptDao
    abstract fun examDraftDao(): ExamDraftDao
    abstract fun examPaperDao(): ExamPaperDao

    companion object {
        fun create(context: Context): LintiDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LintiDatabase::class.java,
                "linti.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9).build()
    }
}
