package com.xjb.linti.data.import

import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class OpenAiCompatibleAiClient(
    baseUrl: String,
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : AiClient {
    private val endpoint: String

    init {
        val normalized = baseUrl.trim().trimEnd('/')
        if (!normalized.startsWith("https://")) {
            throw AiConfigurationException("AI Base URL 必须使用 HTTPS")
        }
        if (apiKey.isBlank()) {
            throw AiConfigurationException("尚未配置 API Key")
        }
        endpoint = "$normalized/chat/completions"
    }

    override suspend fun parseQuestions(text: String): List<DraftQuestion> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext emptyList()
        val requestJson = JSONObject()
            .put("model", model)
            .put("temperature", 0.1)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", SYSTEM_PROMPT),
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", text.take(MAX_INPUT_CHARS)),
                    ),
            )
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("AI 请求失败：HTTP ${response.code}")
            }
            parseResponse(body)
        }
    }

    internal fun parseResponse(body: String): List<DraftQuestion> {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices") ?: return emptyList()
        if (choices.length() == 0) return emptyList()
        val content = choices.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
        return parseQuestionArray(content)
    }

    internal fun parseQuestionArray(content: String): List<DraftQuestion> {
        val cleaned = content
            .replace("```json", "")
            .replace("```", "")
            .trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        if (start < 0 || end <= start) return emptyList()
        val array = runCatching { JSONArray(cleaned.substring(start, end + 1)) }
            .getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val stem = item.optString("stem").trim()
                if (stem.isBlank()) continue
                val type = parseType(item.optString("type"), item.optString("answer"))
                val optionsArray = item.optJSONArray("options") ?: JSONArray()
                val options = buildList {
                    for (optionIndex in 0 until optionsArray.length()) {
                        val option = optionsArray.optJSONObject(optionIndex) ?: continue
                        val label = option.optString("label").trim().uppercase()
                        val text = option.optString("text").trim()
                        if (label.isNotBlank() && text.isNotBlank()) {
                            add(QuestionOption(label.take(1), text))
                        }
                    }
                }
                val rawAnswer = item.optString("answer").trim()
                val normalizedAnswer = AnswerNormalizer.normalizeForStorage(type, rawAnswer)
                val returnedReasons = item.optJSONArray("reviewReasons")?.let { reasons ->
                    buildList {
                        for (reasonIndex in 0 until reasons.length()) {
                            val reason = reasons.optString(reasonIndex).trim()
                            if (reason.isNotBlank()) add(reason)
                        }
                    }
                }.orEmpty()
                add(
                    DraftQuestion(
                        sourceNumber = item.optString("sourceNumber").trim(),
                        type = type,
                        stem = stem,
                        options = options,
                        answer = normalizedAnswer,
                        explanation = item.optString("explanation").trim(),
                        tags = item.optString("tags").takeIf { it.isNotBlank() },
                        confidence = item.optDouble("confidence", 0.6).coerceIn(0.0, 1.0),
                        needsReview = true,
                        reviewReasons = (returnedReasons + "AI 识别结果，请人工确认").distinct(),
                        rawSnippet = item.toString(),
                    ),
                )
            }
        }
    }

    private fun parseType(rawType: String, rawAnswer: String): QuestionType {
        val normalized = rawType.uppercase()
        return when {
            "MULTI" in normalized || "多选" in rawType -> QuestionType.MULTI
            "JUDGE" in normalized || "判断" in rawType -> QuestionType.JUDGE
            "FILL" in normalized || "填空" in rawType -> QuestionType.FILL
            "SINGLE" in normalized || "单选" in rawType -> QuestionType.SINGLE
            AnswerNormalizer.normalizeJudge(rawAnswer) != null -> QuestionType.JUDGE
            AnswerNormalizer.normalizeLetters(rawAnswer).length > 1 -> QuestionType.MULTI
            else -> QuestionType.SINGLE
        }
    }

    private companion object {
        const val MAX_INPUT_CHARS = 120_000
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val SYSTEM_PROMPT = """
你是题库结构化助手。请把用户提供的教材题目整理为 JSON 数组，不得编造答案。
每个元素字段：sourceNumber, type(SINGLE/MULTI/JUDGE/FILL), stem,
options(label/text 数组，最多 A-H), answer, explanation, tags, confidence(0-1),
needsReview, reviewReasons。
多选答案使用排序后的字母串，判断题答案用 true/false，填空题保留原文。
无法确定的题目仍需输出，并在 reviewReasons 中说明。
只返回 JSON 数组，不要 Markdown 代码围栏或额外解释。
"""

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
