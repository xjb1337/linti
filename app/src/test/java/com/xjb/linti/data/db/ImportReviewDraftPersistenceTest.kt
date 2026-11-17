package com.xjb.linti.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xjb.linti.data.import.ConfirmImportResult
import com.xjb.linti.data.import.ConfirmImportUseCase
import com.xjb.linti.data.import.ConfirmTarget
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.data.import.SplitDraftProblem
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.QuestionImage
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
class ImportReviewDraftPersistenceTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LintiDatabase::class.java)

    @Before
    fun addSchemas() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val apk = requireNotNull(System.getProperty("linti.test.schemaApk"))
        listOf(instrumentation.context.assets, instrumentation.targetContext.assets).forEach { assets ->
            check(assets.javaClass.getMethod("addAssetPath", String::class.java).invoke(assets, apk) as Int != 0)
        }
    }

    @Test
    fun migrate13To14CreatesImportReviewDraftsAndCascadesOnDelete() {
        val name = "import-review-13-14.db"
        helper.createDatabase(name, 13).use { db ->
            db.execSQL(
                "INSERT INTO import_jobs (id, file_name, kind, method, status, recognized_count, confirmed_count, pending_count, failed_count, created_at, warnings_json) " +
                    "VALUES (1, 'a.docx', 'docx', 'AI', 'REVIEW', 2, 1, 1, 0, 100, '[]')",
            )
        }
        helper.runMigrationsAndValidate(name, 14, true, MIGRATION_13_14).use { db ->
            db.execSQL("PRAGMA foreign_keys=ON")
            db.execSQL("INSERT INTO import_review_drafts (job_id, payload_json, updated_at) VALUES (1, '{}', 200)")
            db.execSQL("DELETE FROM import_jobs WHERE id = 1")
            db.query("SELECT * FROM import_review_drafts").use { assertEquals(0, it.count) }
            db.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        }
    }

    @Test
    fun codecRoundTripsDraftsAndSplitProblems() {
        val imageFileName = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef.png"
        val snapshot = ImportReviewSnapshot(
            fileName = "exam.pdf",
            method = ImportMethod.AI_ONLY,
            kind = DocumentKind.PDF,
            drafts = listOf(
                DraftQuestion(
                    sourceNumber = "1",
                    type = QuestionType.SINGLE,
                    explicitType = QuestionType.SINGLE,
                    stem = "下列哪项正确？",
                    options = listOf(QuestionOption("A", "选项一"), QuestionOption("B", "选项二")),
                    answer = "A",
                    explanation = "解析",
                    needsReview = true,
                    reviewReasons = listOf("AI 识别结果，请人工确认"),
                    sourceFileName = "exam.pdf",
                    images = listOf(QuestionImage(imageFileName, 100, 80)),
                ),
            ),
            warnings = listOf("提示"),
            failedCount = 0,
            targetBankId = 42L,
            newBankName = "新题库",
            isSplitFile = true,
            answerFileName = "ans.txt",
            splitProblems = mapOf(0 to SplitDraftProblem.Conflict("A", "B")),
            ocrSourceName = "PDF",
            requiresValidAnswers = true,
            sourcePages = mapOf(0 to 2),
        )
        val decoded = ImportReviewCodec.decode(ImportReviewCodec.encode(snapshot))
        assertEquals(snapshot, decoded)
        assertEquals(listOf(imageFileName), ImportReviewCodec.extractImageFileNames(ImportReviewCodec.encode(snapshot)))
    }

    @Test
    fun repositorySavesRestoresAndCleansDraftOnConfirm() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LintiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LintiRepository(db)
            val jobId = repo.insertImportJob(
                ImportJob(
                    fileName = "ai.txt",
                    mimeType = "text/plain",
                    kind = "txt",
                    method = ImportMethod.AI_ONLY,
                    status = ImportStatus.REVIEW,
                    recognizedCount = 1,
                    confirmedCount = 0,
                    pendingCount = 1,
                    failedCount = 0,
                    createdAt = 100L,
                ),
            )
            val draft = DraftQuestion(
                sourceNumber = "1",
                type = QuestionType.SINGLE,
                stem = "题干",
                options = listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")),
                answer = "A",
                needsReview = true,
                reviewReasons = listOf("待核对"),
                sourceFileName = "ai.txt",
            )
            repo.saveImportReviewDraft(
                jobId = jobId,
                snapshot = ImportReviewSnapshot(
                    fileName = "ai.txt",
                    method = ImportMethod.AI_ONLY,
                    kind = DocumentKind.TXT,
                    drafts = listOf(draft),
                    warnings = emptyList(),
                    failedCount = 0,
                    targetBankId = null,
                    newBankName = "AI 题库",
                ),
            )

            // 模拟编辑后再次保存
            val editedDraft = draft.copy(stem = "修改后题干", needsReview = false, reviewReasons = emptyList())
            repo.saveImportReviewDraft(
                jobId = jobId,
                snapshot = ImportReviewSnapshot(
                    fileName = "ai.txt",
                    method = ImportMethod.AI_ONLY,
                    kind = DocumentKind.TXT,
                    drafts = listOf(editedDraft),
                    warnings = emptyList(),
                    failedCount = 0,
                    targetBankId = null,
                    newBankName = "AI 题库",
                ),
            )
            val restored = repo.getImportReviewDraft(jobId)
            assertNotNull(restored)
            assertEquals("修改后题干", restored!!.drafts.single().stem)
            val updatedJob = repo.getImportJob(jobId)!!
            assertEquals(0, updatedJob.pendingCount)
            assertEquals(1, updatedJob.confirmedCount)

            // 确认入库后，草稿应被清理且 job 关联到目标题库
            val result = ConfirmImportUseCase(repo).execute(
                drafts = restored.drafts,
                target = ConfirmTarget(bankId = null, newBankName = restored.newBankName),
                sourceFileName = restored.fileName,
                importJobId = jobId,
            )
            assertTrue(result is ConfirmImportResult.Success)
            assertNull(repo.getImportReviewDraft(jobId))
            val confirmedJob = repo.getImportJob(jobId)!!
            assertEquals(ImportStatus.CONFIRMED, confirmedJob.status)
            assertNotNull(confirmedJob.targetBankId)
        } finally {
            db.close()
        }
    }
}
