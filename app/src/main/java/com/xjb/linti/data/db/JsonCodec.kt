package com.xjb.linti.data.db

import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.MultiSelectRule
import com.xjb.linti.domain.QuestionImage
import com.xjb.linti.data.images.QuestionImageStore
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.TypeStats
import org.json.JSONArray
import org.json.JSONObject

object JsonCodec {
    fun encodeImages(images: List<QuestionImage>): JSONArray = JSONArray().apply {
        images.forEach { put(JSONObject().put("fileName", it.fileName).put("width", it.width).put("height", it.height)) }
    }

    fun decodeImages(array: JSONArray?): List<QuestionImage> {
        if (array == null) return emptyList()
        require(array.length() <= QuestionImageStore.MAX_IMAGES) { "图片数量无效" }
        return List(array.length()) { index ->
            val image = array.getJSONObject(index)
            QuestionImage(image.getString("fileName"), image.getInt("width"), image.getInt("height")).also {
                require(QuestionImageStore.FILE_NAME.matches(it.fileName) && it.width in 1..2048 && it.height in 1..2048) { "图片引用无效" }
            }
        }
    }

    fun encodeAnswerAlternatives(values: List<List<String>>): String = JSONArray().apply {
        values.forEach { alternatives -> put(JSONArray().apply { alternatives.forEach { put(it) } }) }
    }.toString()

    fun decodeAnswerAlternatives(json: String?): List<List<String>> = runCatching {
        val array = JSONArray(json ?: "[]")
        (0 until array.length()).map { i ->
            val answers = array.getJSONArray(i)
            (0 until answers.length()).map { answers.getString(it) }
        }
    }.getOrDefault(emptyList())

    fun encodeOptions(options: List<QuestionOption>): String {
        val array = JSONArray()
        options.forEach { option ->
            array.put(
                JSONObject()
                    .put("label", option.label)
                    .put("text", option.text),
            )
        }
        return array.toString()
    }

    fun decodeOptions(json: String?): List<QuestionOption> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val label = item.optString("label").trim()
                    val text = item.optString("text").trim()
                    if (label.isNotBlank() && text.isNotBlank()) {
                        add(QuestionOption(label, text))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeStrings(values: List<String>): String {
        val array = JSONArray()
        values.forEach(array::put)
        return array.toString()
    }

    fun decodeStrings(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optString(index)
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeLongList(values: Collection<Long>): String {
        val array = JSONArray()
        values.forEach(array::put)
        return array.toString()
    }

    fun decodeLongList(json: String?): List<Long> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val value = array.optLong(index, -1L)
                    if (value >= 0) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun encodeLongSet(values: Set<Long>): String = encodeLongList(values)

    fun decodeLongSet(json: String?): Set<Long> = decodeLongList(json).toSet()

    fun encodeAnswers(answers: Map<Long, String>): String {
        val objectValue = JSONObject()
        answers.forEach { (questionId, answer) -> objectValue.put(questionId.toString(), answer) }
        return objectValue.toString()
    }

    fun decodeAnswers(json: String?): Map<Long, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val objectValue = JSONObject(json)
            buildMap {
                objectValue.keys().forEach { key ->
                    val questionId = key.toLongOrNull() ?: return@forEach
                    val answer = objectValue.optString(key)
                    if (answer.isNotBlank()) put(questionId, answer)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun encodeExamConfig(config: ExamConfig): String = JSONObject()
        .put("singleCount", config.singleCount)
        .put("multiCount", config.multiCount)
        .put("judgeCount", config.judgeCount)
        .put("fillCount", config.fillCount)
        .put("essayCount", config.essayCount)
        .put("singlePoints", config.singlePoints)
        .put("multiPoints", config.multiPoints)
        .put("judgePoints", config.judgePoints)
        .put("fillPoints", config.fillPoints)
        .put("essayPoints", config.essayPoints)
        .put("durationMinutes", config.durationMinutes)
        .put("passScore", config.passScore)
        .put("shuffleOptions", config.shuffleOptions)
        .put("wrongFirst", config.wrongFirst)
        .put("unansweredFirst", config.unansweredFirst)
        .put("multiSelectRule", config.multiSelectRule.name)
        .put("showAnswerAfterAnswer", config.showAnswerAfterAnswer)
        .toString()

    fun decodeExamConfig(json: String?): ExamConfig {
        if (json.isNullOrBlank()) return ExamConfig()
        return runCatching {
            val value = JSONObject(json)
            ExamConfig(
                singleCount = value.optInt("singleCount", 0),
                multiCount = value.optInt("multiCount", 0),
                judgeCount = value.optInt("judgeCount", 0),
                fillCount = value.optInt("fillCount", 0),
                essayCount = value.optInt("essayCount", 0),
                singlePoints = value.optDouble("singlePoints", 2.0),
                multiPoints = value.optDouble("multiPoints", 2.0),
                judgePoints = value.optDouble("judgePoints", 2.0),
                fillPoints = value.optDouble("fillPoints", 2.0),
                essayPoints = value.optDouble("essayPoints", 2.0),
                durationMinutes = value.optInt("durationMinutes", 60),
                passScore = value.optDouble("passScore", 60.0),
                shuffleOptions = value.optBoolean("shuffleOptions", false),
                wrongFirst = value.optBoolean("wrongFirst", false),
                unansweredFirst = value.optBoolean("unansweredFirst", false),
                multiSelectRule = runCatching {
                    MultiSelectRule.valueOf(value.optString("multiSelectRule"))
                }.getOrDefault(MultiSelectRule.LESS_NO_SCORE),
                showAnswerAfterAnswer = value.optBoolean("showAnswerAfterAnswer", false),
            )
        }.getOrDefault(ExamConfig())
    }

    fun encodeTypeStats(stats: List<TypeStats>): String {
        val array = JSONArray()
        stats.forEach { stat ->
            array.put(
                JSONObject()
                    .put("type", stat.type.name)
                    .put("total", stat.total)
                    .put("correct", stat.correct)
                    .put("wrong", stat.wrong)
                    .put("unanswered", stat.unanswered)
                    .put("score", stat.score)
                    .put("maxScore", stat.maxScore),
            )
        }
        return array.toString()
    }

    fun decodeTypeStats(json: String?): List<TypeStats> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val type = QuestionType.entries.firstOrNull { it.name == item.optString("type") } ?: continue
                    // 非数字、非有限值或无效计数的条目整条丢弃，不用 0 补齐；
                    // 由此造成的统计不完整由满分与会话题数的交叉验证兜底
                    val stat = item.toTypeStatOrNull(type) ?: continue
                    add(stat)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.toTypeStatOrNull(type: QuestionType): TypeStats? {
        val total = optFiniteInt("total") ?: return null
        val correct = optFiniteInt("correct") ?: return null
        val wrong = optFiniteInt("wrong") ?: return null
        val unanswered = optFiniteInt("unanswered") ?: return null
        val score = optFiniteDouble("score") ?: return null
        val maxScore = optFiniteDouble("maxScore") ?: return null
        // 计数不得为负，且三项之和必须等于 total（交卷判分的不变式）
        if (total < 0 || correct < 0 || wrong < 0 || unanswered < 0) return null
        if (correct + wrong + unanswered != total) return null
        if (score < 0.0 || maxScore < 0.0) return null
        return TypeStats(type, total, correct, wrong, unanswered, score, maxScore)
    }

    private fun JSONObject.optFiniteInt(name: String): Int? {
        val value = optFiniteDouble(name) ?: return null
        if (value % 1.0 != 0.0) return null
        return value.toInt()
    }

    private fun JSONObject.optFiniteDouble(name: String): Double? {
        if (isNull(name)) return null
        val parsed = when (val raw = opt(name)) {
            is Number -> raw.toDouble()
            is String -> raw.trim().toDoubleOrNull()
            else -> null
        }
        return parsed?.takeIf { it.isFinite() }
    }

    // 逐字段读取交卷配置快照：任一字段缺失/损坏即为 null，
    // 历史回看只展示能可靠推导的及格线与限时，不用默认值冒充。
    fun decodeExamConfigSnapshot(json: String?): ExamConfigSnapshot {
        if (json.isNullOrBlank()) return ExamConfigSnapshot(null, null)
        return runCatching {
            val obj = JSONObject(json)
            val passScore = if (obj.isNull("passScore")) {
                null
            } else {
                obj.optDouble("passScore").takeIf { !it.isNaN() }
            }
            val durationMinutes = if (obj.isNull("durationMinutes")) {
                null
            } else {
                obj.optInt("durationMinutes")
            }
            ExamConfigSnapshot(passScore = passScore, durationMinutes = durationMinutes)
        }.getOrDefault(ExamConfigSnapshot(null, null))
    }
}

data class ExamConfigSnapshot(
    val passScore: Double?,
    val durationMinutes: Int?,
)
