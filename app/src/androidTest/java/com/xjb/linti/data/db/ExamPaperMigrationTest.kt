package com.xjb.linti.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamPaperMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LintiDatabase::class.java,
    )

    @Test
    fun migration3To4KeepsSessionsAndCreatesEmptyPaperTables() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (1, '旧题库', 1, 1)")
            db.execSQL(
                "INSERT INTO quiz_sessions (bank_id, bank_name, mode, config_json, question_ids_json, answers_json, flags_json, bookmarks_json, started_at, score, status, answered_count, correct_count, wrong_count, unanswered_count) " +
                    "VALUES (1, '旧题库', 'EXAM', '{}', '[]', '{}', '[]', '[]', 1, 2.0, 'COMPLETED', 2, 1, 1, 0)",
            )
        }
        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        db.use {
            db.query("SELECT COUNT(*) FROM quiz_sessions").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            // 迁移不从题库或错题本回填旧试卷：旧场次只有成绩
            db.query("SELECT COUNT(*) FROM exam_papers").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
            db.query("SELECT COUNT(*) FROM exam_paper_chunks").use { cursor ->
                cursor.moveToFirst()
                assertEquals(0, cursor.getInt(0))
            }
        }
    }

    private companion object {
        const val TEST_DB = "linti-migration-test.db"
    }
}
