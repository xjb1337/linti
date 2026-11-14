package com.xjb.linti.data.import

import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitDraftResolverTest {
    private val conflictProblem = SplitDraftProblem.Conflict(inlineAnswer = "A", externalAnswer = "C")
    private val headerReason = "疑似包含页眉、页脚或试卷标题，请核对题目边界"

    // 匹配阶段写回：冲突原因进入草稿原因列表（与解析警告分开维护）
    private fun flaggedDraft(extraReasons: List<String> = emptyList()): DraftQuestion {
        val draft = DraftQuestion(
            sourceNumber = "1",
            type = QuestionType.SINGLE,
            explicitType = QuestionType.SINGLE,
            stem = "冲突解决验收的虚构题干是否完整？",
            options = listOf(
                QuestionOption("A", "甲"),
                QuestionOption("B", "乙"),
                QuestionOption("C", "丙"),
                QuestionOption("D", "丁"),
            ),
            answer = "A",
            needsReview = false,
            reviewReasons = extraReasons,
        )
        return SplitDraftResolver.applyMatchProblems(draft, conflictProblem)
    }

    private suspend fun confirm(draft: DraftQuestion): Pair<ConfirmImportResult, FakePersistence> {
        val persistence = FakePersistence()
        val result = ConfirmImportUseCase(persistence).execute(
            drafts = listOf(draft),
            target = ConfirmTarget(bankId = 4),
            sourceFileName = "split.txt",
            importJobId = null,
            onlyValidAnswers = true,
        )
        return result to persistence
    }

    @Test
    fun `match problems and parse warnings are tracked separately`() {
        val flagged = flaggedDraft()

        assertTrue(flagged.needsReview)
        assertTrue(flagged.reviewReasons.any { it.contains("不一致") })
        // 独立维护：问题在 splitProblems 层面单独存在，未把冲突写进非本草案的理由
        assertEquals(listOf(conflictProblem.reason()), flagged.reviewReasons)
    }

    @Test
    fun `adopting external answer clears conflict and unlocks confirm`() = runTest {
        val flagged = flaggedDraft()
        val applied = SplitFileMatcher.applyExternalAnswer(flagged, conflictProblem.externalAnswer)
        assertNotNull(applied)
        val resolved = SplitDraftResolver.afterConflictResolve(applied!!, conflictProblem)

        // 只清冲突文案，needsReview 由最终理由推导
        assertFalse(resolved.reviewReasons.any { it.contains("不一致") })
        assertFalse(resolved.needsReview)
        assertEquals("C", resolved.answer)

        val (result, persistence) = confirm(resolved)
        assertTrue(result is ConfirmImportResult.Success)
        assertEquals(listOf("1"), persistence.inserted.map { it.sourceNumber })
        assertEquals("C", persistence.inserted.single().answer)
    }

    @Test
    fun `keeping inline answer also resolves conflict and unlocks confirm`() = runTest {
        val flagged = flaggedDraft()
        val resolved = SplitDraftResolver.afterConflictResolve(flagged, conflictProblem)

        assertFalse(resolved.reviewReasons.any { it.contains("不一致") })
        assertFalse(resolved.needsReview)
        assertEquals("A", resolved.answer)

        val (result, persistence) = confirm(resolved)
        assertTrue(result is ConfirmImportResult.Success)
        assertEquals(listOf("1"), persistence.inserted.map { it.sourceNumber })
    }

    @Test
    fun `stem only edit keeps conflict reason and stays blocked`() = runTest {
        val flagged = flaggedDraft()
        val edited = flagged.copy(stem = "改写后的冲突验收虚构题干是否完整？")
        val result = SplitDraftResolver.afterEdit(edited, flagged, conflictProblem)

        assertFalse(result.resolved)
        assertTrue(result.draft.needsReview)
        assertTrue(result.draft.reviewReasons.any { it.contains("不一致") })

        val (confirm, persistence) = confirm(result.draft)
        assertTrue(confirm is ConfirmImportResult.Failure)
        assertTrue(persistence.inserted.isEmpty())
    }

    @Test
    fun `answer edit resolving conflict recomputes needs review without stale text`() {
        // 回归：resolved 分支此前沿用合并时的 needsReview，冲突是唯一理由时解除后仍卡在待核对
        val flagged = flaggedDraft()
        val edited = flagged.copy(answer = "C")
        val result = SplitDraftResolver.afterEdit(edited, flagged, conflictProblem)

        assertTrue(result.resolved)
        assertFalse(result.draft.needsReview)
        assertTrue(result.draft.reviewReasons.isEmpty())

        // 问题解除后的后续编辑以解决后的干净草稿为基底，不再出现旧冲突文案
        val followUp = SplitDraftResolver.afterEdit(
            result.draft.copy(stem = "再次编辑的冲突验收虚构题干是否完整？"),
            result.draft,
            problem = null,
        )
        assertFalse(followUp.resolved)
        assertFalse(followUp.draft.needsReview)
        assertFalse(followUp.draft.reviewReasons.any { it.contains("不一致") })
    }

    @Test
    fun `answer edit that fails strict validation does not resolve the problem`() {
        val flagged = flaggedDraft()
        val edited = flagged.copy(answer = "本题选C")
        val result = SplitDraftResolver.afterEdit(edited, flagged, conflictProblem)

        assertFalse(result.resolved)
        assertTrue(result.draft.needsReview)
        assertTrue(result.draft.reviewReasons.any { it.contains("不一致") })
        assertTrue(result.draft.reviewReasons.any { it.contains("无法识别为选项字母") })
    }

    @Test
    fun `unrelated parser reasons survive resolution and keep the draft blocked`() = runTest {
        val flagged = flaggedDraft(extraReasons = listOf(headerReason))
        val resolved = SplitDraftResolver.afterConflictResolve(flagged, conflictProblem)

        // 冲突清了，独立解析警告保留并继续阻塞
        assertFalse(resolved.reviewReasons.any { it.contains("不一致") })
        assertTrue(resolved.reviewReasons.contains(headerReason))
        assertTrue(resolved.needsReview)

        val (confirm, persistence) = confirm(resolved)
        assertTrue(confirm is ConfirmImportResult.Failure)
        assertTrue(persistence.inserted.isEmpty())
    }

    @Test
    fun `unresolved flagged drafts are excluded by the save gate`() = runTest {
        val flagged = flaggedDraft()
        val (confirm, persistence) = confirm(flagged)

        assertTrue(confirm is ConfirmImportResult.Failure)
        assertTrue(persistence.inserted.isEmpty())
    }

    private class FakePersistence : ImportPersistence {
        private val banks = mutableMapOf(4L to QuestionBank(id = 4, name = "验收题库"))
        val inserted = mutableListOf<Question>()
        private var nextQuestionId = 100L

        override suspend fun <T> inImportTransaction(block: suspend () -> T): T = block()

        override suspend fun getImportBank(bankId: Long): QuestionBank? = banks[bankId]

        override suspend fun createImportBank(name: String, now: Long): Long {
            banks[4L] = QuestionBank(id = 4, name = name, createdAt = now, updatedAt = now)
            return 4L
        }

        override suspend fun insertImportQuestions(questions: List<Question>): List<Long> {
            inserted += questions
            return questions.map { nextQuestionId++ }
        }

        override suspend fun getImportJobForConfirm(jobId: Long): ImportJob? = null

        override suspend fun updateImportJobForConfirm(job: ImportJob) {
            // 测试未传 jobId，不触达
        }
    }
}
