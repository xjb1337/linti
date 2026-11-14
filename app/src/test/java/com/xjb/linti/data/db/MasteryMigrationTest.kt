package com.xjb.linti.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class MasteryMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LintiDatabase::class.java)

    @org.junit.Before fun addSchemasToRobolectricAssets() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val apk = requireNotNull(System.getProperty("linti.test.schemaApk"))
        listOf(instrumentation.context.assets, instrumentation.targetContext.assets).forEach { assets ->
            val cookie = assets.javaClass.getMethod("addAssetPath", String::class.java).invoke(assets, apk) as Int
            check(cookie != 0)
        }
    }

    @Test fun migrateFiveToSixPreservesHistoryAndReopensOldCorrectedItems() {
        val name = "mastery-migration.db"
        helper.createDatabase(name, 5).use { db ->
            db.execSQL("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (1, '虚构题库', 1, 2)")
            for (id in 1..2) db.execSQL("INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, confidence, needs_review, review_reasons_json, source_file_name, created_at, updated_at) VALUES ($id, 1, '$id', 'SINGLE', '虚构题', '[]', 'A', '', 1, 0, '[]', '虚构来源', 1, 2)")
            db.execSQL("INSERT INTO wrong_items (question_id, bank_id, wrong_count, last_wrong_at, corrected, pending_review) VALUES (1, 1, 4, 123, 1, 0), (2, 1, 7, 456, 0, 1)")
            db.execSQL("INSERT INTO question_attempts (id, question_id, bank_id, mode, answer, is_correct, answered_at, session_id) VALUES (11, 1, 1, 'WRONG', 'B', 0, 100, NULL), (12, 1, 1, 'WRONG', 'A', 1, 200, NULL)")
        }
        helper.runMigrationsAndValidate(name, 6, true, MIGRATION_5_6).use { db ->
            db.query("SELECT question_id, wrong_count, last_wrong_at, corrected, pending_review, correct_streak, mastered_at FROM wrong_items ORDER BY question_id").use { c ->
                assertTrue(c.moveToFirst()); assertEquals(1, c.getInt(0)); assertEquals(4, c.getInt(1)); assertEquals(123, c.getInt(2)); assertEquals(0, c.getInt(3)); assertEquals(1, c.getInt(4)); assertEquals(0, c.getInt(5)); assertTrue(c.isNull(6))
                assertTrue(c.moveToNext()); assertEquals(2, c.getInt(0)); assertEquals(7, c.getInt(1)); assertEquals(456, c.getInt(2)); assertEquals(0, c.getInt(3)); assertEquals(1, c.getInt(4)); assertEquals(0, c.getInt(5)); assertTrue(c.isNull(6)); assertFalse(c.moveToNext())
            }
            db.query("SELECT id, answer, is_correct, answered_at, submission_id FROM question_attempts ORDER BY id").use { c ->
                assertEquals(2, c.count); assertTrue(c.moveToFirst()); assertEquals(11, c.getInt(0)); assertEquals("B", c.getString(1)); assertEquals(0, c.getInt(2)); assertEquals(100, c.getInt(3)); assertTrue(c.isNull(4))
                assertTrue(c.moveToNext()); assertEquals(12, c.getInt(0)); assertEquals("A", c.getString(1)); assertEquals(1, c.getInt(2)); assertEquals(200, c.getInt(3)); assertTrue(c.isNull(4))
            }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }
}
