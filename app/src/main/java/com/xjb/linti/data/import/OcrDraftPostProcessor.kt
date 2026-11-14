package com.xjb.linti.data.import

import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ParseResult
import com.xjb.linti.domain.QuestionType

data class OcrDraftPostResult(
    val drafts: List<DraftQuestion>,
    /** 草稿序号 → 来源页（1 起）；图片识别统一 1。 */
    val sourcePages: Map<Int, Int>,
    val warnings: List<String>,
)

/**
 * OCR 文本解析后的统一后处理（只转写，不做任何生成式补全）：
 * - 用现有严格校验规则（DraftValidator，与分文件编辑/保存口径一致）重验每个草稿，
 *   无效/缺失答案明确标待核对——不允许靠宽松字母提取把 OCR 杂文当有效答案；
 * - 依 rawSnippet/题干把草稿映射回来源页，供校对时「页码/原文对照」；
 * - 输入必须是按页序拼接后的整份文本统一解析的结果，跨页题不会被误拆。
 */
object OcrDraftPostProcessor {

    fun process(
        parseResult: ParseResult,
        pageTexts: List<String>,
        extraWarnings: List<String> = emptyList(),
    ): OcrDraftPostResult {
        val drafts = parseResult.questions.map { draft ->
            val validation = DraftValidator.validate(draft)
            val reasons = DraftValidator.mergeReasons(draft.reviewReasons, validation)
            val strictViolation = strictAnswerViolation(draft)
            val finalReasons = if (strictViolation != null) {
                (reasons + strictViolation).distinct()
            } else {
                reasons
            }
            draft.copy(
                needsReview = draft.needsReview || finalReasons.isNotEmpty(),
                reviewReasons = finalReasons,
                confidence = if (finalReasons.isEmpty()) draft.confidence else validation.confidence,
            )
        }
        val sourcePages = drafts.mapIndexedNotNull { index, draft ->
            locatePage(pageTexts, draft)?.let { page -> index to page }
        }.toMap()
        val warnings = buildList {
            addAll(extraWarnings)
            if (drafts.any { it.needsReview }) {
                add("OCR 只转写文字，题目与答案由解析规则识别；待核对题目须逐条确认后才能入库")
            }
        }
        return OcrDraftPostResult(
            drafts = drafts,
            sourcePages = sourcePages,
            warnings = warnings,
        )
    }

    /** 空白/全无题时返回 null，调用方不得打开空校对页。 */
    fun locatePage(pageTexts: List<String>, draft: DraftQuestion): Int? {
        val normalizedPages = pageTexts.map { collapseWhitespace(it) }
        val snippet = draft.rawSnippet?.let(::collapseWhitespace)?.takeIf { it.isNotBlank() }
        if (snippet != null) {
            normalizedPages.indexOfFirst { it.contains(snippet) }.takeIf { it >= 0 }
                ?.let { return it + 1 }
        }
        val stem = collapseWhitespace(draft.stem).takeIf { it.isNotBlank() }
        if (stem != null) {
            normalizedPages.indexOfFirst { it.contains(stem) }.takeIf { it >= 0 }
                ?.let { return it + 1 }
            if (stem.length >= STEM_MATCH_MIN) {
                val prefix = stem.take(STEM_MATCH_MIN)
                normalizedPages.indexOfFirst { it.contains(prefix) }.takeIf { it >= 0 }
                    ?.let { return it + 1 }
            }
        }
        return null
    }

    private fun collapseWhitespace(text: String): String =
        text.replace(WHITESPACE, "")

    /**
     * OCR 专属严格答案复检：解析器的行内答案允许宽松字母提取（普通导入既有行为，不改动），
     * 但 OCR 杂文（如「本题选B因为…」）不能借该通道变成有效答案。
     * 这里按 rawSnippet 找到原始答案行，用与分文件一致的严格口径复核；
     * 题型被人工改过或答案来自文末答案对时不适用，返回 null 不误伤。
     */
    private fun strictAnswerViolation(draft: DraftQuestion): String? {
        if (draft.type == QuestionType.FILL) return null
        val snippet = draft.rawSnippet ?: return null
        val remainder = snippet.lineSequence()
            .map { ANSWER_LINE.find(it)?.groupValues?.get(1)?.trim() }
            .lastOrNull { value -> value != null && value.isNotEmpty() }
            ?: return null
        // 同行紧跟解析/考点时只检查答案段
        val answerPart = remainder.split(EXPLANATION_INLINE).firstOrNull().orEmpty().trim()
        val cleaned = answerPart.trim { it.isWhitespace() || it in PUNCTUATION_TRIM_CHARS }
        if (cleaned.isEmpty()) return null
        return when (draft.type) {
            QuestionType.SINGLE, QuestionType.MULTI -> {
                if (SplitFileMatcher.strictLetters(cleaned) == null) {
                    "答案「${cleaned.take(12)}」不是有效的选项字母，已标待核对"
                } else {
                    null
                }
            }
            QuestionType.JUDGE -> {
                if (SplitFileMatcher.strictJudge(cleaned) == null) {
                    "判断答案「${cleaned.take(12)}」无法识别为正确或错误，已标待核对"
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private val ANSWER_LINE = Regex("""(?:【\s*答案\s*】|(?:参考|正确|标准)?答案\s*[:：])\s*(.*)""")
    private val EXPLANATION_INLINE = Regex("""(?:【\s*(?:答案)?解析\s*】|解析\s*[:：]|【\s*考点\s*】|考点\s*[:：])""")
    private const val PUNCTUATION_TRIM_CHARS = "。．.!！?？；;，,、 "

    private val WHITESPACE = Regex("\\s+")
    private const val STEM_MATCH_MIN = 10
}
