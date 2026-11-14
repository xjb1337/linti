package com.xjb.linti.data.import

import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.QuestionType
import java.text.Normalizer

// 分文件导入中绑定到具体草稿的问题；都要求人工逐题解决，不能靠无关编辑抹掉。
sealed interface SplitDraftProblem {
    data class Conflict(
        val inlineAnswer: String,
        val externalAnswer: String,
    ) : SplitDraftProblem

    data class DuplicateQuestionNumber(val number: Int) : SplitDraftProblem

    data class DuplicateAnswerNumber(
        val number: Int,
        val values: List<String>,
    ) : SplitDraftProblem

    data class InvalidExternal(
        val externalAnswer: String,
        val reason: String,
    ) : SplitDraftProblem
}

fun SplitDraftProblem.reason(): String = when (this) {
    is SplitDraftProblem.Conflict ->
        "答案文件答案「$externalAnswer」与题内答案「$inlineAnswer」不一致，请选择保留的答案"
    is SplitDraftProblem.DuplicateQuestionNumber ->
        "试题文件中题号 $number 重复，未自动匹配答案文件，请手动填写答案"
    is SplitDraftProblem.DuplicateAnswerNumber ->
        "答案文件中题号 $number 出现 ${values.size} 条（${values.joinToString(" / ")}），未自动匹配，请手动填写答案"
    is SplitDraftProblem.InvalidExternal ->
        "答案文件的答案「$externalAnswer」无法用于本题：$reason"
}

data class SplitMatchOutput(
    val drafts: List<DraftQuestion>,
    val problems: Map<Int, SplitDraftProblem>,
    val warnings: List<String>,
)

// 分文件匹配：只用两边明确写出的正整数原题号作键（兼容全角与前导零），
// 不按行序、偏移、补号或排序后 zip。试题编号重复、答案编号重复（含同值）的号码
// 一律不自动匹配；行内答案与外部答案冲突时保留题内答案并把两个值交给人工决定。
object SplitFileMatcher {
    fun match(
        questions: List<DraftQuestion>,
        answers: List<AnswerEntry>,
    ): SplitMatchOutput {
        val drafts = questions.toMutableList()
        val problems = mutableMapOf<Int, SplitDraftProblem>()

        val numberKeys = questions.map { normalizeQuestionNumber(it.sourceNumber) }
        val questionKeyCounts = numberKeys.filterNotNull().groupingBy { it }.eachCount()
        val duplicateQuestionKeys = questionKeyCounts.filterValues { it > 1 }.keys

        val answerGroups = answers.groupBy { it.number }
        val duplicateAnswerKeys = answerGroups.filterValues { it.size > 1 }.keys
        val answerValues = answerGroups.mapValues { (_, group) -> group.map { it.rawAnswer.trim() } }

        questions.forEachIndexed { index, draft ->
            val key = numberKeys[index] ?: return@forEachIndexed
            if (key in duplicateQuestionKeys) {
                problems[index] = SplitDraftProblem.DuplicateQuestionNumber(key)
                return@forEachIndexed
            }
            if (key in duplicateAnswerKeys) {
                problems[index] = SplitDraftProblem.DuplicateAnswerNumber(
                    number = key,
                    values = answerValues.getValue(key).map { it.ifBlank { "空" } },
                )
                return@forEachIndexed
            }
            val externalRaw = answerGroups[key]?.firstOrNull()?.rawAnswer?.trim().orEmpty()
            if (externalRaw.isEmpty()) return@forEachIndexed

            if (draft.answer.isNotBlank()) {
                if (!sameNormalizedValue(draft.type, draft.answer, externalRaw)) {
                    problems[index] = SplitDraftProblem.Conflict(
                        inlineAnswer = draft.answer,
                        externalAnswer = externalRaw,
                    )
                }
                return@forEachIndexed
            }
            val applied = applyExternalAnswer(draft, externalRaw)
            if (applied != null) {
                drafts[index] = applied
            } else {
                problems[index] = SplitDraftProblem.InvalidExternal(
                    externalAnswer = externalRaw,
                    reason = invalidExternalReason(draft, externalRaw),
                )
            }
        }

        val warnings = mutableListOf<String>()
        val questionKeys = numberKeys.filterNotNull().toSet()
        val orphanKeys = answerGroups.keys.filter { it !in questionKeys }.sorted()
        if (orphanKeys.isNotEmpty()) {
            warnings += "答案文件题号 ${summarizeNumbers(orphanKeys)} 在试题文件中没有对应题目，未生成题目"
        }
        if (duplicateAnswerKeys.isNotEmpty()) {
            warnings += "答案文件题号 ${summarizeNumbers(duplicateAnswerKeys.sorted())} 重复，对应题目未自动匹配"
        }
        if (duplicateQuestionKeys.isNotEmpty()) {
            warnings += "试题文件题号 ${summarizeNumbers(duplicateQuestionKeys.sorted())} 重复，对应题目未自动匹配"
        }
        return SplitMatchOutput(
            drafts = drafts,
            problems = problems,
            warnings = warnings,
        )
    }

    // 外部答案按题型严格校验后填入；不合法返回 null（不填入、不伪装成已匹配）。
    // 选项字母必须真实存在于本题选项；显式单选不可填多字母、显式多选不可降级；
    // 判断只接受现有明确别名；填空沿用既有存储规则并保留中文。
    fun applyExternalAnswer(draft: DraftQuestion, externalRaw: String): DraftQuestion? {
        val optionLabels = draft.options
            .filter { it.text.isNotBlank() }
            .map { it.label.uppercase() }
            .toSet()
        val letters = strictLetters(externalRaw)
        val judgeAnswer = strictJudge(externalRaw)

        val explicit = draft.explicitType
        if (explicit != null) {
            return when (explicit) {
                QuestionType.SINGLE -> letters
                    ?.takeIf { it.length == 1 && optionLabels.isNotEmpty() && it[0].toString() in optionLabels }
                    ?.let { commitAnswer(draft, answer = it) }
                QuestionType.MULTI -> letters
                    ?.takeIf { it.length >= 2 && optionLabels.isNotEmpty() && it.all { letter -> letter.toString() in optionLabels } }
                    ?.let { commitAnswer(draft, answer = it) }
                QuestionType.JUDGE -> judgeAnswer?.let { commitAnswer(draft, answer = it) }
                QuestionType.FILL, QuestionType.ESSAY -> commitAnswer(
                    draft,
                    answer = AnswerNormalizer.normalizeFillForStorage(externalRaw),
                )
            }
        }

        // 无显式题型：用合法外部答案与题目结构参与推断。
        // 先看字母能否落在选项上；落不上再尝试判断别名——这样无选项题的
        // F/X/×（F 同时是合法选项字母）也能正确推断为判断题，
        // 而有 A–D 选项时 T/F 仍按既有歧义保护不强转判断。
        if (DraftValidator.hasFillBlank(draft.stem)) {
            return commitAnswer(
                draft,
                type = QuestionType.FILL,
                answer = AnswerNormalizer.normalizeFillForStorage(externalRaw),
            )
        }
        if (letters != null &&
            optionLabels.isNotEmpty() &&
            letters.all { letter -> letter.toString() in optionLabels }
        ) {
            val type = if (letters.length >= 2) QuestionType.MULTI else QuestionType.SINGLE
            return commitAnswer(draft, type = type, answer = letters)
        }
        if (judgeAnswer != null) {
            val normalizedRaw = Normalizer.normalize(externalRaw, Normalizer.Form.NFKC)
                .trim()
                .uppercase()
            val ambiguousBooleanLetter = normalizedRaw in setOf("T", "F") && optionLabels.size >= 2
            if (!ambiguousBooleanLetter) {
                return commitAnswer(draft, type = QuestionType.JUDGE, answer = judgeAnswer)
            }
            return null
        }
        return null
    }

    private fun commitAnswer(
        draft: DraftQuestion,
        type: QuestionType = draft.type,
        answer: String,
    ): DraftQuestion {
        val candidate = draft.copy(type = type, answer = answer)
        val validation = DraftValidator.validate(candidate)
        return candidate.copy(
            reviewReasons = DraftValidator.mergeReasons(candidate.reviewReasons, validation),
            needsReview = validation.needsReview ||
                candidate.reviewReasons.any { !DraftValidator.isOwnedReason(it) },
            confidence = validation.confidence,
        )
    }

    // 行内与外部答案的同值比较：外部值先做严格识别，识别不了就按冲突处理，
    // 不用宽松 normalizeLetters 从无关文本里捞字母。
    private fun sameNormalizedValue(type: QuestionType, inline: String, external: String): Boolean {
        return when (type) {
            QuestionType.SINGLE, QuestionType.MULTI -> {
                val externalLetters = strictLetters(external) ?: return false
                externalLetters == AnswerNormalizer.normalizeLetters(inline)
            }
            QuestionType.JUDGE -> {
                val externalJudge = strictJudge(external) ?: return false
                externalJudge == AnswerNormalizer.normalizeJudge(inline)
            }
            QuestionType.FILL, QuestionType.ESSAY ->
                AnswerNormalizer.normalizeFillForComparison(inline) ==
                    AnswerNormalizer.normalizeFillForComparison(external)
        }
    }

    private fun invalidExternalReason(draft: DraftQuestion, externalRaw: String): String {
        val optionLabels = draft.options
            .filter { it.text.isNotBlank() }
            .map { it.label.uppercase() }
            .toSet()
        val letters = strictLetters(externalRaw)
        val explicit = draft.explicitType
        return when {
            explicit == QuestionType.SINGLE && letters != null && letters.length > 1 ->
                "单选题答案只能有一个选项字母"
            explicit == QuestionType.MULTI && letters != null && letters.length == 1 ->
                "多选题答案至少需要两个选项字母"
            explicit == QuestionType.JUDGE ->
                "判断题答案只接受 正确/错误/对/错/√/×/T/F 等明确写法"
            letters != null && optionLabels.isEmpty() ->
                "本题没有选项，无法使用选项字母答案"
            letters != null ->
                "答案字母不在本题选项中"
            draft.explicitType == null &&
                strictJudge(externalRaw) != null &&
                optionLabels.size >= 2 -> "答案 T/F 与本题选项有歧义，无法自动判断"
            else -> "无法识别为可用答案，请在校对中手动填写或调整题型"
        }
    }

    // 严格字母识别：整段去掉空白与常见分隔符后必须全是 A–H 选项字母（至多 8 个），
    // 含其他字符（数字、汉字等）一律视为不是选项字母。
    internal fun strictLetters(raw: String): String? {
        if (raw.isBlank()) return null
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).uppercase()
        val compact = normalized.filter { char ->
            !char.isWhitespace() && char !in "、,，;；:：/．.·-"
        }
        if (compact.isEmpty() || !compact.matches(Regex("[A-H]{1,8}"))) return null
        return AnswerNormalizer.normalizeLetters(compact)
    }

    // 判断答案只接受现有明确别名（与 AnswerNormalizer.normalizeJudge 的精确别名一致）。
    internal fun strictJudge(raw: String): String? {
        if (raw.isBlank()) return null
        val value = Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .trim()
            .lowercase()
            .trim('。', '.', '！', '!', '；', ';', ' ')
        return when {
            value in setOf("错误", "错", "×", "x", "f", "false", "否", "不正确") -> "false"
            value in setOf("正确", "对", "√", "t", "true", "是") -> "true"
            else -> null
        }
    }

    private fun summarizeNumbers(numbers: List<Int>): String {
        val shown = numbers.take(12).joinToString("、")
        return if (numbers.size > 12) "$shown 等 ${numbers.size} 个" else shown
    }
}
