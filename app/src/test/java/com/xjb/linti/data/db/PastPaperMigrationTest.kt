package com.xjb.linti.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Before
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class PastPaperMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LintiDatabase::class.java)

    @Before fun addSchemas() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val apk = requireNotNull(System.getProperty("linti.test.schemaApk"))
        listOf(instrumentation.context.assets, instrumentation.targetContext.assets).forEach { assets ->
            check(assets.javaClass.getMethod("addAssetPath", String::class.java).invoke(assets, apk) as Int != 0)
        }
    }

    @Test fun eightToNinePreservesDataAndCreatesEmptyTablesWithConstraints() {
        val name = "past-papers-8-9.db"
        helper.createDatabase(name, 8).use { db ->
            db.execSQL("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (1, '虚构官方题库', 1, 2)")
            db.execSQL("INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, confidence, needs_review, review_reasons_json, source_file_name, created_at, updated_at) VALUES (1, 1, '1', 'SINGLE', '虚构题', '[]', 'A', '', 1, 0, '[]', '虚构官方发布.pdf', 1, 2)")
        }
        helper.runMigrationsAndValidate(name, 12, true, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.query("SELECT stem FROM questions WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("虚构题", it.getString(0)) }
            db.query("SELECT COUNT(*) FROM past_papers").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM past_paper_items").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.execSQL("INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, confidence, needs_review, review_reasons_json, source_file_name, created_at, updated_at) VALUES (2, 1, '2', 'SINGLE', '虚构题二', '[]', 'A', '', 1, 0, '[]', '虚构来源', 1, 2)")
            db.execSQL("INSERT INTO past_papers (id, bank_id, year, name, created_at, updated_at) VALUES (1, 1, 2024, '虚构甲卷', 1, 2), (2, 1, 2023, '虚构乙卷', 1, 2)")
            db.query("SELECT source_category FROM past_papers WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("UNVERIFIED", it.getString(0)) }
            db.execSQL("INSERT INTO past_paper_items VALUES (1, 1, 1, '一'), (2, 1, 1, '一')")
            assertTrue(runCatching { db.execSQL("INSERT INTO past_paper_items VALUES (1, 1, 2, '二')") }.isFailure)
            assertTrue(runCatching { db.execSQL("INSERT INTO past_paper_items VALUES (1, 2, 2, '一')") }.isFailure)
            db.execSQL("DELETE FROM past_papers WHERE id = 1")
            db.query("SELECT COUNT(*) FROM questions WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
            db.execSQL("DELETE FROM question_banks WHERE id = 1")
            db.query("SELECT COUNT(*) FROM past_papers").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.query("SELECT COUNT(*) FROM past_paper_items").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }
}
