package com.xjb.linti.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.xjb.linti.data.backup.BackupArchive
import com.xjb.linti.data.db.ExamDraftSnapshot
import com.xjb.linti.data.db.LintiDatabase
import com.xjb.linti.data.import.*
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.SessionSnapshot
import com.xjb.linti.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuestionImagesTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var store: QuestionImageStore
    private lateinit var db: LintiDatabase
    private lateinit var repo: LintiRepository
    private var bank = 0L

    @Before fun setup() = runBlocking {
        store = QuestionImageStore(temporary.newFolder("question_images"))
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LintiDatabase::class.java).allowMainThreadQueries().build()
        repo = LintiRepository(db, store)
        bank = repo.createBank("虚构题库")
    }
    @After fun close() { db.close() }

    private fun graphic(width: Int = 60, height: Int = 40, color: Int = Color.BLUE, png: Boolean = true): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, if (png) Bitmap.Config.ARGB_8888 else Bitmap.Config.RGB_565)
        bitmap.eraseColor(color)
        val bytes = ByteArrayOutputStream()
        bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, bytes)
        bitmap.recycle()
        return bytes.toByteArray()
    }
    private fun image(color: Int = Color.BLUE) = store.import(ByteArrayInputStream(graphic(color = color)))
    private suspend fun addQuestion(images: List<QuestionImage>) = repo.saveQuestion(Question(
        bankId = bank, sourceNumber = "1", stem = "虚构示意图", options = listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")),
        answer = "A", sourceFileName = "测试", images = images,
    ))

    @Test fun deduplicatesCompressesRotatesAndRemovesExif() {
        val large = graphic(4096, 2200, Color.RED)
        val a = store.import(ByteArrayInputStream(large))
        val duplicate = store.import(ByteArrayInputStream(large))
        assertEquals(a, duplicate)
        assertEquals(2048, maxOf(a.width, a.height))
        assertTrue(store.file(a).exists())
        assertEquals(1, store.directory.listFiles()!!.size)
        val transparent = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.TRANSPARENT) }
        val pngBytes = ByteArrayOutputStream().also { transparent.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        transparent.recycle()
        val alpha = store.import(ByteArrayInputStream(pngBytes))
        assertTrue(alpha.fileName.endsWith(".png"))
        assertEquals(0, Color.alpha(BitmapFactory.decodeFile(store.file(alpha).path).getPixel(0, 0)))
        val source = temporary.newFile("oriented.jpg")
        val sourceBitmap = Bitmap.createBitmap(120, 60, Bitmap.Config.RGB_565)
        android.graphics.Canvas(sourceBitmap).apply {
            drawColor(Color.RED)
            drawRect(0f, 30f, 120f, 60f, android.graphics.Paint().apply { color = Color.BLUE })
        }
        source.outputStream().use { sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        sourceBitmap.recycle()
        ExifInterface(source).apply { setAttribute(ExifInterface.TAG_ORIENTATION, "6"); saveAttributes() }
        val rotated = store.import(source.inputStream())
        assertEquals(60, rotated.width)
        assertEquals(120, rotated.height)
        val result = BitmapFactory.decodeFile(store.file(rotated).path)
        assertTrue(Color.blue(result.getPixel(5, 60)) > Color.red(result.getPixel(5, 60)))
        assertTrue(Color.red(result.getPixel(55, 60)) > Color.blue(result.getPixel(55, 60)))
        result.recycle()
        val savedOrientation = ExifInterface(store.file(rotated)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        assertTrue(savedOrientation == ExifInterface.ORIENTATION_UNDEFINED || savedOrientation == ExifInterface.ORIENTATION_NORMAL)
    }

    @Test fun enforcesLimitAndPersistsOrderingAndDeletion() = runBlocking {
        val a = image(Color.RED); val b = image(Color.GREEN); val c = image(Color.BLUE)
        val id = addQuestion(listOf(a, b, c))
        listOf(a, b, c).forEach(store::release)
        repo.saveQuestion(repo.getQuestion(id)!!.copy(images = listOf(c, a)))
        assertEquals(listOf(c, a), repo.getQuestion(id)!!.images)
        assertFalse(store.file(b).exists())
        assertTrue(runCatching { repo.saveQuestion(repo.getQuestion(id)!!.copy(images = List(5) { a })) }.isFailure)
        assertEquals(listOf(c, a), repo.getQuestion(id)!!.images)
        repo.deleteQuestion(id)
        assertFalse(store.file(a).exists()); assertFalse(store.file(c).exists())
    }

    private suspend fun submit(id: Long, images: List<QuestionImage>): Long {
        val question = QuizQuestion(id, bank, "1", QuestionType.SINGLE, "虚构示意图", listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")), "A", "解析", images = images)
        val config = ExamConfig(singleCount = 1, durationMinutes = 5, passScore = 1.0)
        val answers = mapOf(id to "A")
        val questions = listOf(question)
        repo.createExamDraft(ExamDraftSnapshot("draft", bank, "虚构题库", config, questions, answers, emptySet(), emptySet(), emptySet(), 0, 1L, 0L))
        val snapshot = SessionSnapshot(bank, "虚构题库", PracticeMode.EXAM, config, questions, answers, emptySet(), emptySet(), 1, 2, Grader.grade(questions, answers, config))
        return repo.submitExamSession(snapshot, emptyList(), "draft")
    }

    @Test fun snapshotKeepsOriginalImagesAndCleanerRemovesOnlyOrphans() = runBlocking {
        val old = image(Color.RED); val replacement = image(Color.GREEN); val orphan = image(Color.BLUE)
        val id = addQuestion(listOf(old))
        val exam = submit(id, listOf(old))
        listOf(old, replacement, orphan).forEach(store::release)
        repo.saveQuestion(repo.getQuestion(id)!!.copy(images = listOf(replacement)))
        assertFalse(store.file(orphan).exists())
        assertTrue(store.file(old).exists())
        repo.deleteQuestion(id)
        assertTrue(store.file(old).exists())
        assertFalse(store.file(replacement).exists())
        assertEquals(listOf(old), repo.getExamPaperReview(exam)!!.questions.single().images)
    }

    @Test fun activeExamDraftKeepsAnImageUntilDiscarded() = runBlocking {
        val picture = image(Color.RED)
        val id = addQuestion(listOf(picture))
        val question = QuizQuestion(id, bank, "1", QuestionType.SINGLE, "虚构题", emptyList(), "A", "", images = listOf(picture))
        val config = ExamConfig(singleCount = 1)
        repo.createExamDraft(ExamDraftSnapshot("draft-only", bank, "虚构题库", config, listOf(question), emptyMap(), emptySet(), emptySet(), emptySet(), 0, 1, 0))
        store.release(picture)
        repo.saveQuestion(repo.getQuestion(id)!!.copy(images = emptyList()))
        assertTrue(store.file(picture).exists())
        assertEquals(listOf(picture), repo.getExamDraft(bank)!!.questions.single().images)
        repo.discardExamDraft("draft-only")
        assertFalse(store.file(picture).exists())
    }

    private fun archive(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()
    private fun entries(bytes: ByteArray): MutableMap<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) { val entry = zip.nextEntry ?: break; result[entry.name] = zip.readBytes(); zip.closeEntry() }
        }
        return result
    }

    @Test fun zipRoundTripAndLegacyJson() = runBlocking {
        val old = image(Color.RED); val current = image(Color.GREEN)
        val id = addQuestion(listOf(old)); val exam = submit(id, listOf(old))
        repo.saveQuestion(repo.getQuestion(id)!!.copy(images = listOf(current)))
        listOf(old, current).forEach(store::release)
        val output = ByteArrayOutputStream(); repo.exportBackup(output)
        val zip = output.toByteArray()
        assertEquals(setOf("backup.json", "images/${old.fileName}", "images/${current.fileName}"), entries(zip).keys)
        repo.deleteQuestion(id)
        repo.restoreBackup(ByteArrayInputStream(zip))
        assertEquals(listOf(current), repo.getQuestion(id)!!.images)
        assertEquals(listOf(old), repo.getExamPaperReview(exam)!!.questions.single().images)
        val legacy = JSONObject(repo.exportBackupJson()).put("version", 11).apply {
            remove("questionImages"); put("examPapers", org.json.JSONArray()); put("examPaperChunks", org.json.JSONArray())
        }.toString()
        repo.restoreBackup(ByteArrayInputStream(legacy.toByteArray()))
        assertTrue(repo.getQuestion(id)!!.images.isEmpty())
    }

    @Test fun validBackupRepairsALocallyCorruptedImage() = runBlocking {
        val picture = image(Color.BLUE); val id = addQuestion(listOf(picture)); store.release(picture)
        val out = ByteArrayOutputStream(); repo.exportBackup(out)
        val expected = store.file(picture).readBytes()
        store.file(picture).writeBytes(byteArrayOf(1, 2, 3))
        repo.restoreBackup(ByteArrayInputStream(out.toByteArray()))
        assertEquals(listOf(picture), repo.getQuestion(id)!!.images)
        assertArrayEquals(expected, store.file(picture).readBytes())
    }

    @Test fun malformedArchivesNeverChangeExistingDatabaseOrFiles() = runBlocking {
        val picture = image(Color.RED); val id = addQuestion(listOf(picture)); store.release(picture)
        val out = ByteArrayOutputStream(); repo.exportBackup(out)
        val original = entries(out.toByteArray())
        val path = "images/${picture.fileName}"
        val invalidImage = byteArrayOf(1, 2, 3, 4)
        val invalidName = QuestionImageStore.sha256(invalidImage) + ".png"
        val badJson = JSONObject(original.getValue("backup.json").toString(Charsets.UTF_8))
        badJson.getJSONArray("questionImages").getJSONObject(0).put("fileName", invalidName)
        val variants = listOf(
            original - path,
            original + (path to graphic(color = Color.GREEN)),
            original + ("../escape" to byteArrayOf(1)),
            original + (path to ByteArray(BackupArchive.MAX_IMAGE_BYTES + 1)),
            (original - path) + ("images/$invalidName" to invalidImage) + ("backup.json" to badJson.toString().toByteArray()),
        )
        val before = store.file(picture).readBytes()
        variants.forEach { bad ->
            assertTrue(runCatching { repo.restoreBackup(ByteArrayInputStream(archive(bad))) }.isFailure)
            assertEquals(listOf(picture), repo.getQuestion(id)!!.images)
            assertArrayEquals(before, store.file(picture).readBytes())
        }
    }

    @Test fun docxAssociatesOnlyStemImagesAndAiReceivesPlainText() = runBlocking {
        val rels = """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="r1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/one.png"/><Relationship Id="r2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="/word/media/two.png"/></Relationships>"""
        fun paragraph(text: String, id: String? = null) = "<w:p><w:r><w:t>$text</w:t>${if (id == null) "" else "<w:drawing><a:blip r:embed=\"$id\"/></w:drawing>"}</w:r></w:p>"
        val xml = """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><w:body>${paragraph("1. 虚构甲", "r1")}${paragraph("A. 甲")}${paragraph("B. 乙")}${paragraph("答案：A")}${paragraph("2. 虚构乙", "r2")}${paragraph("A. 甲")}${paragraph("B. 乙")}${paragraph("答案：B")}</w:body></w:document>"""
        val docx = archive(mapOf("word/document.xml" to xml.toByteArray(), "word/_rels/document.xml.rels" to rels.toByteArray(), "word/media/one.png" to graphic(color = Color.RED), "word/media/two.png" to graphic(color = Color.BLUE)))
        val registry = DocumentExtractorRegistry(mapOf(DocumentKind.DOCX to DocxDocumentExtractor()), UnavailableOcrEngine())
        var sent = ""
        val parser = LocalQuestionParser()
        val pipeline = ImportPipeline(registry, parser, { object : AiClient {
            override suspend fun parseQuestions(text: String): List<DraftQuestion> { sent = text; return parser.parse(text).questions }
        } }, store)
        val result = pipeline.run(ByteArrayInputStream(docx), "虚构.docx", ImportMethod.AI_ONLY)
        assertEquals(2, result.questions.size)
        assertEquals(listOf(1, 1), result.questions.map { it.images.size })
        assertNotEquals(result.questions[0].images[0].fileName, result.questions[1].images[0].fileName)
        assertFalse(sent.contains("image0")); assertFalse(sent.contains('\uE000'))
        assertFalse(result.questions.any { it.stem.contains('\uE000') })

        val reversedPipeline = ImportPipeline(registry, parser, { object : AiClient {
            override suspend fun parseQuestions(text: String) = parser.parse(text).questions.reversed()
        } }, store)
        val reversed = reversedPipeline.run(ByteArrayInputStream(docx), "虚构.docx", ImportMethod.AI_ONLY)
        assertEquals(listOf("2", "1"), reversed.questions.map { it.sourceNumber })
        assertEquals(result.questions[1].images, reversed.questions[0].images)
        assertEquals(result.questions[0].images, reversed.questions[1].images)

        val unmatchedPipeline = ImportPipeline(registry, parser, { object : AiClient {
            override suspend fun parseQuestions(text: String) = listOf(
                DraftQuestion(sourceNumber = "99", type = QuestionType.SINGLE, stem = "无法关联的 AI 题"),
            )
        } }, store)
        val unmatched = unmatchedPipeline.run(ByteArrayInputStream(docx), "虚构.docx", ImportMethod.AI_ONLY)
        assertEquals(listOf("1", "2"), unmatched.questions.map { it.sourceNumber })
        assertTrue(unmatched.questions.all { it.needsReview && it.images.size == 1 })
        assertTrue(unmatched.warnings.any { it.contains("未能与 AI 结果可靠匹配") })
    }
}
