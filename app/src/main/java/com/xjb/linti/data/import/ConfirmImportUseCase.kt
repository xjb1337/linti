package com.xjb.linti.data.import

import com.xjb.linti.data.db.toQuestion
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionBank

data class ConfirmTarget(
    val bankId: Long? = null,
    val newBankName: String = "",
)

data class ConfirmedImport(
    val bankId: Long,
    val questionIds: List<Long>,
    val pendingCount: Int,
)

sealed interface ConfirmImportResult {
    data class Success(val value: ConfirmedImport) : ConfirmImportResult
    data class Failure(val message: String) : ConfirmImportResult
}

interface ImportPersistence {
    suspend fun <T> inImportTransaction(block: suspend () -> T): T
    suspend fun getImportBank(bankId: Long): QuestionBank?
    suspend fun createImportBank(name: String, now: Long): Long
    suspend fun insertImportQuestions(questions: List<Question>): List<Long>
    suspend fun getImportJobForConfirm(jobId: Long): ImportJob?
    suspend fun updateImportJobForConfirm(job: ImportJob)
}

class ConfirmImportUseCase(
    private val persistence: ImportPersistence,
) {
    suspend fun execute(
        drafts: List<DraftQuestion>,
        target: ConfirmTarget,
        sourceFileName: String,
        importJobId: Long?,
        now: Long = System.currentTimeMillis(),
        onlyValidAnswers: Boolean = false,
    ): ConfirmImportResult {
        // 分文件导入门槛：未解决、无答案或答案无法通过严格验证的草稿不得混入可练习题。
        // 这里独立于 VM 过滤，防止保存入口被绕过；按当前草稿重新验证原始输入。
        val effectiveDrafts = if (onlyValidAnswers) {
            drafts.filter { draft ->
                !draft.needsReview &&
                    AnswerNormalizer.normalizeForStorage(draft.type, draft.answer).isNotBlank() &&
                    DraftValidator.validate(draft).reasons.isEmpty()
            }
        } else {
            drafts
        }
        if (effectiveDrafts.isEmpty()) {
            return ConfirmImportResult.Failure(
                if (onlyValidAnswers && drafts.isNotEmpty()) {
                    "没有可入库的题目：待核对或缺少答案的题目不会入库"
                } else {
                    "没有可入库的题目"
                },
            )
        }
        val targetBankId = target.bankId
        if (targetBankId == null && target.newBankName.isBlank()) {
            return ConfirmImportResult.Failure("请选择目标题库或填写新题库名称")
        }

        return persistence.inImportTransaction {
            val bankId = if (targetBankId != null) {
                if (persistence.getImportBank(targetBankId) == null) {
                    return@inImportTransaction ConfirmImportResult.Failure("目标题库不存在")
                }
                targetBankId
            } else {
                persistence.createImportBank(target.newBankName.trim(), now)
            }

            val questions = effectiveDrafts.map { draft ->
                draft.toQuestion(
                    bankId = bankId,
                    now = now,
                    fallbackSourceFileName = sourceFileName,
                )
            }
            val ids = persistence.insertImportQuestions(questions)
            val pendingCount = effectiveDrafts.count { it.needsReview }

            if (importJobId != null) {
                persistence.getImportJobForConfirm(importJobId)?.let { job ->
                    persistence.updateImportJobForConfirm(
                        job.copy(
                            status = ImportStatus.CONFIRMED,
                            targetBankId = bankId,
                            confirmedCount = effectiveDrafts.size,
                            pendingCount = pendingCount,
                        ),
                    )
                }
            }
            ConfirmImportResult.Success(
                ConfirmedImport(
                    bankId = bankId,
                    questionIds = ids,
                    pendingCount = pendingCount,
                ),
            )
        }
    }
}
