package com.xjb.linti.data.explanation

import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.data.prefs.SettingsStore
import com.xjb.linti.domain.Question
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
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

enum class AiExplanationError(val message: String, val needsSettings: Boolean = false) {
    NOT_CONFIGURED("尚未配置 AI", true),
    INVALID_CONFIGURATION("AI 配置无效", true),
    HTTPS_REQUIRED("AI 地址必须使用 HTTPS", true),
    UNAUTHORIZED("AI 认证失败（401/403）"),
    TIMEOUT("AI 请求超时"),
    NETWORK("网络请求失败"),
    RESPONSE("AI 返回格式错误或内容为空"),
    SERVER("AI 服务请求失败"),
    QUESTION_MISSING("题目已删除"),
}

class AiExplanationException(val reason: AiExplanationError) : Exception(reason.message)

// 普通类避免默认 toString 把密钥写入日志。
class AiExplanationConfig(val endpoint: String, val apiKey: String, val model: String, val provider: String)

fun explanationConfig(settings: SettingsStore): AiExplanationConfig {
    val snapshot = settings.snapshot()
    val key = settings.apiKeyForRequest()
    val base = snapshot.baseUrl.trim().trimEnd('/')
    if (key.isBlank() || base.isBlank()) throw AiExplanationException(AiExplanationError.NOT_CONFIGURED)
    if (!base.startsWith("https://", ignoreCase = true)) throw AiExplanationException(AiExplanationError.HTTPS_REQUIRED)
    val url = base.toHttpUrlOrNull()
    if (url == null || url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() ||
        url.query != null || url.fragment != null || key.any { it.code !in 33..126 }) {
        throw AiExplanationException(AiExplanationError.INVALID_CONFIGURATION)
    }
    val model = snapshot.model.trim().ifBlank {
        if (snapshot.provider == AiProvider.DEEPSEEK) "deepseek-chat" else "gpt-4o-mini"
    }
    if (model == key || (key.length >= 8 && (model.contains(key) || base.contains(key)))) {
        throw AiExplanationException(AiExplanationError.INVALID_CONFIGURATION)
    }
    return AiExplanationConfig("$base/chat/completions", key, model, snapshot.provider.name)
}

interface AiExplanationClient {
    suspend fun explain(question: Question, config: AiExplanationConfig): String
}

class OpenAiExplanationClient(
    httpClient: OkHttpClient = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build(),
) : AiExplanationClient {
    // 不允许重定向带走 Authorization，尤其不能降级到 HTTP。
    private val client = httpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()

    override suspend fun explain(question: Question, config: AiExplanationConfig): String = withContext(Dispatchers.IO) {
        val url = config.endpoint.toHttpUrlOrNull()
        if (url == null || url.scheme != "https" || url.username.isNotEmpty() || url.password.isNotEmpty() ||
            url.query != null || url.fragment != null) throw AiExplanationException(AiExplanationError.HTTPS_REQUIRED)
        if (config.apiKey.isBlank()) throw AiExplanationException(AiExplanationError.NOT_CONFIGURED)
        if (config.apiKey.any { it.code !in 33..126 }) throw AiExplanationException(AiExplanationError.INVALID_CONFIGURATION)
        val questionJson = JSONObject()
            .put("type", question.type.label)
            .put("stem", question.stem)
            .put("options", JSONArray().apply {
                question.options.forEach { put(JSONObject().put("label", it.label).put("text", it.text)) }
            })
            .put("answer", question.answer)
            .put("explanation", question.explanation)
        val body = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.1)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                .put(JSONObject().put("role", "user").put("content", questionJson.toString())))
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw AiExplanationException(AiExplanationError.UNAUTHORIZED)
                if (!response.isSuccessful) throw AiExplanationException(AiExplanationError.SERVER)
                val responseBody = response.body?.string().orEmpty()
                val content = try {
                    val root = JSONObject(responseBody)
                    val message = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    (message?.opt("content") as? String).orEmpty().replace(config.apiKey, "[已隐藏]").trim()
                } catch (_: Exception) {
                    throw AiExplanationException(AiExplanationError.RESPONSE)
                }
                if (content.isBlank()) throw AiExplanationException(AiExplanationError.RESPONSE)
                content
            }
        } catch (e: SocketTimeoutException) {
            throw AiExplanationException(AiExplanationError.TIMEOUT)
        } catch (e: IOException) {
            // OkHttp callTimeout 有时以 InterruptedIOException 表示。
            throw AiExplanationException(if (e is java.io.InterruptedIOException) AiExplanationError.TIMEOUT else AiExplanationError.NETWORK)
        }
    }

    private companion object {
        const val SYSTEM_PROMPT = "请依据给定的标准答案解释本题，不要更改答案。使用中文，简洁作答。请使用选项内容而不引用选项字母，适应选项顺序变化。"
    }
}
