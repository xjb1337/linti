package com.xjb.linti.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `ai_explanations` (`question_id` INTEGER NOT NULL, `content` TEXT NOT NULL, `model` TEXT NOT NULL, `provider` TEXT NOT NULL, `question_updated_at` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`question_id`), FOREIGN KEY(`question_id`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `past_papers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bank_id` INTEGER NOT NULL, `year` INTEGER NOT NULL, `name` TEXT NOT NULL, `source_category` TEXT NOT NULL DEFAULT 'UNVERIFIED', `source_note` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`bank_id`) REFERENCES `question_banks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_past_papers_bank_id` ON `past_papers` (`bank_id`)")
        db.execSQL("CREATE TABLE IF NOT EXISTS `past_paper_items` (`paper_id` INTEGER NOT NULL, `question_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `original_number` TEXT NOT NULL, PRIMARY KEY(`paper_id`, `question_id`), FOREIGN KEY(`paper_id`) REFERENCES `past_papers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`question_id`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_past_paper_items_paper_id_original_number` ON `past_paper_items` (`paper_id`, `original_number`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_past_paper_items_question_id` ON `past_paper_items` (`question_id`)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `question_attempts` ADD COLUMN `answer_viewed` INTEGER NOT NULL DEFAULT 0")
    }
}

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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exam_drafts` (`draft_id` TEXT NOT NULL, `bank_id` INTEGER NOT NULL, `revision` INTEGER NOT NULL, PRIMARY KEY(`draft_id`), FOREIGN KEY(`bank_id`) REFERENCES `question_banks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_exam_drafts_bank_id` ON `exam_drafts` (`bank_id`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exam_draft_chunks` (`draft_id` TEXT NOT NULL, `kind` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `part` INTEGER NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`draft_id`, `kind`, `ordinal`, `part`), FOREIGN KEY(`draft_id`) REFERENCES `exam_drafts`(`draft_id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
    }
}

// v4 新增已交卷模考原卷快照表。迁移不从题库或错题本回填旧场次：
// 旧模考只有成绩，没有原卷，回看入口按快照有无隐藏。
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exam_papers` (`session_id` INTEGER NOT NULL, `format_version` INTEGER NOT NULL, `question_count` INTEGER NOT NULL, PRIMARY KEY(`session_id`), FOREIGN KEY(`session_id`) REFERENCES `quiz_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `exam_paper_chunks` (`session_id` INTEGER NOT NULL, `kind` TEXT NOT NULL, `ordinal` INTEGER NOT NULL, `part` INTEGER NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`session_id`, `kind`, `ordinal`, `part`), FOREIGN KEY(`session_id`) REFERENCES `exam_papers`(`session_id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `chapters` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bank_id` INTEGER NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, FOREIGN KEY(`bank_id`) REFERENCES `question_banks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapters_bank_id` ON `chapters` (`bank_id`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_chapters_bank_id_name` ON `chapters` (`bank_id`, `name`)")
        db.execSQL("ALTER TABLE `questions` ADD COLUMN `chapter_id` INTEGER REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_questions_chapter_id` ON `questions` (`chapter_id`)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `wrong_items` ADD COLUMN `correct_streak` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `wrong_items` ADD COLUMN `mastered_at` INTEGER")
        db.execSQL("UPDATE `wrong_items` SET `corrected` = 0, `pending_review` = 1")
        db.execSQL("ALTER TABLE `question_attempts` ADD COLUMN `submission_id` TEXT")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_question_attempts_submission_id` ON `question_attempts` (`submission_id`)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `flashcard_reviews` (`question_id` INTEGER NOT NULL, `bank_id` INTEGER NOT NULL, `step` INTEGER NOT NULL, `due_at` INTEGER NOT NULL, `first_reviewed_at` INTEGER NOT NULL, `last_reviewed_at` INTEGER NOT NULL, `review_count` INTEGER NOT NULL, `lapse_count` INTEGER NOT NULL, PRIMARY KEY(`question_id`), FOREIGN KEY(`question_id`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcard_reviews_bank_id` ON `flashcard_reviews` (`bank_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcard_reviews_due_at` ON `flashcard_reviews` (`due_at`)")
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

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `questions` ADD COLUMN `origin` TEXT NOT NULL DEFAULT 'IMPORTED'")
        db.execSQL("CREATE TABLE IF NOT EXISTS `ai_question_drafts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bank_id` INTEGER NOT NULL, `payload` TEXT NOT NULL, `problems_json` TEXT NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`bank_id`) REFERENCES `question_banks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ai_question_drafts_bank_id` ON `ai_question_drafts` (`bank_id`)")
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `questions` ADD COLUMN `source_excerpt` TEXT")
        db.execSQL("ALTER TABLE `questions` ADD COLUMN `accepted_answers_json` TEXT NOT NULL DEFAULT '[]'")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `question_images` (`question_id` INTEGER NOT NULL, `position` INTEGER NOT NULL, `file_name` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, PRIMARY KEY(`question_id`, `position`), FOREIGN KEY(`question_id`) REFERENCES `questions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
    }
}
