package com.xjb.linti.data.aigeneration

import androidx.room.withTransaction
import com.xjb.linti.data.db.AiQuestionDraftEntity
import com.xjb.linti.data.db.JsonCodec
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.db.toDomain
import com.xjb.linti.data.db.toEntity
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.data.explanation.explanationConfig
import com.xjb.linti.data.prefs.SettingsStore
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOrigin
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.flow.map
import kotlin.random.Random

class AiQuestionRepository(private val database: LintiDatabase, private val settings: SettingsStore,
    private val client: AiQuestionClient, private val random: Random = Random.Default,
    private val now: () -> Long = System::currentTimeMillis) {
    private val drafts = database.aiQuestionDraftDao()
    fun observe(bankId: Long) = drafts.observeByBank(bankId).map { list -> list.map { it.toDraft() } }

    suspend fun generate(bankId: Long, topic: String?, referenceIds: List<Long>, type: QuestionType, count: Int) {
        require(type in listOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE) && count in 1..10)
        require((!topic.isNullOrBlank()) xor referenceIds.isNotEmpty())
        require(referenceIds.size <= 5 && referenceIds.distinct().size == referenceIds.size)
        require(database.questionBankDao().getById(bankId) != null)
        val source = if (!topic.isNullOrBlank()) AiQuestionSource.Topic(topic) else {
            val selected = database.questionDao().getByIds(referenceIds).associateBy { it.id }
            require(selected.size == referenceIds.size && selected.values.all { it.bankId == bankId })
            AiQuestionSource.References(referenceIds.map { selected.getValue(it).toDomain() })
        }
        val fields = client.generate(source, type, count, explanationConfig(settings))
        if (fields.size != count) throw AiExplanationException(AiExplanationError.RESPONSE)
        database.withTransaction {
            require(database.questionBankDao().getById(bankId) != null)
            drafts.insertAll(fields.map { result ->
                val field = AiDraftOptionShuffler.shuffleIfSafe(result.copy(expectedType = type.name), random)
                AiQuestionDraftEntity(bankId = bankId, payload = AiDraftCodec.encode(field),
                    problemsJson = JsonCodec.encodeStrings(AiDraftValidator.validate(field)), createdAt = now())
            })
        }
    }

    fun materialConfig() = explanationConfig(settings)

    fun serviceHost(config: com.xjb.linti.data.explanation.AiExplanationConfig = materialConfig()): String = config.endpoint.let { endpoint ->
        val host = okhttp3.HttpUrl.Companion.run { endpoint.toHttpUrlOrNull()?.host } ?: throw AiExplanationException(AiExplanationError.HTTPS_REQUIRED)
        if (host.contains(config.apiKey, ignoreCase = true)) throw AiExplanationException(AiExplanationError.INVALID_CONFIGURATION)
        host
    }

    suspend fun generateMaterialBatch(bankId: Long, batch: MaterialBatch, config: com.xjb.linti.data.explanation.AiExplanationConfig = materialConfig()) {
        require(database.questionBankDao().getById(bankId) != null)
        val fields = client.generateMaterial(batch, config)
        database.withTransaction {
            require(database.questionBankDao().getById(bankId) != null)
            drafts.insertAll(fields.map { result ->
                val field = AiDraftOptionShuffler.shuffleIfSafe(result, random)
                AiQuestionDraftEntity(bankId = bankId, payload = AiDraftCodec.encode(field),
                    problemsJson = JsonCodec.encodeStrings(AiDraftValidator.validate(field)), createdAt = now())
            })
        }
    }

    suspend fun update(id: Long, fields: AiDraftFields) = database.withTransaction {
        val draft = requireNotNull(drafts.get(id))
        drafts.update(draft.copy(payload = AiDraftCodec.encode(fields), problemsJson = JsonCodec.encodeStrings(AiDraftValidator.validate(fields))))
    }
    suspend fun discard(id: Long) = drafts.delete(id)
    suspend fun approve(id: Long) = approveAll(listOf(id))
    suspend fun approveAll(ids: List<Long>) = database.withTransaction {
        require(ids.isNotEmpty() && ids.distinct().size == ids.size)
        val records = ids.map { requireNotNull(drafts.get(it)) }
        val values = records.map { it to AiDraftCodec.decode(it.payload) }
        require(values.all { AiDraftValidator.validate(it.second).isEmpty() && JsonCodec.decodeStrings(it.first.problemsJson).isEmpty() }) { "请先修正草稿问题" }
        values.forEach { (record, field) ->
            val type = QuestionType.valueOf(field.type)
            val timestamp = now()
            database.questionDao().insert(Question(bankId = record.bankId, type = type, stem = field.stem.trim(),
                options = field.options.map { it.copy(label = it.label.trim().uppercase(), text = it.text.trim()) },
                answer = AnswerNormalizer.normalizeForStorage(type, field.answer), explanation = field.explanation.trim(),
                sourceFileName = "AI 出题", origin = QuestionOrigin.AI_GENERATED,
                sourceExcerpt = field.sourceExcerpt.takeIf { it.isNotBlank() }, acceptedAnswers = field.acceptedAnswers,
                createdAt = timestamp, updatedAt = timestamp).toEntity())
            drafts.delete(record.id)
        }
    }
}
