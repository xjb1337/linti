package com.xjb.linti.data.db

import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.MultiSelectRule
import com.xjb.linti.domain.QuestionOrigin
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizQuestion
import org.json.JSONArray
import org.json.JSONObject

data class ExamDraftSnapshot(
    val draftId: String,
    val bankId: Long,
    val bankName: String,
    val config: ExamConfig,
    val questions: List<QuizQuestion>,
    val answers: Map<Long, String>,
    val committedIds: Set<Long>,
    val flaggedIds: Set<Long>,
    val bookmarkedIds: Set<Long>,
    val currentIndex: Int,
    val startedAt: Long,
    val revision: Long,
)

object ExamDraftCodec {
    private const val CHUNK_SIZE = 16_000

    fun encodeInitial(snapshot: ExamDraftSnapshot): Pair<ExamDraftEntity, List<ExamDraftChunkEntity>> {
        val metadata = JSONObject()
            .put("version", 1)
            .put("bankName", snapshot.bankName)
            .put("config", JSONObject(JsonCodec.encodeExamConfig(snapshot.config)))
            .put("questionCount", snapshot.questions.size)
            .put("startedAt", snapshot.startedAt)
        val rows = chunks(snapshot.draftId, "metadata", 0, metadata.toString()).toMutableList()
        snapshot.questions.forEachIndexed { index, question ->
            val options = JSONArray()
            question.options.forEach { option ->
                options.put(JSONObject().put("label", option.label).put("text", option.text))
            }
            val labelMap = JSONObject()
            question.labelMap.forEach { (label, original) -> labelMap.put(label, original) }
            val json = JSONObject()
                .put("questionId", question.questionId)
                .put("bankId", question.bankId)
                .put("sourceNumber", question.sourceNumber)
                .put("type", question.type.name)
                .put("origin", question.origin.name)
                .put("stem", question.stem)
                .put("images", JsonCodec.encodeImages(question.images))
                .put("options", options)
                .put("correctAnswer", question.correctAnswer)
                .put("acceptedAnswers", JSONArray(JsonCodec.encodeAnswerAlternatives(question.acceptedAnswers)))
                .put("explanation", question.explanation)
                .put("tags", question.tags ?: JSONObject.NULL)
                .put("points", question.points)
                .put("labelMap", labelMap)
            rows += chunks(snapshot.draftId, "question", index, json.toString())
        }
        rows += encodeProgress(snapshot)
        return ExamDraftEntity(snapshot.draftId, snapshot.bankId, snapshot.revision) to rows
    }

    fun encodeProgress(snapshot: ExamDraftSnapshot): List<ExamDraftChunkEntity> {
        val answers = JSONObject()
        snapshot.answers.forEach { (id, answer) -> answers.put(id.toString(), answer) }
        val json = JSONObject()
            .put("answers", answers)
            .put("committedIds", JSONArray(JsonCodec.encodeLongSet(snapshot.committedIds)))
            .put("flaggedIds", JSONArray(JsonCodec.encodeLongSet(snapshot.flaggedIds)))
            .put("bookmarkedIds", JSONArray(JsonCodec.encodeLongSet(snapshot.bookmarkedIds)))
            .put("currentIndex", snapshot.currentIndex)
        return chunks(snapshot.draftId, "progress", 0, json.toString())
    }

    fun decode(entity: ExamDraftEntity, rows: List<ExamDraftChunkEntity>): ExamDraftSnapshot {
        val grouped = rows.groupBy { it.kind to it.ordinal }
        val metadata = JSONObject(assemble(grouped, "metadata", 0))
        require(metadata.getInt("version") == 1) { "不支持的考试进度格式" }
        val configJson = metadata.getJSONObject("config")
        val config = ExamConfig(
            singleCount = configJson.getInt("singleCount"),
            multiCount = configJson.getInt("multiCount"),
            judgeCount = configJson.getInt("judgeCount"),
            fillCount = configJson.getInt("fillCount"),
            essayCount = configJson.optInt("essayCount", 0),
            singlePoints = configJson.getDouble("singlePoints"),
            multiPoints = configJson.getDouble("multiPoints"),
            judgePoints = configJson.getDouble("judgePoints"),
            fillPoints = configJson.getDouble("fillPoints"),
            essayPoints = configJson.optDouble("essayPoints", 2.0),
            durationMinutes = configJson.getInt("durationMinutes"),
            passScore = configJson.getDouble("passScore"),
            shuffleOptions = configJson.getBoolean("shuffleOptions"),
            wrongFirst = configJson.getBoolean("wrongFirst"),
            unansweredFirst = configJson.getBoolean("unansweredFirst"),
            multiSelectRule = MultiSelectRule.valueOf(configJson.getString("multiSelectRule")),
            showAnswerAfterAnswer = configJson.getBoolean("showAnswerAfterAnswer"),
        )
        val questionCount = metadata.getInt("questionCount")
        require(questionCount > 0) { "考试题目为空" }
        val questions = List(questionCount) { index ->
            val question = JSONObject(assemble(grouped, "question", index))
            val optionArray = question.getJSONArray("options")
            val options = List(optionArray.length()) { optionIndex ->
                val option = optionArray.getJSONObject(optionIndex)
                QuestionOption(option.getString("label"), option.getString("text"))
            }
            val mapJson = question.getJSONObject("labelMap")
            val labelMap = buildMap {
                mapJson.keys().forEach { key -> put(key, mapJson.getString(key)) }
            }
            QuizQuestion(
                questionId = question.getLong("questionId"),
                bankId = question.getLong("bankId"),
                sourceNumber = question.getString("sourceNumber"),
                type = QuestionType.valueOf(question.getString("type")),
                origin = if (question.has("origin")) QuestionOrigin.valueOf(question.getString("origin")) else QuestionOrigin.IMPORTED,
                stem = question.getString("stem"),
                images = if (question.has("images")) JsonCodec.decodeImages(question.getJSONArray("images")) else emptyList(),
                options = options,
                correctAnswer = question.getString("correctAnswer"),
                acceptedAnswers = JsonCodec.decodeAnswerAlternatives(question.optJSONArray("acceptedAnswers")?.toString()),
                explanation = question.getString("explanation"),
                tags = if (question.isNull("tags")) null else question.getString("tags"),
                points = question.getDouble("points"),
                labelMap = labelMap,
            )
        }
        val progress = JSONObject(assemble(grouped, "progress", 0))
        val currentIndex = progress.getInt("currentIndex")
        require(currentIndex in questions.indices) { "考试位置无效" }
        val answersJson = progress.getJSONObject("answers")
        val answers = buildMap {
            answersJson.keys().forEach { key -> put(key.toLong(), answersJson.getString(key)) }
        }
        return ExamDraftSnapshot(
            draftId = entity.draftId,
            bankId = entity.bankId,
            bankName = metadata.getString("bankName"),
            config = config,
            questions = questions,
            answers = answers,
            committedIds = readIds(progress.getJSONArray("committedIds")),
            flaggedIds = readIds(progress.getJSONArray("flaggedIds")),
            bookmarkedIds = readIds(progress.getJSONArray("bookmarkedIds")),
            currentIndex = currentIndex,
            startedAt = metadata.getLong("startedAt"),
            revision = entity.revision,
        )
    }

    private fun readIds(array: JSONArray): Set<Long> =
        (0 until array.length()).map { array.getLong(it) }.toSet()

    private fun chunks(id: String, kind: String, ordinal: Int, text: String): List<ExamDraftChunkEntity> {
        val rows = mutableListOf<ExamDraftChunkEntity>()
        var start = 0
        while (start < text.length) {
            var end = minOf(start + CHUNK_SIZE, text.length)
            if (end < text.length && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) {
                end--
            }
            rows += ExamDraftChunkEntity(id, kind, ordinal, rows.size, text.substring(start, end))
            start = end
        }
        return rows
    }

    private fun assemble(
        rows: Map<Pair<String, Int>, List<ExamDraftChunkEntity>>,
        kind: String,
        ordinal: Int,
    ): String {
        val parts = rows[kind to ordinal].orEmpty().sortedBy { it.part }
        require(parts.isNotEmpty() && parts.indices.all { parts[it].part == it }) { "考试进度不完整" }
        return parts.joinToString("") { it.content }
    }
}
