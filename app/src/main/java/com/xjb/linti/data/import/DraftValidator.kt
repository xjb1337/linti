package com.xjb.linti.data.import

import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.QuestionType

data class DraftValidation(
    val reasons: List<String>,
) {
    val needsReview: Boolean get() = reasons.isNotEmpty()
    val confidence: Double
        get() = when {
            reasons.isEmpty() -> 1.0
            reasons.any { it.contains("未找到答案") } -> 0.45
            reasons.any { it.contains("选项") || it.contains("答案字母") } -> 0.55
            else -> 0.68
        }
}

// 草稿硬性规则校验：与 LocalQuestionParser.parseBlock 的检查口径一致，
// 供分文件匹配后与人工编辑后按当前草稿重新验证使用。
// 由此产生的理由可通过 isOwnedReason 识别；其他来源的理由（如页眉页脚疑似、人工说明）合并时保留。
object DraftValidator {
    private const val EMPTY_STEM = "题干为空，请补充题目内容"
    private const val SHORT_STEM = "题干过短，请核对是否识别完整"
    private const val MISSING_ANSWER = "未找到答案，请手动确认"
    private const val TOO_FEW_OPTIONS = "选项不足（至少需要 2 个选项）"
    private const val UNRECOGNIZED_ANSWER = "答案格式无法识别为选项字母"
    private const val SINGLE_WITH_MULTI_LETTERS = "题型标注为单选，但答案包含多个选项字母"
    private const val MULTI_WITH_SINGLE_LETTER = "题型标注为多选，但答案仅有一个选项字母"
    private const val UNRECOGNIZED_JUDGE = "判断题答案无法归一为正确或错误"
    private const val AMBIGUOUS_TYPE = "题型不明确，已暂按单选题处理，请核对"

    private val FILL_BLANK = Regex("""_{2,}|＿{2,}|（\s*）|\(\s*\)""")

    fun validate(draft: DraftQuestion): DraftValidation {
        val reasons = mutableListOf<String>()
        val stem = draft.stem.trim()
        if (stem.isEmpty()) {
            reasons += EMPTY_STEM
        } else if (stem.length < 4) {
            reasons += SHORT_STEM
        }
        val answerRaw = AnswerNormalizer.normalizeFillForStorage(draft.answer)
        if (answerRaw.isEmpty()) {
            reasons += MISSING_ANSWER
        }
        val nonEmptyOptions = draft.options.filter { it.text.isNotBlank() }
        val optionLabels = nonEmptyOptions.map { it.label.uppercase() }
        val duplicateLabels = optionLabels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val emptyOptionLabels = draft.options.filter { it.text.isBlank() }.map { it.label }
        if ((draft.type == QuestionType.SINGLE || draft.type == QuestionType.MULTI) &&
            nonEmptyOptions.size < 2
        ) {
            reasons += TOO_FEW_OPTIONS
        }
        if (duplicateLabels.isNotEmpty()) {
            reasons += "选项字母重复：${duplicateLabels.sorted().joinToString("、")}"
        }
        if (emptyOptionLabels.isNotEmpty()) {
            reasons += "选项 ${emptyOptionLabels.joinToString("、")} 内容为空"
        }

        val letters = when (draft.type) {
            // 选择题人工改答按严格口径验证原始输入：整段去掉空白与分隔符后
            // 必须全是选项字母，不能用宽松 normalizeLetters 从文字里捞字母
            QuestionType.SINGLE, QuestionType.MULTI ->
                SplitFileMatcher.strictLetters(answerRaw).orEmpty()
            else -> AnswerNormalizer.normalizeLetters(answerRaw)
        }
        // 判断题同样严格：只接受明确别名，含说明的值（如「正确但原题另有条件」）不放行
        val judgeAnswer = SplitFileMatcher.strictJudge(answerRaw)
        when (draft.type) {
            QuestionType.SINGLE, QuestionType.MULTI -> {
                if (answerRaw.isNotEmpty() && letters.isEmpty()) {
                    reasons += UNRECOGNIZED_ANSWER
                }
                if (draft.type == QuestionType.SINGLE && letters.length > 1) {
                    reasons += SINGLE_WITH_MULTI_LETTERS
                }
                if (draft.type == QuestionType.MULTI && answerRaw.isNotEmpty() && letters.length == 1) {
                    reasons += MULTI_WITH_SINGLE_LETTER
                }
                val invalidLetters = letters.filter { letter ->
                    optionLabels.isNotEmpty() && letter.toString() !in optionLabels
                }
                if (invalidLetters.isNotEmpty()) {
                    reasons += "答案字母 ${invalidLetters.toList().joinToString("")} 不在选项中"
                }
            }
            QuestionType.JUDGE -> {
                if (answerRaw.isNotEmpty() && judgeAnswer == null) {
                    reasons += UNRECOGNIZED_JUDGE
                }
            }
            QuestionType.FILL, QuestionType.ESSAY -> Unit
        }

        if (draft.explicitType == null &&
            answerRaw.isEmpty() &&
            nonEmptyOptions.size < 2 &&
            !hasFillBlank(stem)
        ) {
            reasons += AMBIGUOUS_TYPE
        }
        return DraftValidation(reasons = reasons.distinct())
    }

    // 合并理由时去掉本校验器负责的旧理由（已修复的不再保留），其余来源理由原样保留。
    fun mergeReasons(existing: List<String>, validation: DraftValidation): List<String> =
        (existing.filterNot(::isOwnedReason) + validation.reasons).distinct()

    fun isOwnedReason(reason: String): Boolean = when {
        reason == EMPTY_STEM || reason == SHORT_STEM -> true
        reason == MISSING_ANSWER || reason == UNRECOGNIZED_ANSWER -> true
        reason.startsWith("选项") -> true
        reason == SINGLE_WITH_MULTI_LETTERS || reason == MULTI_WITH_SINGLE_LETTER -> true
        reason.startsWith("答案字母") -> true
        reason == UNRECOGNIZED_JUDGE || reason == AMBIGUOUS_TYPE -> true
        // OCR 严格答案复检产生的理由：修复后随重新验证解除（仅 OCR 导入路径会产生）
        reason.endsWith("不是有效的选项字母，已标待核对") -> true
        reason.endsWith("无法识别为正确或错误，已标待核对") -> true
        else -> false
    }

    fun hasFillBlank(stem: String): Boolean = FILL_BLANK.containsMatchIn(stem)
}
