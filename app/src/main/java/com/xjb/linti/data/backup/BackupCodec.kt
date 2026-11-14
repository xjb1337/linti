package com.xjb.linti.data.backup

import com.xjb.linti.data.db.ChapterEntity
import com.xjb.linti.data.db.ExamPaperChunkEntity
import com.xjb.linti.data.db.ExamPaperEntity
import com.xjb.linti.data.db.FavoriteEntity
import com.xjb.linti.data.db.ImportJobEntity
import com.xjb.linti.data.db.NoteEntity
import com.xjb.linti.data.db.PracticeProgressEntity
import com.xjb.linti.data.db.QuestionAttemptEntity
import com.xjb.linti.data.db.QuestionBankEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.db.QuizSessionEntity
import com.xjb.linti.data.db.WrongItemEntity
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val version: Int = 5,
    val exportedAt: Long,
    val banks: List<QuestionBankEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val questions: List<QuestionEntity> = emptyList(),
    val importJobs: List<ImportJobEntity> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val wrongItems: List<WrongItemEntity> = emptyList(),
    val progress: List<PracticeProgressEntity> = emptyList(),
    val sessions: List<QuizSessionEntity> = emptyList(),
    val questionAttempts: List<QuestionAttemptEntity> = emptyList(),
    val examPapers: List<ExamPaperEntity> = emptyList(),
    val examPaperChunks: List<ExamPaperChunkEntity> = emptyList(),
)

object BackupCodec {
    const val CURRENT_VERSION = 5

    fun encode(payload: BackupPayload): String = JSONObject()
        .put("version", payload.version)
        .put("exportedAt", payload.exportedAt)
        .put("banks", JSONArray().apply { payload.banks.forEach { put(bankJson(it)) } })
        .put("chapters", JSONArray().apply { payload.chapters.forEach { put(chapterJson(it)) } })
        .put("questions", JSONArray().apply { payload.questions.forEach { put(questionJson(it)) } })
        .put("importJobs", JSONArray().apply { payload.importJobs.forEach { put(importJobJson(it)) } })
        .put("favorites", JSONArray().apply { payload.favorites.forEach { put(favoriteJson(it)) } })
        .put("notes", JSONArray().apply { payload.notes.forEach { put(noteJson(it)) } })
        .put("wrongItems", JSONArray().apply { payload.wrongItems.forEach { put(wrongJson(it)) } })
        .put("progress", JSONArray().apply { payload.progress.forEach { put(progressJson(it)) } })
        .put("sessions", JSONArray().apply { payload.sessions.forEach { put(sessionJson(it)) } })
        .put("questionAttempts", JSONArray().apply { payload.questionAttempts.forEach { put(attemptJson(it)) } })
        .put("examPapers", JSONArray().apply { payload.examPapers.forEach { put(examPaperJson(it)) } })
        .put(
            "examPaperChunks",
            JSONArray().apply { payload.examPaperChunks.forEach { put(examPaperChunkJson(it)) } },
        )
        .toString(2)

    fun decode(json: String): BackupPayload {
        val root = JSONObject(json)
        val version = root.optInt("version", -1)
        require(version in 1..CURRENT_VERSION) { "不支持的备份版本：$version" }
        // v3 起原卷快照必须是显式数组（允许空数组），每个元素必须是 JSON 对象：
        // null、字符串、数字或嵌套数组一律报错，不跳过；v1/v2 备份没有原卷快照，
        // 恢复后旧成绩照常可看，没有原卷
        val papers = if (version >= 3) {
            root.requiredArray("examPapers").requiredObjects("examPapers").map(::examPaperFromJson)
        } else {
            emptyList()
        }
        val paperChunks = if (version >= 3) {
            root.requiredArray("examPaperChunks").requiredObjects("examPaperChunks").map(::examPaperChunkFromJson)
        } else {
            emptyList()
        }
        return BackupPayload(
            version = version,
            exportedAt = root.optLong("exportedAt", 0),
            banks = root.optJSONArray("banks").mapObjects(::bankFromJson),
            chapters = root.optJSONArray("chapters").mapObjects(::chapterFromJson),
            questions = root.optJSONArray("questions").mapObjects(::questionFromJson).map { if (version < 4) it.copy(chapterId = null) else it },
            importJobs = root.optJSONArray("importJobs").mapObjects(::importJobFromJson),
            favorites = root.optJSONArray("favorites").mapObjects(::favoriteFromJson),
            notes = root.optJSONArray("notes").mapObjects(::noteFromJson),
            wrongItems = if (version >= 5) {
                root.requiredArray("wrongItems").requiredObjects("wrongItems").map { wrongFromJson(it, version) }
            } else root.optJSONArray("wrongItems").mapObjects { wrongFromJson(it, version) },
            progress = root.optJSONArray("progress").mapObjects(::progressFromJson),
            sessions = root.optJSONArray("sessions").mapObjects(::sessionFromJson),
            questionAttempts = root.optJSONArray("questionAttempts").mapObjects(::attemptFromJson),
            examPapers = papers,
            examPaperChunks = paperChunks,
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun bankJson(bank: QuestionBankEntity) = JSONObject()
        .put("id", bank.id)
        .put("name", bank.name)
        .put("createdAt", bank.createdAt)
        .put("updatedAt", bank.updatedAt)
        .putNullable("note", bank.note)

    private fun bankFromJson(json: JSONObject) = QuestionBankEntity(
        id = json.optLong("id"),
        name = json.optString("name"),
        createdAt = json.optLong("createdAt"),
        updatedAt = json.optLong("updatedAt"),
        note = json.nullableString("note"),
    )

    // id 是备份文件内的引用；恢复时会重新生成并映射。
    private fun chapterJson(chapter: ChapterEntity) = JSONObject()
        .put("id", chapter.id).put("bankId", chapter.bankId).put("name", chapter.name)
        .put("sortOrder", chapter.sortOrder).put("createdAt", chapter.createdAt).put("updatedAt", chapter.updatedAt)

    private fun chapterFromJson(json: JSONObject) = ChapterEntity(
        id = json.optLong("id"), bankId = json.optLong("bankId"), name = json.optString("name"),
        sortOrder = json.optInt("sortOrder"), createdAt = json.optLong("createdAt"), updatedAt = json.optLong("updatedAt"),
    )

    private fun questionJson(question: QuestionEntity) = JSONObject()
        .put("id", question.id)
        .put("bankId", question.bankId)
        .putNullable("chapterId", question.chapterId)
        .put("sourceNumber", question.sourceNumber)
        .put("type", question.type)
        .put("stem", question.stem)
        .put("optionsJson", question.optionsJson)
        .put("answer", question.answer)
        .put("explanation", question.explanation)
        .putNullable("tags", question.tags)
        .putNullable("knowledgePoint", question.knowledgePoint)
        .put("confidence", question.confidence)
        .put("needsReview", question.needsReview)
        .put("reviewReasonsJson", question.reviewReasonsJson)
        .put("sourceFileName", question.sourceFileName)
        .putNullable("rawSnippet", question.rawSnippet)
        .put("createdAt", question.createdAt)
        .put("updatedAt", question.updatedAt)

    private fun questionFromJson(json: JSONObject) = QuestionEntity(
        id = json.optLong("id"),
        bankId = json.optLong("bankId"),
        chapterId = json.nullableLong("chapterId"),
        sourceNumber = json.optString("sourceNumber"),
        type = json.optString("type"),
        stem = json.optString("stem"),
        optionsJson = json.optString("optionsJson", "[]"),
        answer = json.optString("answer"),
        explanation = json.optString("explanation"),
        tags = json.nullableString("tags"),
        knowledgePoint = json.nullableString("knowledgePoint"),
        confidence = json.optDouble("confidence", 1.0),
        needsReview = json.optBoolean("needsReview", false),
        reviewReasonsJson = json.optString("reviewReasonsJson", "[]"),
        sourceFileName = json.optString("sourceFileName"),
        rawSnippet = json.nullableString("rawSnippet"),
        createdAt = json.optLong("createdAt"),
        updatedAt = json.optLong("updatedAt"),
    )

    private fun importJobJson(job: ImportJobEntity) = JSONObject()
        .put("id", job.id)
        .put("fileName", job.fileName)
        .putNullable("mimeType", job.mimeType)
        .put("kind", job.kind)
        .put("method", job.method)
        .put("status", job.status)
        .put("recognizedCount", job.recognizedCount)
        .put("confirmedCount", job.confirmedCount)
        .put("pendingCount", job.pendingCount)
        .put("failedCount", job.failedCount)
        .put("createdAt", job.createdAt)
        .putNullable("targetBankId", job.targetBankId)
        .put("warningsJson", job.warningsJson)

    private fun importJobFromJson(json: JSONObject) = ImportJobEntity(
        id = json.optLong("id"),
        fileName = json.optString("fileName"),
        mimeType = json.nullableString("mimeType"),
        kind = json.optString("kind"),
        method = json.optString("method"),
        status = json.optString("status"),
        recognizedCount = json.optInt("recognizedCount"),
        confirmedCount = json.optInt("confirmedCount"),
        pendingCount = json.optInt("pendingCount"),
        failedCount = json.optInt("failedCount"),
        createdAt = json.optLong("createdAt"),
        targetBankId = json.nullableLong("targetBankId"),
        warningsJson = json.optString("warningsJson", "[]"),
    )

    private fun favoriteJson(favorite: FavoriteEntity) = JSONObject()
        .put("questionId", favorite.questionId)
        .put("createdAt", favorite.createdAt)

    private fun favoriteFromJson(json: JSONObject) = FavoriteEntity(
        questionId = json.optLong("questionId"),
        createdAt = json.optLong("createdAt"),
    )

    private fun noteJson(note: NoteEntity) = JSONObject()
        .put("questionId", note.questionId)
        .put("content", note.content)
        .put("createdAt", note.createdAt)
        .put("updatedAt", note.updatedAt)

    private fun noteFromJson(json: JSONObject) = NoteEntity(
        questionId = json.optLong("questionId"),
        content = json.optString("content"),
        createdAt = json.optLong("createdAt"),
        updatedAt = json.optLong("updatedAt"),
    )

    private fun wrongJson(item: WrongItemEntity) = JSONObject()
        .put("questionId", item.questionId)
        .put("bankId", item.bankId)
        .put("wrongCount", item.wrongCount)
        .put("lastWrongAt", item.lastWrongAt)
        .put("corrected", item.corrected)
        .put("pendingReview", item.pendingReview)
        .put("correctStreak", item.correctStreak)
        .putNullable("masteredAt", item.masteredAt)

    private fun wrongFromJson(json: JSONObject, version: Int): WrongItemEntity {
        if (version < 5) return WrongItemEntity(
            questionId = json.optLong("questionId"), bankId = json.optLong("bankId"),
            wrongCount = json.optInt("wrongCount"), lastWrongAt = json.optLong("lastWrongAt"),
            corrected = false, pendingReview = true,
        )
        fun integer(key: String): Long {
            val value = json.opt(key)
            require(value is Int || value is Long) { "备份错题字段 $key 无效" }
            return (value as Number).toLong()
        }
        val questionId = integer("questionId")
        val bankId = integer("bankId")
        val wrongCount = integer("wrongCount")
        val lastWrongAt = integer("lastWrongAt")
        val streak = integer("correctStreak")
        require(questionId > 0 && bankId > 0 && wrongCount in 1..Int.MAX_VALUE && streak in 0..Int.MAX_VALUE && lastWrongAt >= 0) { "备份错题数据无效" }
        require(json.has("masteredAt")) { "备份缺少 masteredAt" }
        val masteredAt = if (json.isNull("masteredAt")) null else integer("masteredAt").also { require(it >= 0) }
        require(json.opt("corrected") is Boolean && json.opt("pendingReview") is Boolean) { "备份错题状态无效" }
        require(json.getBoolean("corrected") == (masteredAt != null) && json.getBoolean("pendingReview") == (masteredAt == null)) { "备份错题状态不一致" }
        return WrongItemEntity(
            questionId = questionId, bankId = bankId, wrongCount = wrongCount.toInt(), lastWrongAt = lastWrongAt,
            corrected = masteredAt != null, pendingReview = masteredAt == null,
            correctStreak = streak.toInt(), masteredAt = masteredAt,
        )
    }

    private fun progressJson(progress: PracticeProgressEntity) = JSONObject()
        .put("bankId", progress.bankId)
        .put("mode", progress.mode)
        .put("currentIndex", progress.currentIndex)
        .put("answeredCount", progress.answeredCount)
        .put("lastOpenedAt", progress.lastOpenedAt)
        .put("roundStartedAt", progress.roundStartedAt)

    private fun progressFromJson(json: JSONObject) = PracticeProgressEntity(
        bankId = json.optLong("bankId"),
        mode = json.optString("mode"),
        currentIndex = json.optInt("currentIndex"),
        answeredCount = json.optInt("answeredCount"),
        lastOpenedAt = json.optLong("lastOpenedAt"),
        roundStartedAt = json.optLong("roundStartedAt", 0),
    )

    private fun sessionJson(session: QuizSessionEntity) = JSONObject()
        .put("id", session.id)
        .putNullable("bankId", session.bankId)
        .put("bankName", session.bankName)
        .put("mode", session.mode)
        .put("configJson", session.configJson)
        .put("questionIdsJson", session.questionIdsJson)
        .put("answersJson", session.answersJson)
        .put("flagsJson", session.flagsJson)
        .put("bookmarksJson", session.bookmarksJson)
        .put("startedAt", session.startedAt)
        .putNullable("endedAt", session.endedAt)
        .putNullable("score", session.score)
        .putNullable("passed", session.passed)
        .putNullable("statsJson", session.statsJson)
        .put("status", session.status)
        .put("answeredCount", session.answeredCount)
        .put("correctCount", session.correctCount)
        .put("wrongCount", session.wrongCount)
        .put("unansweredCount", session.unansweredCount)

    private fun sessionFromJson(json: JSONObject) = QuizSessionEntity(
        id = json.optLong("id"),
        bankId = json.nullableLong("bankId"),
        bankName = json.optString("bankName"),
        mode = json.optString("mode"),
        configJson = json.optString("configJson"),
        questionIdsJson = json.optString("questionIdsJson"),
        answersJson = json.optString("answersJson"),
        flagsJson = json.optString("flagsJson"),
        bookmarksJson = json.optString("bookmarksJson"),
        startedAt = json.optLong("startedAt"),
        endedAt = json.nullableLong("endedAt"),
        score = json.nullableDouble("score"),
        passed = json.nullableBoolean("passed"),
        statsJson = json.nullableString("statsJson"),
        status = json.optString("status"),
        answeredCount = json.optInt("answeredCount"),
        correctCount = json.optInt("correctCount"),
        wrongCount = json.optInt("wrongCount"),
        unansweredCount = json.optInt("unansweredCount"),
    )

    // v3 新增：已交卷模考原卷快照。内容分块原样导出，校验交给 ExamPaperCodec；
    // 字段一律必填，不用默认 0/空字符串补齐缺失
    private fun examPaperJson(paper: ExamPaperEntity) = JSONObject()
        .put("sessionId", paper.sessionId)
        .put("formatVersion", paper.formatVersion)
        .put("questionCount", paper.questionCount)

    private fun examPaperFromJson(json: JSONObject) = ExamPaperEntity(
        sessionId = json.requiredLong("sessionId"),
        formatVersion = json.requiredInt("formatVersion"),
        questionCount = json.requiredInt("questionCount"),
    )

    private fun examPaperChunkJson(chunk: ExamPaperChunkEntity) = JSONObject()
        .put("sessionId", chunk.sessionId)
        .put("kind", chunk.kind)
        .put("ordinal", chunk.ordinal)
        .put("part", chunk.part)
        .put("content", chunk.content)

    private fun examPaperChunkFromJson(json: JSONObject) = ExamPaperChunkEntity(
        sessionId = json.requiredLong("sessionId"),
        kind = json.requiredString("kind"),
        ordinal = json.requiredInt("ordinal"),
        part = json.requiredInt("part"),
        content = json.requiredString("content"),
    )

    private fun attemptJson(attempt: QuestionAttemptEntity) = JSONObject()
        .put("id", attempt.id)
        .put("questionId", attempt.questionId)
        .put("bankId", attempt.bankId)
        .put("mode", attempt.mode)
        .put("answer", attempt.answer)
        .put("isCorrect", attempt.isCorrect)
        .put("answeredAt", attempt.answeredAt)
        .putNullable("sessionId", attempt.sessionId)
        .putNullable("submissionId", attempt.submissionId)

    private fun attemptFromJson(json: JSONObject) = QuestionAttemptEntity(
        id = json.optLong("id"),
        questionId = json.optLong("questionId"),
        bankId = json.optLong("bankId"),
        mode = json.optString("mode"),
        answer = json.optString("answer"),
        isCorrect = json.optBoolean("isCorrect"),
        answeredAt = json.optLong("answeredAt"),
        sessionId = json.nullableLong("sessionId"),
        submissionId = json.nullableString("submissionId"),
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    // 原卷快照字段必填：缺失即损坏，不允许默认值补齐
    private fun JSONObject.requiredString(key: String): String {
        if (!has(key) || isNull(key)) throw IllegalArgumentException("原卷快照缺少字段 $key")
        return getString(key)
    }

    private fun JSONObject.requiredLong(key: String): Long {
        if (!has(key) || isNull(key)) throw IllegalArgumentException("原卷快照缺少字段 $key")
        return getLong(key)
    }

    private fun JSONObject.requiredInt(key: String): Int {
        if (!has(key) || isNull(key)) throw IllegalArgumentException("原卷快照缺少字段 $key")
        return getInt(key)
    }

    private fun JSONObject.requiredArray(key: String): JSONArray {
        if (!has(key)) throw IllegalArgumentException("备份缺少 $key")
        val value = opt(key)
        if (value !is JSONArray) throw IllegalArgumentException("备份的 $key 不是数组")
        return value
    }

    // v3 原卷快照数组的元素必须是 JSON 对象：
    // null、字符串、数字、嵌套数组立即报错，不静默跳过（与旧表的宽松解码不同）
    private fun JSONArray.requiredObjects(key: String): List<JSONObject> =
        List(length()) { index ->
            val element = opt(index)
            if (element !is JSONObject) {
                throw IllegalArgumentException("备份的 $key[$index] 不是 JSON 对象")
            }
            element
        }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key)

    private fun JSONObject.nullableBoolean(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else optBoolean(key)

    private fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                optJSONObject(index)?.let { add(transform(it)) }
            }
        }
    }
}
