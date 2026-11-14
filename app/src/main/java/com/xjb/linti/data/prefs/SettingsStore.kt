package com.xjb.linti.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AiProvider(val label: String) {
    DEEPSEEK("DeepSeek"),
    OPENAI_COMPATIBLE("OpenAI 兼容"),
    CUSTOM("自定义"),
}

enum class AiExplanationReasoningEffort(val label: String, val apiValue: String) {
    NONE("不思考", "none"),
    LOW("低", "low"),
    HIGH("高", "high"),
    MAX("最高", "max"),
}

data class SettingsSnapshot(
    val provider: AiProvider = AiProvider.DEEPSEEK,
    val hasApiKey: Boolean = false,
    val apiKeyHint: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val localFirst: Boolean = true,
    val callAiOnlyWhenLocalFails: Boolean = true,
    val hasOcrApiKey: Boolean = false,
    val ocrApiKeyHint: String = "",
    val aiExplanationReasoningEffort: AiExplanationReasoningEffort = AiExplanationReasoningEffort.HIGH,
)

interface SettingsStore {
    fun snapshot(): SettingsSnapshot
    fun saveAiConfiguration(
        provider: AiProvider,
        apiKey: String?,
        baseUrl: String,
        model: String = "",
    )
    fun clearApiKey()
    fun setAiExplanationReasoningEffort(effort: AiExplanationReasoningEffort)
    fun setLocalFirst(enabled: Boolean)
    fun setCallAiOnlyWhenLocalFails(enabled: Boolean)
    fun apiKeyForRequest(): String
    fun saveOcrApiKey(key: String)
    fun clearOcrApiKey()
    fun ocrApiKeyForRequest(): String
}

class SecureSettingsStore(context: Context) : SettingsStore {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences by lazy { createPreferences() }

    override fun snapshot(): SettingsSnapshot {
        val key = prefs.getString(KEY_API_KEY, "").orEmpty()
        val ocrKey = prefs.getString(KEY_OCR_API_KEY, "").orEmpty()
        return SettingsSnapshot(
            provider = runCatching {
                AiProvider.valueOf(prefs.getString(KEY_PROVIDER, AiProvider.DEEPSEEK.name).orEmpty())
            }.getOrDefault(AiProvider.DEEPSEEK),
            hasApiKey = key.isNotBlank(),
            apiKeyHint = if (key.isNotBlank()) "已保存" else "",
            baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
            model = prefs.getString(KEY_MODEL, "").orEmpty(),
            localFirst = prefs.getBoolean(KEY_LOCAL_FIRST, true),
            callAiOnlyWhenLocalFails = prefs.getBoolean(KEY_AI_ONLY_ON_FAILURE, true),
            hasOcrApiKey = ocrKey.isNotBlank(),
            ocrApiKeyHint = if (ocrKey.length >= 4) "已保存（末尾 ${ocrKey.takeLast(4)}）" else if (ocrKey.isNotBlank()) "已保存" else "",
            aiExplanationReasoningEffort = runCatching {
                AiExplanationReasoningEffort.valueOf(
                    prefs.getString(KEY_AI_EXPLANATION_REASONING_EFFORT, AiExplanationReasoningEffort.HIGH.name).orEmpty(),
                )
            }.getOrDefault(AiExplanationReasoningEffort.HIGH),
        )
    }

    override fun saveAiConfiguration(
        provider: AiProvider,
        apiKey: String?,
        baseUrl: String,
        model: String,
    ) {
        val editor = prefs.edit()
            .putString(KEY_PROVIDER, provider.name)
            .putString(KEY_BASE_URL, baseUrl.trim())
            .putString(KEY_MODEL, model.trim())
        if (!apiKey.isNullOrBlank()) {
            editor.putString(KEY_API_KEY, apiKey.trim())
        }
        editor.apply()
    }

    override fun clearApiKey() {
        prefs.edit().remove(KEY_API_KEY).apply()
    }

    override fun setAiExplanationReasoningEffort(effort: AiExplanationReasoningEffort) {
        prefs.edit().putString(KEY_AI_EXPLANATION_REASONING_EFFORT, effort.name).apply()
    }

    override fun setLocalFirst(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOCAL_FIRST, enabled).apply()
    }

    override fun setCallAiOnlyWhenLocalFails(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_ONLY_ON_FAILURE, enabled).apply()
    }

    override fun apiKeyForRequest(): String =
        prefs.getString(KEY_API_KEY, "").orEmpty()

    override fun saveOcrApiKey(key: String) {
        prefs.edit().putString(KEY_OCR_API_KEY, key.trim()).apply()
    }

    override fun clearOcrApiKey() {
        prefs.edit().remove(KEY_OCR_API_KEY).apply()
    }

    override fun ocrApiKeyForRequest(): String =
        prefs.getString(KEY_OCR_API_KEY, "").orEmpty()

    private fun createPreferences(): SharedPreferences {
        return runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                SECURE_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // Keeps the app usable on devices with a broken keystore. Keys never leave the sandbox.
            appContext.getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    private companion object {
        const val SECURE_FILE_NAME = "linti_secure_settings"
        const val FALLBACK_FILE_NAME = "linti_settings"
        const val KEY_PROVIDER = "ai_provider"
        const val KEY_API_KEY = "ai_api_key"
        const val KEY_BASE_URL = "ai_base_url"
        const val KEY_MODEL = "ai_model"
        const val KEY_AI_EXPLANATION_REASONING_EFFORT = "ai_explanation_reasoning_effort"
        const val KEY_LOCAL_FIRST = "local_first"
        const val KEY_AI_ONLY_ON_FAILURE = "ai_only_on_failure"
        const val KEY_OCR_API_KEY = "deepseek_ocr_api_key"
    }
}
