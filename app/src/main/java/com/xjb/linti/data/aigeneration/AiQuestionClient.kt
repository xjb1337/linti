package com.xjb.linti.data.aigeneration

import com.xjb.linti.data.explanation.AiExplanationConfig
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.data.ocr.OcrKeyResolver
import com.xjb.linti.data.prefs.AiExplanationReasoningEffort
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.Dispatchers
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
import java.util.concurrent.TimeUnit

sealed interface AiQuestionSource {
    data class Topic(val text: String) : AiQuestionSource
    data class References(val questions: List<Question>) : AiQuestionSource
}

interface AiQuestionClient {
    suspend fun generate(source: AiQuestionSource, type: QuestionType, count: Int, config: AiExplanationConfig): List<AiDraftFields>
    suspend fun generateMaterial(batch: MaterialBatch, config: AiExplanationConfig): List<AiDraftFields> =
        throw UnsupportedOperationException()
}

class OpenAiQuestionClient(httpClient: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()) : AiQuestionClient {
    private val client = httpClient.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    override suspend fun generate(source: AiQuestionSource, type: QuestionType, count: Int, config: AiExplanationConfig): List<AiDraftFields> = withContext(Dispatchers.IO) {
        require(type in listOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE) && count in 1..10)
        val url = config.endpoint.toHttpUrlOrNull()
        if (url == null || url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null)
            throw AiExplanationException(AiExplanationError.HTTPS_REQUIRED)
        if (config.apiKey.isBlank()) throw AiExplanationException(AiExplanationError.NOT_CONFIGURED)
        if (config.apiKey.any { it.code !in 33..126 }) throw AiExplanationException(AiExplanationError.INVALID_CONFIGURATION)
        val input = JSONObject().put("type", type.name).put("count", count)
        when (source) {
            is AiQuestionSource.Topic -> { require(source.text.isNotBlank()); input.put("topic", source.text.trim()) }
            is AiQuestionSource.References -> {
                require(source.questions.size in 1..5)
                input.put("references", JSONArray().apply { source.questions.forEach { q ->
                    put(JSONObject().put("stem", q.stem).put("options", JSONArray().apply {
                        q.options.forEach { put(JSONObject().put("label", it.label).put("text", it.text)) }
                    }).put("answer", q.answer).put("explanation", q.explanation))
                } })
            }
        }
        val body = JSONObject().put("model", config.model).apply {
            if (OcrKeyResolver.isOfficialDeepSeekBaseUrl(config.baseUrl)) {
                put("reasoning_effort", config.reasoningEffort.apiValue)
                put("response_format", JSONObject().put("type", "json_object"))
            }
            if (config.reasoningEffort == AiExplanationReasoningEffort.NONE) put("temperature", 0.1)
        }.put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", input.toString())))
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw AiExplanationException(AiExplanationError.UNAUTHORIZED)
                if (!response.isSuccessful) throw AiExplanationException(AiExplanationError.SERVER)
                try {
                    val message = JSONObject(response.body?.string().orEmpty()).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    val content = (message?.opt("content") as? String) ?: throw IllegalArgumentException()
                    fun redact(value: String) = value.replace(config.apiKey, "[已隐藏]")
                    AiDraftCodec.parseResponse(content, count).map { fields ->
                        fields.copy(type = redact(fields.type), stem = redact(fields.stem),
                            options = fields.options.map { it.copy(label = redact(it.label), text = redact(it.text)) },
                            answer = redact(fields.answer), explanation = redact(fields.explanation), expectedType = type.name)
                    }
                } catch (_: Exception) { throw AiExplanationException(AiExplanationError.RESPONSE) }
            }
        } catch (e: IOException) {
            throw AiExplanationException(if (e is InterruptedIOException) AiExplanationError.TIMEOUT else AiExplanationError.NETWORK)
        }
    }

    override suspend fun generateMaterial(batch: MaterialBatch, config: AiExplanationConfig): List<AiDraftFields> = withContext(Dispatchers.IO) {
        require(batch.text.isNotBlank() && batch.counts.isNotEmpty() && batch.counts.values.all { it > 0 })
        val url = config.endpoint.toHttpUrlOrNull()
        if (url == null || url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() || url.query != null || url.fragment != null)
            throw AiExplanationException(AiExplanationError.HTTPS_REQUIRED)
        if (config.apiKey.isBlank()) throw AiExplanationException(AiExplanationError.NOT_CONFIGURED)
        if (config.apiKey.any { it.code !in 33..126 } || batch.text.contains(config.apiKey)) throw AiExplanationException(AiExplanationError.INVALID_CONFIGURATION)
        val counts = JSONObject().apply { batch.counts.forEach { (type, count) -> put(type.name, count) } }
        val input = JSONObject().put("material", batch.text).put("counts", counts)
        val body = JSONObject().put("model", config.model).apply {
            if (OcrKeyResolver.isOfficialDeepSeekBaseUrl(config.baseUrl)) put("reasoning_effort", config.reasoningEffort.apiValue)
            put("response_format", JSONObject().put("type", "json_object"))
            if (config.reasoningEffort == AiExplanationReasoningEffort.NONE) put("temperature", 0.1)
        }.put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", MATERIAL_PROMPT))
            .put(JSONObject().put("role", "user").put("content", input.toString())))
        val request = Request.Builder().url(url).header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw AiExplanationException(AiExplanationError.UNAUTHORIZED)
                if (!response.isSuccessful) throw AiExplanationException(AiExplanationError.SERVER)
                try {
                    val message = JSONObject(response.body?.string().orEmpty()).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    val content = message?.opt("content") as? String ?: throw IllegalArgumentException()
                    StrictJson.check(content)
                    val tokener = org.json.JSONTokener(content)
                    val root = tokener.nextValue() as? JSONObject ?: throw IllegalArgumentException()
                    if (tokener.nextClean() != '\u0000') throw IllegalArgumentException()
                    val questions = root.opt("questions") as? JSONArray ?: throw IllegalArgumentException()
                    val remaining = batch.counts.toMutableMap()
                    val result = mutableListOf<AiDraftFields>()
                    fun redact(value: String) = value.replace(config.apiKey, "[已隐藏]")
                    for (i in 0 until questions.length()) {
                        val obj = questions.optJSONObject(i) ?: JSONObject()
                        val decoded = AiDraftCodec.decode(obj.toString())
                        val type = QuestionType.entries.firstOrNull { it.name == decoded.type }
                        val available = if (type == null) 0 else remaining[type] ?: 0
                        if (type != null && available > 0) remaining[type] = available - 1
                        val answers = decoded.acceptedAnswers.map { alternatives -> alternatives.map(::redact) }
                        result += decoded.copy(type = redact(decoded.type), stem = redact(decoded.stem),
                            options = decoded.options.map { it.copy(label = redact(it.label), text = redact(it.text)) },
                            answer = if (type == QuestionType.FILL && answers.isNotEmpty()) answers.joinToString("；") { it.firstOrNull().orEmpty() } else redact(decoded.answer),
                            explanation = redact(decoded.explanation), sourceExcerpt = if (decoded.sourceExcerpt.contains(config.apiKey)) "" else decoded.sourceExcerpt,
                            acceptedAnswers = answers, materialText = batch.text, material = true,
                            generationProblem = if (available <= 0) "题型或数量不符合要求" else "")
                    }
                    remaining.forEach { (type, count) -> repeat(count) {
                        result += AiDraftFields(type = type.name, expectedType = type.name, material = true,
                            materialText = batch.text, generationProblem = "返回数量不足")
                    } }
                    result
                } catch (_: Exception) { throw AiExplanationException(AiExplanationError.RESPONSE) }
            }
        } catch (e: IOException) {
            throw AiExplanationException(if (e is InterruptedIOException) AiExplanationError.TIMEOUT else AiExplanationError.NETWORK)
        }
    }

    companion object {
        const val MATERIAL_PROMPT = "你是中文出题助手。只依据用户提供的材料和各题型数量出题，不使用材料以外的题库内容。只返回一个 JSON 对象，不要使用 Markdown 或附加文字。结构为 {\"questions\":[{\"type\":\"SINGLE|MULTI|JUDGE|FILL|ESSAY\",\"stem\":\"题干\",\"options\":[{\"label\":\"A\",\"text\":\"选项内容\"}],\"answer\":\"答案或问答参考要点\",\"acceptedAnswers\":[[\"标准答案\",\"同义写法\"]],\"explanation\":\"解析\",\"sourceExcerpt\":\"材料中的连续原文片段\"}]}。按 counts 指定的题型和数量出题；每题的 sourceExcerpt 必须逐字摘自本批材料。单选和多选有 2–8 个 A–H 选项，答案为选项标签，单选一个、多选至少两个；判断题答案为 true 或 false。填空题用连续下划线表示每个空，acceptedAnswers 按空的顺序列出每空标准答案及可接受同义写法。问答题在 answer 中提供参考答案要点。非选择题 options 为空数组，非填空题 acceptedAnswers 为空数组。每题须提供非空题干和解析。单选和多选的正确答案应在各选项位置中随机均匀分布。"
        const val SYSTEM_PROMPT = "你是中文出题助手。根据用户给出的主题或参照题，生成指定题型和数量的新题，不要照抄参照题。只返回一个 JSON 对象，不要使用 Markdown 或附加文字。结构必须为 {\"questions\":[{\"type\":\"SINGLE|MULTI|JUDGE\",\"stem\":\"题干\",\"options\":[{\"label\":\"A\",\"text\":\"选项内容\"}],\"answer\":\"答案\",\"explanation\":\"解析\"}]}。type 必须等于指定题型；单选和多选提供 2–8 个标签唯一的 A–H 选项，单选答案为一个标签，多选答案为至少两个标签的字符串；判断题 options 为空数组，answer 只能是 true 或 false 字符串。单选和多选的正确答案应在各选项位置中随机均匀分布，不要集中在同一位置。每题均须提供非空题干、答案和解析。"
    }
}
