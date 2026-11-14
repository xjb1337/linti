package com.xjb.linti.data.import

import com.xjb.linti.domain.DraftQuestion

data class SplitDraftEditResult(
    val draft: DraftQuestion,
    // 本问题是否被这次编辑解决（解决方由调用方移除问题标记）
    val resolved: Boolean,
)

// 分文件校对的草稿重验证：匹配问题（splitProblems）与解析警告分开维护——
// 解决只清本问题对应的原因，其他独立理由（解析器警告、人工说明）保留；
// needsReview 始终由最终理由列表推导，不沿用合并前的旧值，避免问题解除后仍卡在待核对。
object SplitDraftResolver {
    // 匹配完成：把问题写回对应草稿（原因直接可见），待核对由问题与验证共同决定
    fun applyMatchProblems(draft: DraftQuestion, problem: SplitDraftProblem?): DraftQuestion {
        val validation = DraftValidator.validate(draft)
        val reasons = (
            DraftValidator.mergeReasons(draft.reviewReasons, validation) +
                (problem?.reason()?.let(::listOf) ?: emptyList())
            ).distinct()
        return draft.copy(
            reviewReasons = reasons,
            needsReview = reasons.isNotEmpty() || problem != null,
            confidence = validation.confidence,
        )
    }

    // 人工编辑：改答案/题型且验证通过才解除本问题；题干等无关编辑不解锁
    fun afterEdit(
        draft: DraftQuestion,
        old: DraftQuestion,
        problem: SplitDraftProblem?,
    ): SplitDraftEditResult {
        val validation = DraftValidator.validate(draft)
        val reasons = DraftValidator.mergeReasons(draft.reviewReasons, validation).distinct()
        val answerOrTypeChanged = draft.answer != old.answer || draft.type != old.type
        val answerNowValid = draft.answer.isNotBlank() &&
            validation.reasons.none { it.contains("答案") || it.contains("题型") }
        val activeProblem = problem?.takeIf { answerOrTypeChanged && answerNowValid }
        return if (activeProblem != null) {
            val finalReasons = reasons.filterNot { it == activeProblem.reason() }
            SplitDraftEditResult(
                draft = draft.copy(
                    reviewReasons = finalReasons,
                    needsReview = finalReasons.isNotEmpty(),
                    confidence = validation.confidence,
                ),
                resolved = true,
            )
        } else {
            val finalReasons = (reasons + (problem?.reason()?.let(::listOf) ?: emptyList())).distinct()
            SplitDraftEditResult(
                draft = draft.copy(
                    reviewReasons = finalReasons,
                    needsReview = finalReasons.isNotEmpty(),
                    confidence = validation.confidence,
                ),
                resolved = false,
            )
        }
    }

    // 冲突逐题解决（采用外部答案或保留题内答案）：只清冲突文案，其他独立理由保留
    fun afterConflictResolve(candidate: DraftQuestion, problem: SplitDraftProblem.Conflict): DraftQuestion {
        val validation = DraftValidator.validate(candidate)
        val reasons = DraftValidator
            .mergeReasons(candidate.reviewReasons, validation)
            .filterNot { it == problem.reason() }
            .distinct()
        return candidate.copy(
            reviewReasons = reasons,
            needsReview = reasons.isNotEmpty(),
            confidence = validation.confidence,
        )
    }
}
