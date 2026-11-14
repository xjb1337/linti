package com.xjb.linti.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestionImageMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LintiDatabase::class.java)

    @Test fun twelveToThirteenPreservesRowsAndCascadesImages() {
        val name = "question-images-12-13.db"
        helper.createDatabase(name, 12).use { db ->
            db.execSQL("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (1, '虚构题库', 1, 2)")
            db.execSQL("INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, confidence, needs_review, review_reasons_json, source_file_name, created_at, updated_at) VALUES (1, 1, '1', 'SINGLE', '虚构题', '[]', 'A', '', 1, 0, '[]', '虚构来源', 1, 2)")
        }
        helper.runMigrationsAndValidate(name, 13, true, MIGRATION_12_13).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.query("SELECT stem FROM questions WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("虚构题", it.getString(0)) }
            db.execSQL("INSERT INTO question_images (question_id, position, file_name, width, height) VALUES (1, 0, 'example.png', 4, 3)")
            db.execSQL("DELETE FROM questions WHERE id = 1")
            db.query("SELECT * FROM question_images").use { assertEquals(0, it.count) }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }
}
