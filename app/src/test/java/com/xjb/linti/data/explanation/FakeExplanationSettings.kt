package com.xjb.linti.data.explanation

import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.data.prefs.AiExplanationReasoningEffort
import com.xjb.linti.data.prefs.SettingsSnapshot
import com.xjb.linti.data.prefs.SettingsStore

class FakeExplanationSettings(
    var key: String = "fictional-secret-key",
    var baseUrl: String = "https://localhost/v1",
    var model: String = "fictional-model",
    var provider: AiProvider = AiProvider.CUSTOM,
    var reasoningEffort: AiExplanationReasoningEffort = AiExplanationReasoningEffort.HIGH,
) : SettingsStore {
    override fun snapshot() = SettingsSnapshot(provider = provider, hasApiKey = key.isNotBlank(), baseUrl = baseUrl, model = model, aiExplanationReasoningEffort = reasoningEffort)
    override fun apiKeyForRequest() = key
    override fun saveAiConfiguration(provider: AiProvider, apiKey: String?, baseUrl: String, model: String) {
        this.provider = provider; this.key = apiKey ?: key; this.baseUrl = baseUrl; this.model = model
    }
    override fun clearApiKey() { key = "" }
    override fun setAiExplanationReasoningEffort(effort: AiExplanationReasoningEffort) { reasoningEffort = effort }
    override fun setLocalFirst(enabled: Boolean) = Unit
    override fun setCallAiOnlyWhenLocalFails(enabled: Boolean) = Unit
    override fun saveOcrApiKey(key: String) = Unit
    override fun clearOcrApiKey() = Unit
    override fun ocrApiKeyForRequest() = ""
}
