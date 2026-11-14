package com.xjb.linti.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.robolectric.annotation.Config
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class AiExplanationMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LintiDatabase::class.java)

    @Before fun addSchemas() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val apk = requireNotNull(System.getProperty("linti.test.schemaApk"))
        listOf(instrumentation.context.assets, instrumentation.targetContext.assets).forEach { assets ->
            check(assets.javaClass.getMethod("addAssetPath", String::class.java).invoke(assets, apk) as Int != 0)
        }
    }

    @Test fun nineToTenCreatesEmptyTableAndPreservesQuestions() {
        val name = "ai-explanations-9-10.db"
        helper.createDatabase(name, 9).use { db ->
            db.execSQL("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (1, '虚构题库', 1, 2)")
            db.execSQL("INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, confidence, needs_review, review_reasons_json, source_file_name, created_at, updated_at) VALUES (1, 1, '1', 'SINGLE', '虚构题', '[]', 'A', '原解析', 1, 0, '[]', '虚构来源', 1, 2)")
        }
        helper.runMigrationsAndValidate(name, 10, true, MIGRATION_9_10).use { db ->
            db.execSQL("PRAGMA foreign_keys = ON")
            db.query("SELECT COUNT(*) FROM ai_explanations").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.query("SELECT explanation FROM questions WHERE id = 1").use { assertTrue(it.moveToFirst()); assertEquals("原解析", it.getString(0)) }
            assertTrue(runCatching { db.execSQL("INSERT INTO ai_explanations VALUES (2, '解析', '模型', 'provider', 2, 3)") }.isFailure)
            db.execSQL("INSERT INTO ai_explanations VALUES (1, '解析', '模型', 'provider', 2, 3)")
            db.execSQL("DELETE FROM questions WHERE id = 1")
            db.query("SELECT COUNT(*) FROM ai_explanations").use { assertTrue(it.moveToFirst()); assertEquals(0, it.getInt(0)) }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }
}
