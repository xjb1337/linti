package com.xjb.linti.data.import

import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SplitFileImporterTest {
    private val questionTxt = """
        1. 分文件导入的虚构题干一是否完整？
        A. 甲
        B. 乙
        C. 丙
        D. 丁
        2. 分文件导入的虚构题干二是否完整？
        A. 甲
        B. 乙
        C. 丙
        D. 丁
        答案：B
        3. 判断：分文件导入的虚构说法三是否正确。
        4. 填空：中国历史上第一个朝代是____。
    """.trimIndent()

    private fun importer(registry: DocumentExtractorRegistry = defaultRegistry()) =
        SplitFileImporter(
            extractorRegistry = registry,
            parser = LocalQuestionParser(),
            answerFileReader = AnswerFileReader(),
        )

    private fun defaultRegistry() = DocumentExtractorRegistry(
        extractors = mapOf(
            DocumentKind.TXT to TxtDocumentExtractor(),
            DocumentKind.CSV to CsvDocumentExtractor(),
            DocumentKind.DOCX to DocxDocumentExtractor(),
            DocumentKind.XLSX to XlsxDocumentExtractor(),
            DocumentKind.PDF to PdfDocumentExtractor(),
        ),
        ocrEngine = UnavailableOcrEngine(),
    )

    @Test
    fun `TXT questions with shuffled CSV answers match by number and keep inline answers`() = runBlocking {
        val csv = """
            正确答案,原题号,备注
            B,2,行内答案同值
            A,1,
            错误,3,判断题
            夏朝,4,填空保留中文
        """.trimIndent()

        val outcome = importer().import(
            questionInput = ByteArrayInputStream(questionTxt.toByteArray(Charsets.UTF_8)),
            questionFileName = "questions.txt",
            answerInput = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            answerFileName = "answers.csv",
        )

        assertTrue(outcome is SplitImportOutcome.Success)
        val success = outcome as SplitImportOutcome.Success
        assertEquals(4, success.drafts.size)
        assertEquals(QuestionType.SINGLE, success.drafts[0].type)
        assertEquals("A", success.drafts[0].answer)
        assertEquals(QuestionType.SINGLE, success.drafts[1].type)
        assertEquals("B", success.drafts[1].answer)
        assertEquals(QuestionType.JUDGE, success.drafts[2].type)
        assertEquals("false", success.drafts[2].answer)
        assertEquals(QuestionType.FILL, success.drafts[3].type)
        assertEquals("夏朝", success.drafts[3].answer)
        assertTrue(success.drafts[0].reviewReasons.none { it.contains("未找到答案") })
        assertTrue(success.problems.isEmpty())
        assertTrue(success.warnings.none { it.contains("题号") })
    }

    @Test
    fun `XLSX answers with full width numbers and orphan entry produce diagnostics`() = runBlocking {
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <si><t>题号</t></si><si><t>答案</t></si>
            <si><t>０１</t></si><si><t>AC</t></si>
            <si><t>9</t></si><si><t>D</t></si>
            </sst>
        """.trimIndent()
        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
            <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="s"><v>3</v></c></row>
            <row r="3"><c r="A3" t="s"><v>4</v></c><c r="B3" t="s"><v>5</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
            <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>
        """.trimIndent()
        val workbookRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            </Relationships>
        """.trimIndent()
        val xlsx = zipOf(
            "xl/sharedStrings.xml" to sharedStringsXml.toByteArray(Charsets.UTF_8),
            "xl/workbook.xml" to workbookXml.toByteArray(Charsets.UTF_8),
            "xl/_rels/workbook.xml.rels" to workbookRelsXml.toByteArray(Charsets.UTF_8),
            "xl/worksheets/sheet1.xml" to sheetXml.toByteArray(Charsets.UTF_8),
        )

        val outcome = importer().import(
            questionInput = ByteArrayInputStream(questionTxt.toByteArray(Charsets.UTF_8)),
            questionFileName = "questions.txt",
            answerInput = ByteArrayInputStream(xlsx),
            answerFileName = "answers.xlsx",
        )

        assertTrue(outcome is SplitImportOutcome.Success)
        val success = outcome as SplitImportOutcome.Success
        assertEquals(4, success.drafts.size)
        assertEquals("AC", success.drafts[0].answer)
        assertEquals(QuestionType.MULTI, success.drafts[0].type)
        assertFalse(success.drafts[0].needsReview)
        assertEquals("B", success.drafts[1].answer)
        assertTrue(success.warnings.any { it.contains("题号 9") && it.contains("没有对应题目") })
    }

    @Test
    fun `TXT answer file with single entry and full width question numbers`() = runBlocking {
        val questionTxt = "０１．全角题号的虚构题干是否完整？\nA. 甲\nB. 乙\nC. 丙\nD. 丁\n"
        val answers = "参考答案\n０１、C"

        val outcome = importer().import(
            questionInput = ByteArrayInputStream(questionTxt.toByteArray(Charsets.UTF_8)),
            questionFileName = "questions.txt",
            answerInput = ByteArrayInputStream(answers.toByteArray(Charsets.UTF_8)),
            answerFileName = "answers.txt",
        )

        assertTrue(outcome is SplitImportOutcome.Success)
        val success = outcome as SplitImportOutcome.Success
        assertEquals(1, success.drafts.size)
        assertEquals("０１", success.drafts[0].sourceNumber)
        assertEquals("C", success.drafts[0].answer)
        assertTrue(success.problems.isEmpty())
    }

    @Test
    fun `question file slot errors are actionable and keep answer slot intact`() = runBlocking {
        val blankPdfRegistry = DocumentExtractorRegistry(
            extractors = mapOf(
                DocumentKind.PDF to PdfDocumentExtractor(
                    textExtractor = object : PdfTextExtractor {
                        override fun extract(input: InputStream) = PdfTextResult("")
                    },
                ),
            ),
            ocrEngine = UnavailableOcrEngine(),
        )

        val outcome = importer(blankPdfRegistry).import(
            questionInput = ByteArrayInputStream(byteArrayOf(1)),
            questionFileName = "scan.pdf",
            answerInput = ByteArrayInputStream("1.A".toByteArray(Charsets.UTF_8)),
            answerFileName = "answers.txt",
        )

        assertTrue(outcome is SplitImportOutcome.SlotErrors)
        val errors = outcome as SplitImportOutcome.SlotErrors
        assertTrue(errors.question.error.orEmpty().contains("PDF"))
        assertTrue(errors.question.error.orEmpty().contains("TXT") || errors.question.error.orEmpty().contains("扫描"))
        assertNull(errors.answer.error)
    }

    @Test
    fun `answer slot rejects unsupported format and wrong header`() = runBlocking {
        val badFormat = importer().import(
            questionInput = ByteArrayInputStream(questionTxt.toByteArray(Charsets.UTF_8)),
            questionFileName = "questions.txt",
            answerInput = ByteArrayInputStream("nope".toByteArray(Charsets.UTF_8)),
            answerFileName = "answers.docx",
        )
        val noHeader = importer().import(
            questionInput = ByteArrayInputStream(questionTxt.toByteArray(Charsets.UTF_8)),
            questionFileName = "questions.txt",
            answerInput = ByteArrayInputStream("1,A\n2,B".toByteArray(Charsets.UTF_8)),
            answerFileName = "answers.csv",
        )
        val wrongKindQuestion = importer().import(
            questionInput = ByteArrayInputStream(questionTxt.toByteArray(Charsets.UTF_8)),
            questionFileName = "questions.xlsx",
            answerInput = ByteArrayInputStream("1.A".toByteArray(Charsets.UTF_8)),
            answerFileName = "answers.txt",
        )

        assertTrue(badFormat is SplitImportOutcome.SlotErrors)
        assertTrue((badFormat as SplitImportOutcome.SlotErrors).answer.error.orEmpty().contains("TXT、CSV、XLSX"))
        assertTrue(noHeader is SplitImportOutcome.SlotErrors)
        assertTrue((noHeader as SplitImportOutcome.SlotErrors).answer.error.orEmpty().contains("表头"))
        assertTrue(wrongKindQuestion is SplitImportOutcome.SlotErrors)
        assertTrue((wrongKindQuestion as SplitImportOutcome.SlotErrors).question.error.orEmpty().contains("试题文件仅支持"))
    }

    @Test
    fun `CSV valued answer plus blank answer for same number never auto matches`() = runBlocking {
        val questions = """
            1. 重复空答案验收的虚构题干是否完整？
            A. 甲
            B. 乙
            C. 丙
            D. 丁
        """.trimIndent()
        val csv = "题号,答案\n1,B\n01,"

        val outcome = importer().import(
            questionInput = ByteArrayInputStream(questions.toByteArray(Charsets.UTF_8)),
            questionFileName = "questions.txt",
            answerInput = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            answerFileName = "answers.csv",
        )

        assertTrue(outcome is SplitImportOutcome.Success)
        val success = outcome as SplitImportOutcome.Success
        val problem = success.problems[0]
        assertNotNull(problem)
        assertTrue(problem is SplitDraftProblem.DuplicateAnswerNumber)
        assertEquals(listOf("B", "空"), (problem as SplitDraftProblem.DuplicateAnswerNumber).values)
        assertEquals("", success.drafts[0].answer)
        assertTrue(success.drafts[0].needsReview)
        assertTrue(success.warnings.any { it.contains("答案文件题号 1 重复") })
    }

    @Test
    fun `DOCX question file with conflicting external answer reports conflict`() = runBlocking {        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>1. DOCX 冲突校验的虚构题干是否完整？</w:t></w:r></w:p>
                <w:p><w:r><w:t>A. 甲</w:t></w:r></w:p>
                <w:p><w:r><w:t>B. 乙</w:t></w:r></w:p>
                <w:p><w:r><w:t>C. 丙</w:t></w:r></w:p>
                <w:p><w:r><w:t>D. 丁</w:t></w:r></w:p>
                <w:p><w:r><w:t>答案：A</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val docx = zipOf("word/document.xml" to documentXml.toByteArray(Charsets.UTF_8))
        val csv = "题号,答案\n1,C\n"

        val outcome = importer().import(
            questionInput = ByteArrayInputStream(docx),
            questionFileName = "questions.docx",
            answerInput = ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            answerFileName = "answers.csv",
        )

        assertTrue(outcome is SplitImportOutcome.Success)
        val success = outcome as SplitImportOutcome.Success
        assertEquals(1, success.drafts.size)
        assertEquals("A", success.drafts[0].answer)
        val problem = success.problems[0]
        assertNotNull(problem)
        assertTrue(problem is SplitDraftProblem.Conflict)
        assertEquals("A", (problem as SplitDraftProblem.Conflict).inlineAnswer)
        assertEquals("C", problem.externalAnswer)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
