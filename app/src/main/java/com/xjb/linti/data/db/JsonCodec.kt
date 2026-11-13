package com.xjb.linti.data.db

import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.MultiSelectRule
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.TypeStats
import org.json.JSONArray
import org.json.JSONObject

object JsonCodec {
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
        .put("singlePoints", config.singlePoints)
        .put("multiPoints", config.multiPoints)
        .put("judgePoints", config.judgePoints)
        .put("fillPoints", config.fillPoints)
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
                singlePoints = value.optDouble("singlePoints", 2.0),
                multiPoints = value.optDouble("multiPoints", 2.0),
                judgePoints = value.optDouble("judgePoints", 2.0),
                fillPoints = value.optDouble("fillPoints", 2.0),
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
}
