package com.xjb.linti.data.backup

import com.xjb.linti.data.db.AiExplanationEntity
import com.xjb.linti.data.db.ChapterEntity
import com.xjb.linti.data.db.ExamPaperChunkEntity
import com.xjb.linti.data.db.ExamPaperEntity
import com.xjb.linti.data.db.FlashcardReviewEntity
import com.xjb.linti.data.db.FavoriteEntity
import com.xjb.linti.data.db.ImportJobEntity
import com.xjb.linti.data.db.NoteEntity
import com.xjb.linti.data.db.PastPaperEntity
import com.xjb.linti.data.db.PastPaperItemEntity
import com.xjb.linti.data.db.PracticeProgressEntity
import com.xjb.linti.data.db.QuestionAttemptEntity
import com.xjb.linti.data.db.QuestionBankEntity
import com.xjb.linti.data.db.QuestionEntity
import com.xjb.linti.data.db.QuizSessionEntity
import com.xjb.linti.data.db.WrongItemEntity
import org.json.JSONArray
import org.json.JSONObject

data class BackupPayload(
    val version: Int = BackupCodec.CURRENT_VERSION,
    val exportedAt: Long,
    val banks: List<QuestionBankEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList(),
    val pastPapers: List<PastPaperEntity> = emptyList(),
    val pastPaperItems: List<PastPaperItemEntity> = emptyList(),
    val questions: List<QuestionEntity> = emptyList(),
    val aiExplanations: List<AiExplanationEntity> = emptyList(),
    val importJobs: List<ImportJobEntity> = emptyList(),
    val favorites: List<FavoriteEntity> = emptyList(),
    val notes: List<NoteEntity> = emptyList(),
    val wrongItems: List<WrongItemEntity> = emptyList(),
    val flashcardReviews: List<FlashcardReviewEntity> = emptyList(),
    val progress: List<PracticeProgressEntity> = emptyList(),
    val sessions: List<QuizSessionEntity> = emptyList(),
    val questionAttempts: List<QuestionAttemptEntity> = emptyList(),
    val examPapers: List<ExamPaperEntity> = emptyList(),
    val examPaperChunks: List<ExamPaperChunkEntity> = emptyList(),
)

object BackupCodec {
    const val CURRENT_VERSION = 10

    fun encode(payload: BackupPayload): String = JSONObject()
        .put("version", payload.version)
        .put("exportedAt", payload.exportedAt)
        .put("banks", JSONArray().apply { payload.banks.forEach { put(bankJson(it)) } })
        .put("chapters", JSONArray().apply { payload.chapters.forEach { put(chapterJson(it)) } })
        .put("pastPapers", JSONArray().apply { payload.pastPapers.forEach { put(pastPaperJson(it)) } })
        .put("pastPaperItems", JSONArray().apply { payload.pastPaperItems.forEach { put(pastPaperItemJson(it)) } })
        .put("questions", JSONArray().apply { payload.questions.forEach { put(questionJson(it)) } })
        .put("aiExplanations", JSONArray().apply { payload.aiExplanations.forEach { put(aiExplanationJson(it)) } })
        .put("importJobs", JSONArray().apply { payload.importJobs.forEach { put(importJobJson(it)) } })
        .put("favorites", JSONArray().apply { payload.favorites.forEach { put(favoriteJson(it)) } })
        .put("notes", JSONArray().apply { payload.notes.forEach { put(noteJson(it)) } })
        .put("wrongItems", JSONArray().apply { payload.wrongItems.forEach { put(wrongJson(it)) } })
        .put("flashcardReviews", JSONArray().apply { payload.flashcardReviews.forEach { put(flashcardJson(it)) } })
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
        if (version >= 8) validateCurrentShape(root)
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
            banks = root.requiredArray("banks").requiredObjects("banks").map(::bankFromJson),
            chapters = root.optJSONArray("chapters").mapObjects(::chapterFromJson),
            pastPapers = if (version >= 8) root.requiredArray("pastPapers").requiredObjects("pastPapers").map(::pastPaperFromJson) else emptyList(),
            pastPaperItems = if (version >= 8) root.requiredArray("pastPaperItems").requiredObjects("pastPaperItems").map(::pastPaperItemFromJson) else emptyList(),
            questions = root.requiredArray("questions").requiredObjects("questions").map { questionFromJson(it, version) }.map { if (version < 4) it.copy(chapterId = null) else it },
            aiExplanations = if (version >= 9) root.requiredArray("aiExplanations").requiredObjects("aiExplanations").map(::aiExplanationFromJson) else emptyList(),
            importJobs = root.optJSONArray("importJobs").mapObjects(::importJobFromJson),
            favorites = root.optJSONArray("favorites").mapObjects(::favoriteFromJson),
            notes = root.optJSONArray("notes").mapObjects(::noteFromJson),
            wrongItems = if (version >= 5) {
                root.requiredArray("wrongItems").requiredObjects("wrongItems").map { wrongFromJson(it, version) }
            } else root.optJSONArray("wrongItems").mapObjects { wrongFromJson(it, version) },
            flashcardReviews = if (version >= 6) root.requiredArray("flashcardReviews").requiredObjects("flashcardReviews").map(::flashcardFromJson) else emptyList(),
            progress = root.optJSONArray("progress").mapObjects(::progressFromJson),
            sessions = root.optJSONArray("sessions").mapObjects(::sessionFromJson),
            questionAttempts = if (version >= 7) {
                root.requiredArray("questionAttempts").requiredObjects("questionAttempts").map { attemptFromJson(it, version) }
            } else root.optJSONArray("questionAttempts").mapObjects { attemptFromJson(it, version) },
            examPapers = papers,
            examPaperChunks = paperChunks,
        )
    }

    // v8 是完整备份：缺少集合或字段不能当作空值恢复，否则会静默清除设备上的数据。
    // 旧版本继续使用各自的兼容解码规则。
    private data class BackupFields(
        val strings: String = "", val numbers: String = "", val booleans: String = "",
        val nullableStrings: String = "", val nullableNumbers: String = "", val nullableBooleans: String = "",
    )

    private fun validateCurrentShape(root: JSONObject) {
        require(root.opt("version") is Number && root.opt("exportedAt") is Number) { "备份头部无效" }
        val fields = mapOf(
            "banks" to BackupFields(strings = "name", numbers = "id createdAt updatedAt", nullableStrings = "note"),
            "chapters" to BackupFields(strings = "name", numbers = "id bankId sortOrder createdAt updatedAt"),
            "questions" to BackupFields(
                strings = "sourceNumber type stem optionsJson answer explanation reviewReasonsJson sourceFileName",
                numbers = "id bankId confidence createdAt updatedAt", booleans = "needsReview",
                nullableStrings = "tags knowledgePoint rawSnippet", nullableNumbers = "chapterId",
            ),
            "importJobs" to BackupFields(
                strings = "fileName kind method status warningsJson", numbers = "id recognizedCount confirmedCount pendingCount failedCount createdAt",
                nullableStrings = "mimeType", nullableNumbers = "targetBankId",
            ),
            "favorites" to BackupFields(numbers = "questionId createdAt"),
            "notes" to BackupFields(strings = "content", numbers = "questionId createdAt updatedAt"),
            "wrongItems" to BackupFields(numbers = "questionId bankId wrongCount lastWrongAt correctStreak",
                booleans = "corrected pendingReview", nullableNumbers = "masteredAt"),
            "flashcardReviews" to BackupFields(numbers = "questionId bankId step dueAt firstReviewedAt lastReviewedAt reviewCount lapseCount"),
            "progress" to BackupFields(strings = "mode", numbers = "bankId currentIndex answeredCount lastOpenedAt roundStartedAt"),
            "sessions" to BackupFields(
                strings = "bankName mode configJson questionIdsJson answersJson flagsJson bookmarksJson status",
                numbers = "id startedAt answeredCount correctCount wrongCount unansweredCount",
                nullableStrings = "statsJson", nullableNumbers = "bankId endedAt score", nullableBooleans = "passed",
            ),
            "questionAttempts" to BackupFields(strings = "mode answer", numbers = "id questionId bankId answeredAt",
                booleans = "isCorrect answerViewed", nullableStrings = "submissionId", nullableNumbers = "sessionId"),
            "pastPapers" to BackupFields(strings = "name sourceCategory", numbers = "id bankId year createdAt updatedAt", nullableStrings = "sourceNote"),
            "pastPaperItems" to BackupFields(strings = "originalNumber", numbers = "paperId questionId position"),
        )
        fields.forEach { (arrayName, spec) ->
            root.requiredArray(arrayName).requiredObjects(arrayName).forEach { obj ->
                fun checkFields(names: String, nullable: Boolean, valid: (Any?) -> Boolean) {
                    names.split(' ').filter { it.isNotEmpty() }.forEach { name ->
                        require(obj.has(name) && ((nullable && obj.isNull(name)) || (!obj.isNull(name) && valid(obj.opt(name))))) {
                            "备份的 $arrayName.$name 无效"
                        }
                    }
                }
                checkFields(spec.strings, false) { it is String }
                checkFields(spec.numbers, false) { it is Number }
                checkFields(spec.booleans, false) { it is Boolean }
                checkFields(spec.nullableStrings, true) { it is String }
                checkFields(spec.nullableNumbers, true) { it is Number }
                checkFields(spec.nullableBooleans, true) { it is Boolean }
            }
        }
    }

    private fun aiExplanationJson(item: AiExplanationEntity) = JSONObject()
        .put("questionId", item.questionId).put("content", item.content)
        .put("model", item.model).put("provider", item.provider)
        .put("questionUpdatedAt", item.questionUpdatedAt).put("createdAt", item.createdAt)

    private fun aiExplanationFromJson(json: JSONObject): AiExplanationEntity {
        val item = AiExplanationEntity(
            questionId = json.backupLong("questionId"), content = json.backupString("content"),
            model = json.backupString("model"), provider = json.backupString("provider"),
            questionUpdatedAt = json.backupLong("questionUpdatedAt"), createdAt = json.backupLong("createdAt"),
        )
        require(item.questionId > 0 && item.content.isNotBlank() && item.model.isNotBlank() &&
            item.provider.isNotBlank() && item.questionUpdatedAt >= 0 && item.createdAt >= 0) { "备份 AI 解析无效" }
        return item
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

    private fun pastPaperJson(paper: PastPaperEntity) = JSONObject()
        .put("id", paper.id).put("bankId", paper.bankId).put("year", paper.year).put("name", paper.name)
        .put("sourceCategory", paper.sourceCategory).putNullable("sourceNote", paper.sourceNote)
        .put("createdAt", paper.createdAt).put("updatedAt", paper.updatedAt)

    private fun JSONObject.backupLong(key: String): Long {
        val value = opt(key)
        require(value is Number) { "备份真题字段 $key 无效" }
        return value.toString().toLongOrNull() ?: throw IllegalArgumentException("备份真题字段 $key 无效")
    }
    private fun JSONObject.backupInt(key: String): Int {
        val value = backupLong(key)
        require(value in Int.MIN_VALUE..Int.MAX_VALUE) { "备份真题字段 $key 无效" }
        return value.toInt()
    }
    private fun JSONObject.backupString(key: String): String {
        val value = opt(key)
        require(value is String) { "备份真题字段 $key 无效" }
        return value
    }
    private fun pastPaperFromJson(json: JSONObject): PastPaperEntity {
        require(json.has("sourceNote") && (json.isNull("sourceNote") || json.opt("sourceNote") is String)) { "备份真题来源备注无效" }
        return PastPaperEntity(
            id = json.backupLong("id"), bankId = json.backupLong("bankId"),
            year = json.backupInt("year"), name = json.backupString("name"),
            sourceCategory = json.backupString("sourceCategory"), sourceNote = json.nullableString("sourceNote"),
            createdAt = json.backupLong("createdAt"), updatedAt = json.backupLong("updatedAt"),
        )
    }
    private fun pastPaperItemJson(item: PastPaperItemEntity) = JSONObject()
        .put("paperId", item.paperId).put("questionId", item.questionId)
        .put("position", item.position).put("originalNumber", item.originalNumber)
    private fun pastPaperItemFromJson(json: JSONObject) = PastPaperItemEntity(
        paperId = json.backupLong("paperId"), questionId = json.backupLong("questionId"),
        position = json.backupInt("position"), originalNumber = json.backupString("originalNumber"),
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
        .put("origin", question.origin)

    private fun questionFromJson(json: JSONObject, version: Int) = QuestionEntity(
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
        origin = if (version >= 10) json.backupString("origin").also { require(it in setOf("IMPORTED", "AI_GENERATED")) { "备份题目来源无效" } } else "IMPORTED",
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

    private fun flashcardJson(review: FlashcardReviewEntity) = JSONObject()
        .put("questionId", review.questionId).put("bankId", review.bankId)
        .put("step", review.step).put("dueAt", review.dueAt)
        .put("firstReviewedAt", review.firstReviewedAt).put("lastReviewedAt", review.lastReviewedAt)
        .put("reviewCount", review.reviewCount).put("lapseCount", review.lapseCount)

    private fun flashcardFromJson(json: JSONObject): FlashcardReviewEntity {
        fun integer(key: String): Long {
            val value = json.opt(key)
            require(value is Int || value is Long) { "备份闪卡字段 $key 无效" }
            return (value as Number).toLong()
        }
        val questionId = integer("questionId")
        val bankId = integer("bankId")
        val step = integer("step")
        val dueAt = integer("dueAt")
        val first = integer("firstReviewedAt")
        val last = integer("lastReviewedAt")
        val count = integer("reviewCount")
        val lapses = integer("lapseCount")
        require(questionId > 0 && bankId > 0 && step in 0..Int.MAX_VALUE && dueAt >= 0 && first >= 0 && last >= 0 && count in 1..Int.MAX_VALUE && lapses in 0..count) { "备份闪卡数据无效" }
        return FlashcardReviewEntity(questionId, bankId, step.toInt(), dueAt, first, last, count.toInt(), lapses.toInt())
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
        .put("answerViewed", attempt.answerViewed)
        .put("answeredAt", attempt.answeredAt)
        .putNullable("sessionId", attempt.sessionId)
        .putNullable("submissionId", attempt.submissionId)

    private fun attemptFromJson(json: JSONObject, version: Int) = QuestionAttemptEntity(
        id = json.optLong("id"),
        questionId = json.optLong("questionId"),
        bankId = json.optLong("bankId"),
        mode = json.optString("mode"),
        answer = json.optString("answer"),
        isCorrect = json.optBoolean("isCorrect"),
        answeredAt = json.optLong("answeredAt"),
        sessionId = json.nullableLong("sessionId"),
        submissionId = json.nullableString("submissionId"),
        answerViewed = if (version >= 7) {
            require(json.opt("answerViewed") is Boolean) { "备份答题记录缺少有效的 answerViewed" }
            json.getBoolean("answerViewed")
        } else false,
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
