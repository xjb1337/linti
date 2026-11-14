package com.xjb.linti.data.explanation

import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.data.prefs.SettingsSnapshot
import com.xjb.linti.data.prefs.SettingsStore

class FakeExplanationSettings(
    var key: String = "fictional-secret-key",
    var baseUrl: String = "https://localhost/v1",
    var model: String = "fictional-model",
) : SettingsStore {
    override fun snapshot() = SettingsSnapshot(provider = AiProvider.CUSTOM, hasApiKey = key.isNotBlank(), baseUrl = baseUrl, model = model)
    override fun apiKeyForRequest() = key
    override fun saveAiConfiguration(provider: AiProvider, apiKey: String?, baseUrl: String, model: String) {
        this.key = apiKey ?: key; this.baseUrl = baseUrl; this.model = model
    }
    override fun clearApiKey() { key = "" }
    override fun setLocalFirst(enabled: Boolean) = Unit
    override fun setCallAiOnlyWhenLocalFails(enabled: Boolean) = Unit
    override fun saveOcrApiKey(key: String) = Unit
    override fun clearOcrApiKey() = Unit
    override fun ocrApiKeyForRequest() = ""
}
