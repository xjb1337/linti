package com.xjb.linti.data.db

import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.data.import.SplitDraftProblem
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import org.json.JSONArray
import org.json.JSONObject

data class ImportReviewSnapshot(
    val fileName: String,
    val method: ImportMethod,
    val kind: DocumentKind?,
    val drafts: List<DraftQuestion>,
    val warnings: List<String>,
    val failedCount: Int,
    val targetBankId: Long?,
    val newBankName: String,
    val isSplitFile: Boolean = false,
    val answerFileName: String = "",
    val splitProblems: Map<Int, SplitDraftProblem> = emptyMap(),
    val ocrSourceName: String? = null,
    val requiresValidAnswers: Boolean = false,
    val sourcePages: Map<Int, Int> = emptyMap(),
    val originalPreviewPath: String? = null,
    val pdfPreviewPath: String? = null,
)

/** 校对草稿会话的 JSON 编解码，用于未入库前退出后的断点恢复与重复编辑。 */
object ImportReviewCodec {
    fun encode(snapshot: ImportReviewSnapshot): String {
        val root = JSONObject()
            .put("fileName", snapshot.fileName)
            .put("method", snapshot.method.storageValue)
            .put("kind", snapshot.kind?.storageValue ?: JSONObject.NULL)
            .put("failedCount", snapshot.failedCount)
            .put("targetBankId", snapshot.targetBankId ?: JSONObject.NULL)
            .put("newBankName", snapshot.newBankName)
            .put("isSplitFile", snapshot.isSplitFile)
            .put("answerFileName", snapshot.answerFileName)
            .put("ocrSourceName", snapshot.ocrSourceName ?: JSONObject.NULL)
            .put("requiresValidAnswers", snapshot.requiresValidAnswers)
            .put("originalPreviewPath", snapshot.originalPreviewPath ?: JSONObject.NULL)
            .put("pdfPreviewPath", snapshot.pdfPreviewPath ?: JSONObject.NULL)
            .put("warnings", JSONArray(snapshot.warnings))
            .put("drafts", JSONArray().apply { snapshot.drafts.forEach { put(encodeDraft(it)) } })
            .put("splitProblems", encodeSplitProblems(snapshot.splitProblems))
            .put("sourcePages", encodeIntMap(snapshot.sourcePages))
        return root.toString()
    }

    fun decode(json: String): ImportReviewSnapshot? = runCatching {
        val root = JSONObject(json)
        val draftsArray = root.optJSONArray("drafts") ?: return@runCatching null
        val drafts = buildList {
            for (i in 0 until draftsArray.length()) {
                val obj = draftsArray.optJSONObject(i) ?: continue
                add(decodeDraft(obj))
            }
        }
        val warningsArray = root.optJSONArray("warnings")
        val warnings = buildList {
            if (warningsArray != null) {
                for (i in 0 until warningsArray.length()) {
                    warningsArray.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        val kindStorage = root.optString("kind", "").takeIf { it.isNotBlank() && !root.isNull("kind") }
        val targetBankId = if (root.isNull("targetBankId")) null else root.optLong("targetBankId").takeIf { it > 0 }
        ImportReviewSnapshot(
            fileName = root.optString("fileName", ""),
            method = ImportMethod.fromStorage(root.optString("method")),
            kind = kindStorage?.let { s -> DocumentKind.entries.firstOrNull { it.storageValue == s } },
            drafts = drafts,
            warnings = warnings,
            failedCount = root.optInt("failedCount", 0),
            targetBankId = targetBankId,
            newBankName = root.optString("newBankName", ""),
            isSplitFile = root.optBoolean("isSplitFile", false),
            answerFileName = root.optString("answerFileName", ""),
            splitProblems = decodeSplitProblems(root.optJSONObject("splitProblems")),
            ocrSourceName = root.optString("ocrSourceName", "").takeIf { it.isNotBlank() && !root.isNull("ocrSourceName") },
            requiresValidAnswers = root.optBoolean("requiresValidAnswers", false),
            sourcePages = decodeIntMap(root.optJSONObject("sourcePages")),
            originalPreviewPath = root.optString("originalPreviewPath", "").takeIf { it.isNotBlank() && !root.isNull("originalPreviewPath") },
            pdfPreviewPath = root.optString("pdfPreviewPath", "").takeIf { it.isNotBlank() && !root.isNull("pdfPreviewPath") },
        )
    }.getOrNull()

    fun extractImageFileNames(json: String): List<String> = runCatching {
        val root = JSONObject(json)
        val draftsArray = root.optJSONArray("drafts") ?: return@runCatching emptyList()
        buildList {
            for (i in 0 until draftsArray.length()) {
                val obj = draftsArray.optJSONObject(i) ?: continue
                val images = runCatching { JsonCodec.decodeImages(obj.optJSONArray("images")) }.getOrDefault(emptyList())
                images.forEach { add(it.fileName) }
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeDraft(draft: DraftQuestion): JSONObject = JSONObject()
        .put("sourceNumber", draft.sourceNumber)
        .put("type", draft.type.name)
        .put("explicitType", draft.explicitType?.name ?: JSONObject.NULL)
        .put("stem", draft.stem)
        .put("options", JSONArray(JsonCodec.encodeOptions(draft.options)))
        .put("answer", draft.answer)
        .put("explanation", draft.explanation)
        .put("tags", draft.tags ?: JSONObject.NULL)
        .put("knowledgePoint", draft.knowledgePoint ?: JSONObject.NULL)
        .put("confidence", draft.confidence)
        .put("needsReview", draft.needsReview)
        .put("reviewReasons", JSONArray(draft.reviewReasons))
        .put("sourceFileName", draft.sourceFileName)
        .put("rawSnippet", draft.rawSnippet ?: JSONObject.NULL)
        .put("acceptedAnswers", JSONArray(JsonCodec.encodeAnswerAlternatives(draft.acceptedAnswers)))
        .put("images", JsonCodec.encodeImages(draft.images))

    private fun decodeDraft(obj: JSONObject): DraftQuestion {
        val explicitTypeStr = obj.optString("explicitType", "").takeIf { it.isNotBlank() && !obj.isNull("explicitType") }
        val optionsJson = obj.optJSONArray("options")?.toString() ?: "[]"
        val reasonsArray = obj.optJSONArray("reviewReasons")
        val reasons = buildList {
            if (reasonsArray != null) {
                for (i in 0 until reasonsArray.length()) {
                    reasonsArray.optString(i).takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        val acceptedJson = obj.optJSONArray("acceptedAnswers")?.toString() ?: "[]"
        val images = runCatching { JsonCodec.decodeImages(obj.optJSONArray("images")) }.getOrDefault(emptyList())
        return DraftQuestion(
            sourceNumber = obj.optString("sourceNumber", ""),
            type = runCatching { QuestionType.valueOf(obj.optString("type")) }.getOrDefault(QuestionType.SINGLE),
            explicitType = explicitTypeStr?.let { runCatching { QuestionType.valueOf(it) }.getOrNull() },
            stem = obj.optString("stem", ""),
            options = decodeOptionsRaw(optionsJson),
            answer = obj.optString("answer", ""),
            explanation = obj.optString("explanation", ""),
            tags = obj.optString("tags", "").takeIf { it.isNotBlank() && !obj.isNull("tags") },
            knowledgePoint = obj.optString("knowledgePoint", "").takeIf { it.isNotBlank() && !obj.isNull("knowledgePoint") },
            confidence = obj.optDouble("confidence", 1.0),
            needsReview = obj.optBoolean("needsReview", false),
            reviewReasons = reasons,
            sourceFileName = obj.optString("sourceFileName", ""),
            rawSnippet = obj.optString("rawSnippet", "").takeIf { it.isNotBlank() && !obj.isNull("rawSnippet") },
            acceptedAnswers = JsonCodec.decodeAnswerAlternatives(acceptedJson),
            images = images,
        )
    }

    // 保留空文本选项（手动或半成品编辑中可能出现未填完的选项）
    private fun decodeOptionsRaw(json: String): List<QuestionOption> = runCatching {
        val array = JSONArray(json)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(QuestionOption(item.optString("label"), item.optString("text")))
            }
        }
    }.getOrDefault(emptyList())

    private fun encodeSplitProblems(map: Map<Int, SplitDraftProblem>): JSONObject = JSONObject().apply {
        map.forEach { (index, problem) ->
            val obj = when (problem) {
                is SplitDraftProblem.Conflict -> JSONObject()
                    .put("kind", "CONFLICT")
                    .put("inlineAnswer", problem.inlineAnswer)
                    .put("externalAnswer", problem.externalAnswer)
                is SplitDraftProblem.DuplicateQuestionNumber -> JSONObject()
                    .put("kind", "DUP_Q")
                    .put("number", problem.number)
                is SplitDraftProblem.DuplicateAnswerNumber -> JSONObject()
                    .put("kind", "DUP_A")
                    .put("number", problem.number)
                    .put("values", JSONArray(problem.values))
                is SplitDraftProblem.InvalidExternal -> JSONObject()
                    .put("kind", "INVALID_EXT")
                    .put("externalAnswer", problem.externalAnswer)
                    .put("reason", problem.reason)
            }
            put(index.toString(), obj)
        }
    }

    private fun decodeSplitProblems(obj: JSONObject?): Map<Int, SplitDraftProblem> {
        if (obj == null) return emptyMap()
        return buildMap {
            obj.keys().forEach { key ->
                val index = key.toIntOrNull() ?: return@forEach
                val item = obj.optJSONObject(key) ?: return@forEach
                val problem = when (item.optString("kind")) {
                    "CONFLICT" -> SplitDraftProblem.Conflict(
                        inlineAnswer = item.optString("inlineAnswer"),
                        externalAnswer = item.optString("externalAnswer"),
                    )
                    "DUP_Q" -> SplitDraftProblem.DuplicateQuestionNumber(item.optInt("number"))
                    "DUP_A" -> {
                        val arr = item.optJSONArray("values")
                        val values = buildList {
                            if (arr != null) {
                                for (i in 0 until arr.length()) add(arr.optString(i))
                            }
                        }
                        SplitDraftProblem.DuplicateAnswerNumber(item.optInt("number"), values)
                    }
                    "INVALID_EXT" -> SplitDraftProblem.InvalidExternal(
                        externalAnswer = item.optString("externalAnswer"),
                        reason = item.optString("reason"),
                    )
                    else -> null
                }
                if (problem != null) put(index, problem)
            }
        }
    }

    private fun encodeIntMap(map: Map<Int, Int>): JSONObject = JSONObject().apply {
        map.forEach { (k, v) -> put(k.toString(), v) }
    }

    private fun decodeIntMap(obj: JSONObject?): Map<Int, Int> {
        if (obj == null) return emptyMap()
        return buildMap {
            obj.keys().forEach { key ->
                val k = key.toIntOrNull() ?: return@forEach
                put(k, obj.optInt(key))
            }
        }
    }
}
