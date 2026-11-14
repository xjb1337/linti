package com.xjb.linti.data.aigeneration

import com.xjb.linti.data.db.AiQuestionDraftEntity
import com.xjb.linti.data.db.JsonCodec
import com.xjb.linti.data.import.SplitFileMatcher
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** 原始字段保留空值和无效值；只有用户修正后才能通过。 */
data class AiDraftFields(
    val type: String = "",
    val stem: String = "",
    val options: List<QuestionOption> = emptyList(),
    val answer: String = "",
    val explanation: String = "",
    val invalidOptions: Boolean = false,
    val expectedType: String = "",
    val sourceExcerpt: String = "",
    val materialText: String = "",
    val acceptedAnswers: List<List<String>> = emptyList(),
    val material: Boolean = false,
    val generationProblem: String = "",
)

data class AiQuestionDraft(val id: Long, val bankId: Long, val fields: AiDraftFields, val problems: List<String>, val createdAt: Long)

object AiDraftValidator {
    fun validate(draft: AiDraftFields): List<String> = buildList {
        val type = QuestionType.entries.firstOrNull { it.name == draft.type && (draft.material || it in listOf(QuestionType.SINGLE, QuestionType.MULTI, QuestionType.JUDGE)) }
        if (type == null) add("题型无效")
        if (draft.expectedType.isNotBlank() && draft.type != draft.expectedType) add("题型与所选题型不一致")
        if (draft.invalidOptions) add("选项格式无效")
        if (draft.generationProblem.isNotBlank()) add(draft.generationProblem)
        if (draft.material && (draft.sourceExcerpt.isBlank() || !draft.materialText.contains(draft.sourceExcerpt))) add("出处片段不在材料中")
        if (draft.stem.isBlank()) add("题干为空")
        if (draft.explanation.isBlank()) add("待补解析")
        if (type == QuestionType.SINGLE || type == QuestionType.MULTI) {
            if (draft.options.size !in 2..8) add("选项数量须为 2–8 个")
            val labels = draft.options.map { it.label.trim().uppercase() }
            if (labels.any { it.length != 1 || it[0] !in 'A'..'H' }) add("选项标签须为 A–H")
            if (labels.distinct().size != labels.size) add("选项标签重复")
            if (draft.options.any { it.text.isBlank() }) add("选项内容为空")
            val answer = draft.answer.trim().uppercase()
            val letters = SplitFileMatcher.strictLetters(answer)
            if (answer.isBlank()) add("待补答案")
            else if (letters == null || letters.isEmpty()) add("答案格式无效")
            else {
                // 重复答案不能通过 distinct 归一化掩盖。
                val rawLetters = java.text.Normalizer.normalize(answer, java.text.Normalizer.Form.NFKC).filter { it in 'A'..'H' }
                if (rawLetters.length != rawLetters.toSet().size) add("答案选项重复")
                if (type == QuestionType.SINGLE && letters.length != 1) add("单选题须有一个答案")
                if (type == QuestionType.MULTI && letters.length < 2) add("多选题须至少有两个答案")
                if (letters.any { it.toString() !in labels }) add("答案不在选项中")
            }
        } else if (type == QuestionType.JUDGE) {
            if (draft.options.isNotEmpty()) add("判断题不能有选项")
            if (draft.answer.isBlank()) add("待补答案")
            else if (SplitFileMatcher.strictJudge(draft.answer) == null) add("判断题答案无效")
        } else if (type == QuestionType.FILL) {
            if (draft.options.isNotEmpty()) add("填空题不能有选项")
            val blanks = Regex("_{2,}|＿{2,}|（\\s*）|\\(\\s*\\)").findAll(draft.stem).count()
            if (blanks == 0 || blanks != draft.acceptedAnswers.size) add("空数与答案数不一致")
            if (draft.acceptedAnswers.any { it.isEmpty() || it.any(String::isBlank) }) add("待补填空答案")
        } else if (type == QuestionType.ESSAY) {
            if (draft.options.isNotEmpty()) add("问答题不能有选项")
            if (draft.answer.isBlank()) add("待补参考答案")
        } else if (draft.answer.isBlank()) add("待补答案")
    }.distinct()
}

object AiDraftCodec {
    fun encode(fields: AiDraftFields): String = JSONObject()
        .put("type", fields.type).put("stem", fields.stem)
        .put("options", JSONArray().apply { fields.options.forEach { put(JSONObject().put("label", it.label).put("text", it.text)) } })
        .put("answer", fields.answer).put("explanation", fields.explanation)
        .put("_invalidOptions", fields.invalidOptions).put("_expectedType", fields.expectedType)
        .put("sourceExcerpt", fields.sourceExcerpt).put("_materialText", fields.materialText)
        .put("acceptedAnswers", JSONArray(JsonCodec.encodeAnswerAlternatives(fields.acceptedAnswers)))
        .put("_material", fields.material).put("_generationProblem", fields.generationProblem).toString()

    fun decode(payload: String): AiDraftFields {
        val value = JSONObject(payload)
        val options = value.optJSONArray("options")
        return AiDraftFields(
            type = value.opt("type") as? String ?: "",
            stem = value.opt("stem") as? String ?: "",
            options = if (options == null) emptyList() else (0 until options.length()).map { index ->
                val option = options.optJSONObject(index)
                QuestionOption(option?.opt("label") as? String ?: "", option?.opt("text") as? String ?: "")
            },
            answer = value.opt("answer") as? String ?: "",
            explanation = value.opt("explanation") as? String ?: "",
            invalidOptions = options == null || value.optBoolean("_invalidOptions", false),
            expectedType = value.opt("_expectedType") as? String ?: "",
            sourceExcerpt = value.opt("sourceExcerpt") as? String ?: "",
            materialText = value.opt("_materialText") as? String ?: "",
            acceptedAnswers = JsonCodec.decodeAnswerAlternatives(value.optJSONArray("acceptedAnswers")?.toString()),
            material = value.optBoolean("_material", false),
            generationProblem = value.opt("_generationProblem") as? String ?: "",
        )
    }

    fun parseResponse(content: String, count: Int): List<AiDraftFields> {
        StrictJson.check(content)
        val tokener = JSONTokener(content)
        val root = tokener.nextValue() as? JSONObject ?: throw IllegalArgumentException("返回格式错误")
        if (tokener.nextClean() != '\u0000') throw IllegalArgumentException("返回格式错误")
        val questions = root.opt("questions") as? JSONArray ?: throw IllegalArgumentException("返回格式错误")
        if (questions.length() != count) throw IllegalArgumentException("返回格式错误")
        return (0 until questions.length()).map { index ->
            val obj = questions.optJSONObject(index) ?: JSONObject()
            decode(obj.toString())
        }
    }
}

fun AiQuestionDraftEntity.toDraft(): AiQuestionDraft = AiQuestionDraft(id, bankId, AiDraftCodec.decode(payload), JsonCodec.decodeStrings(problemsJson), createdAt)
