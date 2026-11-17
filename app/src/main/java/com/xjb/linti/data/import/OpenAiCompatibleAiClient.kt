package com.xjb.linti.data.import

import com.xjb.linti.data.explanation.putDeepSeekThinking
import com.xjb.linti.data.net.SharedHttp
import com.xjb.linti.data.ocr.OcrKeyResolver
import com.xjb.linti.data.prefs.AiExplanationReasoningEffort
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class OpenAiCompatibleAiClient(
    baseUrl: String,
    private val apiKey: String,
    private val model: String = "gpt-4o-mini",
    httpClient: OkHttpClient = defaultHttpClient(),
) : AiClient {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
    private val endpoint: String
    private val client = httpClient.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    init {
        if (!normalizedBaseUrl.startsWith("https://", ignoreCase = true)) {
            throw AiConfigurationException("AI Base URL 必须使用 HTTPS")
        }
        val url = normalizedBaseUrl.toHttpUrlOrNull()
        if (url == null || url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null) {
            throw AiConfigurationException("AI Base URL 无效")
        }
        if (apiKey.isBlank()) {
            throw AiConfigurationException("尚未配置 API Key")
        }
        if (apiKey.any { it.code !in 33..126 }) {
            throw AiConfigurationException("API Key 格式无效")
        }
        endpoint = "${url.toString().trimEnd('/')}/chat/completions"
    }

    override suspend fun parseQuestions(text: String): List<DraftQuestion> =
        parseQuestions(text) {}

    override suspend fun parseQuestions(
        text: String,
        onProgress: (String) -> Unit,
    ): List<DraftQuestion> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext emptyList()
        val bounded = text.take(MAX_INPUT_CHARS)
        val chunks = splitTextIntoChunks(bounded, MAX_CHUNK_CHARS)
        if (chunks.isEmpty()) return@withContext emptyList()
        val accumulated = mutableListOf<DraftQuestion>()
        chunks.forEachIndexed { index, chunk ->
            currentCoroutineContext().ensureActive()
            val status = if (chunks.size == 1) {
                "正在调用 AI 识别题目…"
            } else {
                "正在调用 AI 识别题目（第 ${index + 1} / ${chunks.size} 批，已识别 ${accumulated.size} 题）…"
            }
            onProgress(status)
            accumulated += requestChunk(chunk)
        }
        accumulated
    }

    private fun requestChunk(chunk: String): List<DraftQuestion> {
        val requestJson = JSONObject()
            .put("model", model)
            .apply {
                if (OcrKeyResolver.isOfficialDeepSeekBaseUrl(normalizedBaseUrl)) {
                    putDeepSeekThinking(this, AiExplanationReasoningEffort.NONE)
                    put("max_tokens", MAX_OUTPUT_TOKENS)
                } else {
                    put("temperature", 0.1)
                }
            }
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
                            .put("content", chunk),
                    ),
            )
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(formatHttpError(response.code, body))
                }
                return parseResponse(body)
            }
        } catch (e: SocketTimeoutException) {
            throw IllegalStateException("AI 请求超时（超过 90 秒），请检查网络或缩小文件后重试")
        } catch (e: InterruptedIOException) {
            throw IllegalStateException("AI 请求超时（超过 90 秒），请检查网络或缩小文件后重试")
        } catch (e: IOException) {
            throw IllegalStateException("AI 网络连接失败，请检查网络后重试")
        }
    }

    private fun formatHttpError(code: Int, body: String): String {
        val serverMessage = runCatching { JSONObject(body) }.getOrNull()
            ?.optJSONObject("error")
            ?.optString("message")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let(::redactKey)
            ?.take(120)
        return when (code) {
            in 300..399 -> "AI 请求失败：HTTP $code（服务返回重定向，已中止）"
            401, 403 -> "AI 认证失败（HTTP $code）：API Key 无效或未授权，请检查设置"
            402 -> "AI 账户余额不足（HTTP 402），请充值后重试"
            429 -> "AI 请求过于频繁（HTTP 429），请稍后重试"
            in 500..599 -> "AI 服务端繁忙或异常（HTTP $code），请稍后重试"
            else -> "AI 请求失败：HTTP $code" + (serverMessage?.let { "（$it）" } ?: "")
        }
    }

    internal fun parseResponse(body: String): List<DraftQuestion> {
        val root = runCatching { JSONObject(body) }.getOrElse {
            throw IllegalStateException("AI 返回内容不是有效 JSON")
        }
        val choices = root.optJSONArray("choices")
            ?: throw IllegalStateException("AI 返回结果缺少 choices 字段")
        if (choices.length() == 0) {
            throw IllegalStateException("AI 未返回有效识别结果")
        }
        val firstChoice = choices.optJSONObject(0)
        val finishReason = firstChoice?.optString("finish_reason").orEmpty()
        val content = firstChoice
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
        return parseQuestionArray(content, truncatedByLength = finishReason == "length")
    }

    internal fun parseQuestionArray(content: String, truncatedByLength: Boolean = false): List<DraftQuestion> {
        val cleaned = content
            .replace(Regex("```[a-zA-Z]*\\s*"), "")
            .replace("```", "")
            .trim()
        if (cleaned.isBlank()) {
            throw IllegalStateException("AI 返回内容为空，未能解析出题目")
        }
        var explicitEmpty = false
        val items: List<JSONObject> = run {
            val objArray = if (cleaned.startsWith("{")) {
                runCatching { JSONObject(cleaned) }.getOrNull()?.optJSONArray("questions")
            } else null
            if (objArray != null) {
                if (objArray.length() == 0) explicitEmpty = true
                (0 until objArray.length()).mapNotNull { objArray.optJSONObject(it) }
            } else {
                val start = cleaned.indexOf('[')
                val end = cleaned.lastIndexOf(']')
                val fullArray = if (start >= 0 && end > start) {
                    runCatching { JSONArray(cleaned.substring(start, end + 1)) }.getOrNull()
                } else null
                if (fullArray != null) {
                    if (fullArray.length() == 0) explicitEmpty = true
                    (0 until fullArray.length()).mapNotNull { fullArray.optJSONObject(it) }
                } else {
                    rescueJsonObjects(cleaned)
                }
            }
        }
        if (explicitEmpty) return emptyList()
        val parsed = items.mapNotNull { item -> toDraftQuestion(item) }
        if (parsed.isEmpty()) {
            throw IllegalStateException(
                if (truncatedByLength) {
                    "AI 输出过长被截断，未能解析出题目"
                } else {
                    "AI 返回内容格式异常或被截断，未能解析出题目"
                },
            )
        }
        return parsed
    }

    private fun toDraftQuestion(item: JSONObject): DraftQuestion? {
        val stem = redactKey(item.optString("stem").trim())
        if (stem.isBlank()) return null
        val rawAnswer = redactKey(item.optString("answer").trim())
        val type = parseType(item.optString("type"), rawAnswer)
        val optionsArray = item.optJSONArray("options") ?: JSONArray()
        val options = buildList {
            for (optionIndex in 0 until optionsArray.length()) {
                val option = optionsArray.optJSONObject(optionIndex) ?: continue
                val label = option.optString("label").trim().uppercase()
                val text = redactKey(option.optString("text").trim())
                if (label.isNotBlank() && text.isNotBlank()) {
                    add(QuestionOption(label.take(1), text))
                }
            }
        }
        val normalizedAnswer = AnswerNormalizer.normalizeForStorage(type, rawAnswer)
        val returnedReasons = item.optJSONArray("reviewReasons")?.let { reasons ->
            buildList {
                for (reasonIndex in 0 until reasons.length()) {
                    val reason = redactKey(reasons.optString(reasonIndex).trim())
                    if (reason.isNotBlank()) add(reason)
                }
            }
        }.orEmpty()
        val tags = when (val rawTags = item.opt("tags")) {
            is JSONArray -> (0 until rawTags.length())
                .mapNotNull { i -> redactKey(rawTags.optString(i).trim()).takeIf { it.isNotBlank() } }
                .joinToString("、")
                .takeIf { it.isNotBlank() }
            is String -> redactKey(rawTags.trim()).takeIf { it.isNotBlank() && it != "null" }
            else -> null
        }
        return DraftQuestion(
            sourceNumber = redactKey(item.optString("sourceNumber").trim()),
            type = type,
            stem = stem,
            options = options,
            answer = normalizedAnswer,
            explanation = redactKey(item.optString("explanation").trim()),
            tags = tags,
            confidence = item.optDouble("confidence", 0.6).coerceIn(0.0, 1.0),
            needsReview = true,
            reviewReasons = (returnedReasons + "AI 识别结果，请人工确认").distinct(),
            rawSnippet = redactKey(item.toString()),
        )
    }

    private fun redactKey(value: String): String =
        if (apiKey.isNotEmpty()) value.replace(apiKey, "[已隐藏]") else value

    private fun parseType(rawType: String, rawAnswer: String): QuestionType {
        val normalized = rawType.uppercase()
        return when {
            "MULTI" in normalized || "多选" in rawType -> QuestionType.MULTI
            "JUDGE" in normalized || "判断" in rawType -> QuestionType.JUDGE
            "FILL" in normalized || "填空" in rawType -> QuestionType.FILL
            "ESSAY" in normalized || "问答" in rawType || "简答" in rawType -> QuestionType.ESSAY
            "SINGLE" in normalized || "单选" in rawType -> QuestionType.SINGLE
            AnswerNormalizer.normalizeJudge(rawAnswer) != null -> QuestionType.JUDGE
            AnswerNormalizer.normalizeLetters(rawAnswer).length > 1 -> QuestionType.MULTI
            else -> QuestionType.SINGLE
        }
    }

    private companion object {
        const val MAX_INPUT_CHARS = 120_000
        const val MAX_CHUNK_CHARS = 3_500
        const val MAX_OUTPUT_TOKENS = 8_192
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val QUESTION_START_REGEX = Regex(
            "(?m)^[ \t]*(?:第\\s*\\d+\\s*题|(?:\\d+|[一二三四五六七八九十]+)\\s*[.、．)）]|[(（]\\s*\\d+\\s*[)）])",
        )
        val PARAGRAPH_BREAK_REGEX = Regex("\\r?\\n[ \t]*\\r?\\n+")
        const val SYSTEM_PROMPT = """
你是题库结构化助手。请把用户提供的教材题目整理为 JSON 数组，不得编造答案。
每个元素字段：sourceNumber, type(SINGLE/MULTI/JUDGE/FILL/ESSAY), stem,
options(label/text 数组，最多 A-H), answer, explanation, tags, confidence(0-1),
needsReview, reviewReasons。
多选答案使用排序后的字母串，判断题答案用 true/false，填空题与问答题保留答案原文或要点。
无法确定的题目仍需输出，并在 reviewReasons 中说明。
只返回 JSON 数组，不要 Markdown 代码围栏或额外解释。
"""

        fun splitTextIntoChunks(text: String, maxChars: Int = MAX_CHUNK_CHARS): List<String> {
            if (text.length <= maxChars) return listOf(text)
            val questionBoundaries = QUESTION_START_REGEX.findAll(text).map { it.range.first }.toList()
            val paragraphBoundaries = PARAGRAPH_BREAK_REGEX.findAll(text).map { it.range.last + 1 }.toList()
            val chunks = mutableListOf<String>()
            var start = 0
            while (start < text.length) {
                var limit = minOf(start + maxChars, text.length)
                if (limit < text.length &&
                    Character.isHighSurrogate(text[limit - 1]) &&
                    Character.isLowSurrogate(text[limit])
                ) {
                    limit--
                }
                val boundary = if (limit == text.length) {
                    limit
                } else {
                    questionBoundaries.lastOrNull { it > start && it <= limit && text.substring(start, it).isNotBlank() }
                        ?: paragraphBoundaries.lastOrNull { it > start && it <= limit && text.substring(start, it).isNotBlank() }
                        ?: text.lastIndexOf('\n', limit - 1).takeIf { it > start && text.substring(start, it + 1).isNotBlank() }?.plus(1)
                        ?: limit
                }
                text.substring(start, boundary).trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
                start = boundary
            }
            return chunks
        }

        fun rescueJsonObjects(text: String): List<JSONObject> {
            val result = mutableListOf<JSONObject>()
            val start = text.indexOf('[').takeIf { it >= 0 } ?: 0
            var inString = false
            var escape = false
            var depth = 0
            var objStart = -1
            for (i in start until text.length) {
                val c = text[i]
                if (inString) {
                    if (escape) {
                        escape = false
                    } else if (c == '\\') {
                        escape = true
                    } else if (c == '"') {
                        inString = false
                    }
                } else {
                    when (c) {
                        '"' -> inString = true
                        '{' -> {
                            if (depth == 0) objStart = i
                            depth++
                        }
                        '}' -> {
                            if (depth > 0) {
                                depth--
                                if (depth == 0 && objStart >= 0) {
                                    runCatching { JSONObject(text.substring(objStart, i + 1)) }
                                        .getOrNull()
                                        ?.let(result::add)
                                    objStart = -1
                                }
                            }
                        }
                    }
                }
            }
            return result
        }

        fun defaultHttpClient(): OkHttpClient = SharedHttp.base.newBuilder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
