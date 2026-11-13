package com.xjb.linti.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        QuestionBankEntity::class,
        QuestionEntity::class,
        ImportJobEntity::class,
        FavoriteEntity::class,
        NoteEntity::class,
        WrongItemEntity::class,
        PracticeProgressEntity::class,
        QuizSessionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class LintiDatabase : RoomDatabase() {
    abstract fun questionBankDao(): QuestionBankDao
    abstract fun questionDao(): QuestionDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun noteDao(): NoteDao
    abstract fun wrongItemDao(): WrongItemDao
    abstract fun practiceProgressDao(): PracticeProgressDao
    abstract fun quizSessionDao(): QuizSessionDao

    companion object {
        fun create(context: Context): LintiDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LintiDatabase::class.java,
                "linti.db",
            ).build()
    }
}
