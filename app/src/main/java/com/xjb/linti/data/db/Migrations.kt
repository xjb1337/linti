package com.xjb.linti.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `question_attempts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `question_id` INTEGER NOT NULL, `bank_id` INTEGER NOT NULL, `mode` TEXT NOT NULL, `answer` TEXT NOT NULL, `is_correct` INTEGER NOT NULL, `answered_at` INTEGER NOT NULL, `session_id` INTEGER, FOREIGN KEY(`question_id`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`bank_id`) REFERENCES `question_banks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_question_attempts_question_id` ON `question_attempts` (`question_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_question_attempts_bank_id` ON `question_attempts` (`bank_id`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_question_attempts_answered_at` ON `question_attempts` (`answered_at`)",
        )
        db.execSQL(
            "ALTER TABLE `practice_progress` ADD COLUMN `round_started_at` INTEGER NOT NULL DEFAULT 0",
        )
        LegacySessionBackfill.apply(SupportSqliteBackfillStore(db))
    }
}

internal object LegacySessionBackfill {
    fun apply(store: LegacyBackfillStore) {
        val (seeds, counts) = SessionBackfill.backfill(store.read())
        store.write(seeds, counts)
    }
}

internal interface LegacyBackfillStore {
    fun read(): LegacyBackfillData
    fun write(seeds: List<AttemptSeed>, counts: List<Pair<Long, Int>>)
}

internal class SupportSqliteBackfillStore(
    private val db: SupportSQLiteDatabase,
) : LegacyBackfillStore {
    override fun read(): LegacyBackfillData {
        val sessions = mutableListOf<LegacySessionRow>()
        db.query(
            "SELECT id, mode, started_at, ended_at, question_ids_json, answers_json FROM quiz_sessions WHERE status = 'COMPLETED' AND mode != 'EXAM'",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                sessions += LegacySessionRow(
                    id = cursor.getLong(0),
                    mode = cursor.getString(1),
                    startedAt = cursor.getLong(2),
                    endedAt = if (cursor.isNull(3)) null else cursor.getLong(3),
                    questionIdsJson = cursor.getString(4) ?: "",
                    answersJson = cursor.getString(5) ?: "",
                )
            }
        }

        val questions = mutableListOf<LegacyQuestionRow>()
        db.query("SELECT id, bank_id, type, answer, options_json FROM questions").use { cursor ->
            while (cursor.moveToNext()) {
                questions += LegacyQuestionRow(
                    id = cursor.getLong(0),
                    bankId = cursor.getLong(1),
                    type = cursor.getString(2),
                    answer = cursor.getString(3),
                    optionsJson = cursor.getString(4) ?: "",
                )
            }
        }

        val progress = mutableListOf<LegacyProgressRow>()
        db.query("SELECT bank_id, mode, round_started_at FROM practice_progress").use { cursor ->
            while (cursor.moveToNext()) {
                progress += LegacyProgressRow(
                    bankId = cursor.getLong(0),
                    mode = cursor.getString(1),
                    roundStartedAt = cursor.getLong(2),
                )
            }
        }
        return LegacyBackfillData(sessions, questions, progress)
    }

    override fun write(seeds: List<AttemptSeed>, counts: List<Pair<Long, Int>>) {
        seeds.forEach { seed ->
            db.execSQL(
                "INSERT INTO `question_attempts` (`question_id`, `bank_id`, `mode`, `answer`, `is_correct`, `answered_at`, `session_id`) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    seed.questionId,
                    seed.bankId,
                    seed.mode,
                    seed.answer,
                    if (seed.isCorrect) 1 else 0,
                    seed.answeredAt,
                    seed.sessionId,
                ),
            )
        }
        counts.forEach { (bankId, count) ->
            db.execSQL(
                "UPDATE `practice_progress` SET `answered_count` = ? WHERE `bank_id` = ? AND `mode` = 'SEQUENCE'",
                arrayOf<Any>(count, bankId),
            )
        }
    }
}
