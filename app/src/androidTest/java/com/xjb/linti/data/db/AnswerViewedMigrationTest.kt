package com.xjb.linti.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnswerViewedMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LintiDatabase::class.java)

    @Test fun sevenToNinePreservesAttemptsAndDefaultsToNotViewed() = migrateFrom(7)

    @Test fun sixToNinePreservesAttemptsAndDefaultsToNotViewed() = migrateFrom(6)

    private fun migrateFrom(version: Int) {
        val name = "answer-viewed-$version.db"
        helper.createDatabase(name, version).use { db ->
            db.execSQL("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (1, '虚构题库', 1, 2)")
            db.execSQL("INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, confidence, needs_review, review_reasons_json, source_file_name, created_at, updated_at) VALUES (1, 1, '1', 'MULTI', '虚构题', '[]', 'A', '', 1, 0, '[]', '虚构来源', 1, 2)")
            db.execSQL("INSERT INTO question_attempts (id, question_id, bank_id, mode, answer, is_correct, answered_at, submission_id) VALUES (11, 1, 1, 'SEQUENCE', 'A', 1, 100, '虚构提交一'), (12, 1, 1, 'WRONG', 'B', 0, 200, '虚构提交二')")
        }
        val migrations = if (version == 6) arrayOf(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9) else arrayOf(MIGRATION_7_8, MIGRATION_8_9)
        helper.runMigrationsAndValidate(name, 9, true, *migrations).use { db ->
            db.query("SELECT id, answer, is_correct, submission_id, answer_viewed FROM question_attempts ORDER BY id").use { c ->
                assertTrue(c.moveToNext()); assertEquals(11L, c.getLong(0)); assertEquals("A", c.getString(1))
                assertEquals(1, c.getInt(2)); assertEquals("虚构提交一", c.getString(3)); assertEquals(0, c.getInt(4))
                assertTrue(c.moveToNext()); assertEquals(12L, c.getLong(0)); assertEquals(0, c.getInt(2))
                assertEquals("虚构提交二", c.getString(3)); assertEquals(0, c.getInt(4)); assertFalse(c.moveToNext())
            }
            db.execSQL("INSERT INTO question_attempts (question_id, bank_id, mode, answer, is_correct, answered_at) VALUES (1, 1, 'TOPIC', 'A', 1, 300)")
            db.query("SELECT answer_viewed FROM question_attempts WHERE answered_at = 300").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(0, c.getInt(0))
            }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }
}
