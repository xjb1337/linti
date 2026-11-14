package com.xjb.linti.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiQuestionMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LintiDatabase::class.java)
    @Test fun tenToElevenPreservesOldQuestionsAndCascadesDrafts() {
        val name = "ai-questions-10-11.db"
        helper.createDatabase(name, 10).use { db ->
            db.execSQL("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (1, '虚构题库', 1, 2)")
            db.execSQL("INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, confidence, needs_review, review_reasons_json, source_file_name, created_at, updated_at) VALUES (1, 1, '1', 'SINGLE', '虚构题', '[]', 'A', '解析', 1, 0, '[]', '虚构来源', 1, 2)")
        }
        helper.runMigrationsAndValidate(name, 11, true, MIGRATION_10_11).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.query("SELECT origin FROM questions WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("IMPORTED", it.getString(0)) }
            db.execSQL("INSERT INTO ai_question_drafts (bank_id, payload, problems_json, created_at) VALUES (1, '{}', '[]', 3)")
            assertTrue(runCatching { db.execSQL("INSERT INTO ai_question_drafts (bank_id, payload, problems_json, created_at) VALUES (999, '{}', '[]', 3)") }.isFailure)
            db.execSQL("DELETE FROM question_banks WHERE id = 1")
            db.query("SELECT COUNT(*) FROM ai_question_drafts").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }
}
