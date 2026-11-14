package com.xjb.linti.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class FlashcardMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LintiDatabase::class.java)

    @Before fun addSchemas() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val apk = requireNotNull(System.getProperty("linti.test.schemaApk"))
        listOf(instrumentation.context.assets, instrumentation.targetContext.assets).forEach { assets ->
            check(assets.javaClass.getMethod("addAssetPath", String::class.java).invoke(assets, apk) as Int != 0)
        }
    }

    @Test fun sixToSevenPreservesQuestionsAndStartsEmpty() {
        val name = "flashcard-migration.db"
        helper.createDatabase(name, 6).use { db ->
            db.execSQL("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (1, '虚构题库', 1, 2)")
            db.execSQL("INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, confidence, needs_review, review_reasons_json, source_file_name, created_at, updated_at) VALUES (1, 1, '1', 'SINGLE', '虚构题', '[]', 'A', '', 1, 0, '[]', '虚构来源', 1, 2)")
        }
        helper.runMigrationsAndValidate(name, 7, true, MIGRATION_6_7).use { db ->
            db.setForeignKeyConstraintsEnabled(true)
            db.query("SELECT * FROM flashcard_reviews").use { assertEquals(0, it.count) }
            db.query("SELECT stem FROM questions WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("虚构题", it.getString(0)) }
            db.execSQL("INSERT INTO flashcard_reviews VALUES (1, 1, 0, 100, 10, 10, 1, 1)")
            db.execSQL("DELETE FROM questions WHERE id = 1")
            db.query("SELECT * FROM flashcard_reviews").use { assertEquals(0, it.count) }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }
}
