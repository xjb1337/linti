package com.xjb.linti.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val testDbName = "linti-migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LintiDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate2To3KeepsExistingDataAndCreatesDraftTables() {
        val name = "linti-migration-2-to-3.db"
        helper.createDatabase(name, 2).apply {
            execSQL("INSERT INTO question_banks (id, name, created_at, updated_at, note) VALUES (1, '题库', 10, 20, NULL)")
            close()
        }
        val migrated = helper.runMigrationsAndValidate(name, 3, true, MIGRATION_2_3)
        migrated.query("SELECT name FROM question_banks WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("题库", cursor.getString(0))
        }
        migrated.query("SELECT COUNT(*) FROM exam_drafts").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM exam_draft_chunks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun migrate1To2KeepsOldDataAndBackfillsNonExamSessions() {
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                "INSERT INTO question_banks (id, name, created_at, updated_at, note) VALUES (1, '题库甲', 10, 20, '备注')",
            )
            listOf(1L, 2L, 3L).forEach { id ->
                execSQL(
                    "INSERT INTO questions (id, bank_id, source_number, type, stem, options_json, answer, explanation, tags, knowledge_point, confidence, needs_review, review_reasons_json, source_file_name, raw_snippet, created_at, updated_at) " +
                        "VALUES ($id, 1, '$id', 'SINGLE', '题干$id', '[{\"label\":\"A\",\"text\":\"甲\"},{\"label\":\"B\",\"text\":\"乙\"}]', 'A', '', NULL, NULL, 1.0, 0, '[]', 'a.txt', NULL, 1, 2)",
                )
            }
            execSQL(
                "INSERT INTO import_jobs (id, file_name, mime_type, kind, method, status, recognized_count, confirmed_count, pending_count, failed_count, created_at, target_bank_id, warnings_json) " +
                    "VALUES (1, 'a.txt', 'text/plain', 'txt', 'LOCAL', 'CONFIRMED', 1, 1, 0, 0, 10, 1, '[]')",
            )
            execSQL("INSERT INTO favorites (question_id, created_at) VALUES (1, 30)")
            execSQL("INSERT INTO notes (question_id, content, created_at, updated_at) VALUES (1, '笔记', 30, 40)")
            execSQL(
                "INSERT INTO wrong_items (question_id, bank_id, wrong_count, last_wrong_at, corrected, pending_review) VALUES (2, 1, 1, 50, 0, 1)",
            )
            execSQL(
                "INSERT INTO practice_progress (bank_id, mode, current_index, answered_count, last_opened_at) VALUES (1, 'SEQUENCE', 7, 0, 60)",
            )
            execSQL(
                "INSERT INTO quiz_sessions (id, bank_id, bank_name, mode, config_json, question_ids_json, answers_json, flags_json, bookmarks_json, started_at, ended_at, score, passed, stats_json, status, answered_count, correct_count, wrong_count, unanswered_count) " +
                    "VALUES (100, 1, '题库甲', 'SEQUENCE', '{}', '[1,2,3]', '{\"1\":\"A\",\"2\":\"B\",\"3\":\"\"}', '[]', '[]', 1000, 2000, 2.0, 1, '[]', 'COMPLETED', 2, 1, 1, 1)",
            )
            execSQL(
                "INSERT INTO quiz_sessions (id, bank_id, bank_name, mode, config_json, question_ids_json, answers_json, flags_json, bookmarks_json, started_at, ended_at, score, passed, stats_json, status, answered_count, correct_count, wrong_count, unanswered_count) " +
                    "VALUES (101, 1, '题库甲', 'EXAM', '{}', '[1,2]', '{\"1\":\"A\",\"2\":\"A\"}', '[]', '[]', 3000, 4000, 2.0, 1, '[]', 'COMPLETED', 2, 2, 0, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(testDbName, 2, true, MIGRATION_1_2)

        migrated.query("SELECT COUNT(*) FROM question_banks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT name, note FROM question_banks WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("题库甲", cursor.getString(0))
            assertEquals("备注", cursor.getString(1))
        }
        migrated.query("SELECT COUNT(*) FROM questions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM import_jobs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM favorites").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM notes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM wrong_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM quiz_sessions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }

        migrated.query("SELECT question_id, mode, answer, is_correct, answered_at, session_id FROM question_attempts ORDER BY question_id").use { cursor ->
            val rows = mutableListOf<List<Any?>>()
            while (cursor.moveToNext()) {
                rows += listOf(
                    cursor.getLong(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getInt(3),
                    cursor.getLong(4),
                    if (cursor.isNull(5)) null else cursor.getLong(5),
                )
            }
            assertEquals(2, rows.size)
            assertEquals(listOf(1L, "SEQUENCE", "A", 1, 2_000L, 100L), rows[0])
            assertEquals(listOf(2L, "SEQUENCE", "B", 0, 2_000L, 100L), rows[1])
        }

        migrated.query("SELECT current_index, answered_count, round_started_at FROM practice_progress WHERE bank_id = 1 AND mode = 'SEQUENCE'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(0))
            assertEquals(2, cursor.getInt(1))
            assertEquals(0L, cursor.getLong(2))
        }
        migrated.close()
    }
}
