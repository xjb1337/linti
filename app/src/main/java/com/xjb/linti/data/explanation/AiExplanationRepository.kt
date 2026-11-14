package com.xjb.linti.data.explanation

import androidx.room.withTransaction
import com.xjb.linti.data.db.AiExplanationEntity
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.db.toDomain
import com.xjb.linti.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class AiExplanationRecord(val explanation: AiExplanationEntity, val stale: Boolean)

class AiExplanationRepository(
    private val database: LintiDatabase,
    private val settings: SettingsStore,
    private val client: AiExplanationClient,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val explanations = database.aiExplanationDao()
    private val questions = database.questionDao()

    fun configurationError(): AiExplanationError? = try {
        explanationConfig(settings)
        null
    } catch (e: AiExplanationException) {
        e.reason
    }

    fun observe(questionId: Long): Flow<AiExplanationRecord?> = combine(
        explanations.observe(questionId), questions.observeById(questionId),
    ) { explanation, question ->
        if (explanation == null || question == null) null
        else AiExplanationRecord(explanation, question.updatedAt > explanation.questionUpdatedAt)
    }

    suspend fun generate(questionId: Long) {
        val config = explanationConfig(settings)
        val question = questions.getById(questionId)?.toDomain()
            ?: throw AiExplanationException(AiExplanationError.QUESTION_MISSING)
        val content = client.explain(question, config).replace(config.apiKey, "[已隐藏]").trim()
        if (content.isBlank()) throw AiExplanationException(AiExplanationError.RESPONSE)
        database.withTransaction {
            if (questions.getById(questionId) == null) throw AiExplanationException(AiExplanationError.QUESTION_MISSING)
            explanations.upsert(AiExplanationEntity(questionId, content, config.model, config.provider, question.updatedAt, now()))
        }
    }

    suspend fun delete(questionId: Long) = explanations.delete(questionId)
}
