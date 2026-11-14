package com.xjb.linti.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChapterMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LintiDatabase::class.java)

    @Test fun migrate4To5PreservesAllDataAndLeavesAllQuestionsUncategorized() = runBlocking {
        val dbName = "chapter-migration-test.db"
        val tables = listOf("question_banks", "questions", "wrong_items", "quiz_sessions", "question_attempts", "exam_papers", "exam_paper_chunks")
        val before = mutableMapOf<String, List<Map<String, String?>>>()
        helper.createDatabase(dbName, 4).use { db ->
            db.execSQL("INSERT INTO question_banks (id, name, created_at, updated_at) VALUES (1, '虚构题库', 1, 2)")
            db.execSQL("INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, tags, knowledge_point, confidence, needs_review, review_reasons_json, source_file_name, created_at, updated_at) VALUES (1, 1, '1', 'SINGLE', '虚构题', '[]', 'A', '虚构解析', '虚构标签', '虚构知识点', 1.0, 0, '[]', '虚构来源', 1, 2)")
            db.execSQL("INSERT INTO wrong_items (question_id, bank_id, wrong_count, last_wrong_at, corrected, pending_review) VALUES (1, 1, 2, 3, 0, 1)")
            db.execSQL("INSERT INTO quiz_sessions (id, bank_id, bank_name, mode, config_json, question_ids_json, answers_json, flags_json, bookmarks_json, started_at, score, status, answered_count, correct_count, wrong_count, unanswered_count) VALUES (2, 1, '虚构题库', 'EXAM', '{}', '[1]', '{}', '[]', '[]', 1, 2.0, 'COMPLETED', 1, 0, 1, 0)")
            db.execSQL("INSERT INTO question_attempts (id, question_id, bank_id, mode, answer, is_correct, answered_at, session_id) VALUES (3, 1, 1, 'EXAM', 'B', 0, 4, 2)")
            db.execSQL("INSERT INTO exam_papers (session_id, format_version, question_count) VALUES (2, 1, 1)")
            db.execSQL("INSERT INTO exam_paper_chunks (session_id, kind, ordinal, part, content) VALUES (2, 'fixture', 0, 0, '虚构内容')")
            tables.forEach { before[it] = readRows(db, it) }
        }
        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5).use { db ->
            tables.forEach { table -> assertEquals(table, before.getValue(table), readRows(db, table)) }
            db.query("SELECT chapter_id, knowledge_point, tags, stem FROM questions").use { cursor ->
                cursor.moveToFirst(); assertTrue(cursor.isNull(0)); assertEquals("虚构知识点", cursor.getString(1)); assertEquals("虚构标签", cursor.getString(2)); assertEquals("虚构题", cursor.getString(3))
            }
            db.query("SELECT COUNT(*) FROM chapters").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LintiDatabase::class.java, dbName)
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12).allowMainThreadQueries().build()
        try {
            assertEquals("虚构题", room.questionDao().getById(1)!!.stem)
            assertNull(room.questionDao().getById(1)!!.chapterId)
            assertEquals(1, room.questionAttemptDao().getAll().size)
            assertEquals(1, room.examPaperDao().getAll().size)
        } finally { room.close() }
    }
    private fun readRows(db: SupportSQLiteDatabase, table: String): List<Map<String, String?>> =
        db.query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.columnNames.mapIndexedNotNull { index, name ->
                        if (name == "chapter_id") null else name to if (cursor.isNull(index)) null else cursor.getString(index)
                    }.toMap())
                }
            }
        }

}
