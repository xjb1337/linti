package com.xjb.linti.data.ocr

import com.xjb.linti.data.prefs.AiProvider
import java.net.URI

/**
 * DeepSeek OCR 密钥来源决策。
 *
 * 规则：OCR 只把密钥发给硬编码的官方端点（api.deepseek.com）。
 * - 优先使用独立配置的 DeepSeek OCR Key；
 * - 仅当 AI 配置明确指向官方 DeepSeek（HTTPS + 官方 host）时才复用 AI Key，
 *   防止第三方中转/自建服务的 Key 被发送给 DeepSeek 官方；
 * - 都没有则视为未配置，UI 引导去设置页，不发请求。
 */
sealed interface OcrKeySource {
    /** 独立配置的 DeepSeek OCR Key。 */
    data object Dedicated : OcrKeySource

    /** 复用 AI 服务的 Key（仅官方 DeepSeek 配置时）。 */
    data object ReuseAi : OcrKeySource

    /** 无可用 Key：不发请求。 */
    data object None : OcrKeySource
}

object OcrKeyResolver {

    /**
     * 严格判断 AI Base URL 是否指向官方 DeepSeek：
     * 必须 HTTPS、host 精确为 api.deepseek.com、path 仅为空/根/一级 v1。
     * 自定义中转、拼写变体、http 明文一律不算官方。
     */
    fun isOfficialDeepSeekBaseUrl(rawBaseUrl: String?): Boolean {
        val trimmed = rawBaseUrl?.trim().orEmpty()
        if (trimmed.isEmpty()) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        if (host != OFFICIAL_HOST) return false
        if (uri.port !in listOf(-1, 443)) return false
        val path = uri.rawPath ?: ""
        if (uri.rawQuery != null || uri.fragment != null) return false
        return path.isEmpty() || path == "/" || path == "/v1" || path == "/v1/"
    }

    /**
     * @param provider 当前 AI 服务商；baseUrl AI Base URL；aiKeyPresent 是否已存 AI Key；
     * @param dedicatedKeyPresent 是否已存独立 OCR Key。
     */
    fun resolve(
        provider: AiProvider?,
        baseUrl: String?,
        aiKeyPresent: Boolean,
        dedicatedKeyPresent: Boolean,
    ): OcrKeySource = when {
        dedicatedKeyPresent -> OcrKeySource.Dedicated
        provider == AiProvider.DEEPSEEK && aiKeyPresent && isOfficialDeepSeekBaseUrl(baseUrl) ->
            OcrKeySource.ReuseAi
        else -> OcrKeySource.None
    }

    private const val OFFICIAL_HOST = "api.deepseek.com"
}
