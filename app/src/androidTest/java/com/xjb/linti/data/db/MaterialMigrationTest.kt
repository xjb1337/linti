package com.xjb.linti.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LintiDatabase::class.java)
    @Test fun elevenToTwelvePreservesQuestions() {
        val name = "material-11-12.db"
        helper.createDatabase(name, 11).use { db ->
            db.execSQL("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (1, '虚构题库', 1, 2)")
            db.execSQL("INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, confidence, needs_review, review_reasons_json, source_file_name, created_at, updated_at) VALUES (1, 1, '1', 'FILL', '虚构题', '[]', '甲', '解析', 1, 0, '[]', '虚构来源', 1, 2)")
        }
        helper.runMigrationsAndValidate(name, 12, true, MIGRATION_11_12).use { db ->
            db.query("SELECT answer, origin, source_excerpt, accepted_answers_json FROM questions WHERE id = 1").use {
                assertTrue(it.moveToFirst()); assertEquals("甲", it.getString(0)); assertEquals("IMPORTED", it.getString(1))
                assertTrue(it.isNull(2)); assertEquals("[]", it.getString(3))
            }
        }
    }
}
