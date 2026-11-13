package com.xjb.linti.data.import

import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmImportUseCaseTest {
    @Test
    fun `confirms edited drafts into a new bank and preserves every edit`() = runTest {
        val persistence = FakeImportPersistence()
        val useCase = ConfirmImportUseCase(persistence)
        val draft = DraftQuestion(
            sourceNumber = "15",
            type = QuestionType.SINGLE,
            stem = "用户编辑后的虚构题干",
            options = listOf(
                QuestionOption("A", "用户编辑的甲"),
                QuestionOption("B", "用户编辑的乙"),
            ),
            answer = "B",
            explanation = "用户编辑后的解析",
            confidence = 0.6,
            needsReview = true,
            reviewReasons = listOf("题号从 14 跳到 16，请核对是否缺题或题号有误"),
            sourceFileName = "questions.txt",
        )

        val result = useCase.execute(
            drafts = listOf(draft),
            target = ConfirmTarget(newBankName = "新建题库"),
            sourceFileName = "questions.txt",
            importJobId = 8,
            now = 1234,
        )

        assertTrue(result is ConfirmImportResult.Success)
        val success = result as ConfirmImportResult.Success
        assertEquals(1, success.value.questionIds.size)
        val saved = persistence.inserted.single()
        assertEquals("用户编辑后的虚构题干", saved.stem)
        assertEquals(listOf("A", "B"), saved.options.map { it.label })
        assertEquals("用户编辑的甲", saved.options.first().text)
        assertEquals("B", saved.answer)
        assertEquals("用户编辑后的解析", saved.explanation)
        assertTrue(saved.needsReview)
        assertEquals("新建题库", persistence.createdBankName)
        assertEquals(ImportStatus.CONFIRMED, persistence.job?.status)
        assertEquals(1, persistence.job?.confirmedCount)
        assertEquals(1, persistence.job?.pendingCount)
        assertEquals("新建题库", persistence.bankNameFor(saved.bankId))
    }

    @Test
    fun `pending questions are allowed without dropping review flags`() = runTest {
        val persistence = FakeImportPersistence()
        val useCase = ConfirmImportUseCase(persistence)

        val result = useCase.execute(
            drafts = listOf(draft(needsReview = true), draft(needsReview = false)),
            target = ConfirmTarget(bankId = 4),
            sourceFileName = "mixed.txt",
            importJobId = null,
            now = 2,
        )

        assertTrue(result is ConfirmImportResult.Success)
        assertEquals(2, persistence.inserted.size)
        assertEquals(1, persistence.inserted.count { it.needsReview })
    }

    @Test
    fun `user edited answers are normalized before storage`() = runTest {
        val persistence = FakeImportPersistence()
        val useCase = ConfirmImportUseCase(persistence)

        val result = useCase.execute(
            drafts = listOf(
                draft().copy(
                    type = QuestionType.MULTI,
                    options = listOf(
                        QuestionOption("A", "甲"),
                        QuestionOption("B", "乙"),
                        QuestionOption("C", "丙"),
                    ),
                    answer = "c、A、B",
                ),
            ),
            target = ConfirmTarget(bankId = 4),
            sourceFileName = "edited.txt",
            importJobId = null,
        )

        assertTrue(result is ConfirmImportResult.Success)
        assertEquals("ABC", persistence.inserted.single().answer)
    }

    @Test
    fun `rejects missing bank target and empty drafts`() = runTest {
        val useCase = ConfirmImportUseCase(FakeImportPersistence())

        val noTarget = useCase.execute(
            drafts = listOf(draft()),
            target = ConfirmTarget(),
            sourceFileName = "a.txt",
            importJobId = null,
        )
        val empty = useCase.execute(
            drafts = emptyList(),
            target = ConfirmTarget(bankId = 1),
            sourceFileName = "a.txt",
            importJobId = null,
        )

        assertTrue(noTarget is ConfirmImportResult.Failure)
        assertTrue(empty is ConfirmImportResult.Failure)
    }

    private fun draft(needsReview: Boolean = false) = DraftQuestion(
        sourceNumber = "1",
        type = QuestionType.SINGLE,
        stem = "虚构题干",
        options = listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")),
        answer = "A",
        needsReview = needsReview,
    )

    private class FakeImportPersistence : ImportPersistence {
        private val banks = mutableMapOf(
            4L to QuestionBank(id = 4, name = "已有题库"),
        )
        var createdBankName: String? = null
        var job: ImportJob? = ImportJob(
            id = 8,
            fileName = "questions.txt",
            mimeType = "text/plain",
            kind = "txt",
            method = ImportMethod.LOCAL_ONLY,
            status = ImportStatus.REVIEW,
            recognizedCount = 1,
            confirmedCount = 0,
            pendingCount = 1,
            failedCount = 0,
            createdAt = 0,
        )
        val inserted = mutableListOf<Question>()
        private var nextBankId = 20L
        private var nextQuestionId = 100L

        override suspend fun <T> inImportTransaction(block: suspend () -> T): T = block()

        override suspend fun getImportBank(bankId: Long): QuestionBank? = banks[bankId]

        override suspend fun createImportBank(name: String, now: Long): Long {
            createdBankName = name
            val id = nextBankId++
            banks[id] = QuestionBank(id = id, name = name, createdAt = now, updatedAt = now)
            return id
        }

        override suspend fun insertImportQuestions(questions: List<Question>): List<Long> {
            inserted += questions
            return questions.map { nextQuestionId++ }
        }

        override suspend fun getImportJobForConfirm(jobId: Long): ImportJob? = job

        override suspend fun updateImportJobForConfirm(job: ImportJob) {
            this.job = job
        }

        fun bankNameFor(bankId: Long): String? = banks[bankId]?.name
    }
}
