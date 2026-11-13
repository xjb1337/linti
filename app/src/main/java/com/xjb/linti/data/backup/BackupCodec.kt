package com.xjb.linti.data.backup

import com.xjb.linti.data.db.FavoriteEntity
import com.xjb.linti.data.db.ImportJobEntity
import com.xjb.linti.data.db.NoteEntity
import com.xjb.linti.data.db.PracticeProgressEntity
import com.xjb.linti.data.db.QuestionBankEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.db.QuizSessionEntity
import com.xjb.linti.data.db.WrongItemEntity
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val version: Int = 1,
    val exportedAt: Long,
    val banks: List<QuestionBankEntity> = emptyList(),
    val questions: List<QuestionEntity> = emptyList(),
    val importJobs: List<ImportJobEntity> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val wrongItems: List<WrongItemEntity> = emptyList(),
    val progress: List<PracticeProgressEntity> = emptyList(),
    val sessions: List<QuizSessionEntity> = emptyList(),
)

object BackupCodec {
    const val CURRENT_VERSION = 1

    fun encode(payload: BackupPayload): String = JSONObject()
        .put("version", payload.version)
        .put("exportedAt", payload.exportedAt)
        .put("banks", JSONArray().apply { payload.banks.forEach { put(bankJson(it)) } })
        .put("questions", JSONArray().apply { payload.questions.forEach { put(questionJson(it)) } })
        .put("importJobs", JSONArray().apply { payload.importJobs.forEach { put(importJobJson(it)) } })
        .put("favorites", JSONArray().apply { payload.favorites.forEach { put(favoriteJson(it)) } })
        .put("notes", JSONArray().apply { payload.notes.forEach { put(noteJson(it)) } })
        .put("wrongItems", JSONArray().apply { payload.wrongItems.forEach { put(wrongJson(it)) } })
        .put("progress", JSONArray().apply { payload.progress.forEach { put(progressJson(it)) } })
        .put("sessions", JSONArray().apply { payload.sessions.forEach { put(sessionJson(it)) } })
        .toString(2)

    fun decode(json: String): BackupPayload {
        val root = JSONObject(json)
        val version = root.optInt("version", -1)
        require(version in 1..CURRENT_VERSION) { "不支持的备份版本：$version" }
        return BackupPayload(
            version = version,
            exportedAt = root.optLong("exportedAt", 0),
            banks = root.optJSONArray("banks").mapObjects(::bankFromJson),
            questions = root.optJSONArray("questions").mapObjects(::questionFromJson),
            importJobs = root.optJSONArray("importJobs").mapObjects(::importJobFromJson),
            favorites = root.optJSONArray("favorites").mapObjects(::favoriteFromJson),
            notes = root.optJSONArray("notes").mapObjects(::noteFromJson),
            wrongItems = root.optJSONArray("wrongItems").mapObjects(::wrongFromJson),
            progress = root.optJSONArray("progress").mapObjects(::progressFromJson),
            sessions = root.optJSONArray("sessions").mapObjects(::sessionFromJson),
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

    private fun questionJson(question: QuestionEntity) = JSONObject()
        .put("id", question.id)
        .put("bankId", question.bankId)
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

    private fun wrongFromJson(json: JSONObject) = WrongItemEntity(
        questionId = json.optLong("questionId"),
        bankId = json.optLong("bankId"),
        wrongCount = json.optInt("wrongCount"),
        lastWrongAt = json.optLong("lastWrongAt"),
        corrected = json.optBoolean("corrected"),
        pendingReview = json.optBoolean("pendingReview"),
    )

    private fun progressJson(progress: PracticeProgressEntity) = JSONObject()
        .put("bankId", progress.bankId)
        .put("mode", progress.mode)
        .put("currentIndex", progress.currentIndex)
        .put("answeredCount", progress.answeredCount)
        .put("lastOpenedAt", progress.lastOpenedAt)

    private fun progressFromJson(json: JSONObject) = PracticeProgressEntity(
        bankId = json.optLong("bankId"),
        mode = json.optString("mode"),
        currentIndex = json.optInt("currentIndex"),
        answeredCount = json.optInt("answeredCount"),
        lastOpenedAt = json.optLong("lastOpenedAt"),
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

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

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
