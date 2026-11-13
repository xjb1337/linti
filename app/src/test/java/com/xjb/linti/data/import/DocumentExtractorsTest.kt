package com.xjb.linti.data.import

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocumentExtractorsTest {
    @Test
    fun `TXT reads UTF-8 and falls back to GB18030`() = runBlocking {
        val standard = "1. UTF-8 虚构题干是否完整？\n答案：A\n"
        val utf8Result = TxtDocumentExtractor().extract(
            ByteArrayInputStream(standard.toByteArray(Charsets.UTF_8)),
            "questions.txt",
        )
        assertEquals(standard, utf8Result.text)
        assertTrue(utf8Result.warnings.isEmpty())

        val legacy = "1. 中药虚构题干是否完整？"
        val gbkResult = TxtDocumentExtractor().extract(
            ByteArrayInputStream(legacy.toByteArray(Charset.forName("GBK"))),
            "questions.txt",
        )
        assertEquals(legacy, gbkResult.text)
        assertTrue(gbkResult.warnings.any { it.contains("GB18030") })
    }

    @Test
    fun `CSV maps common Chinese headers and preserves selection data`() = runBlocking {
        val csv = """
            题号,题干,选项A,选项B,选项C,选项D,答案,解析,题型
            1,"表格中的虚构题干是否完整？",甲项,乙项,丙项,丁项,C,"表格解析，含逗号",单选题
        """.trimIndent()

        val extraction = CsvDocumentExtractor().extract(
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            "questions.csv",
        )

        assertTrue(extraction.warnings.any { it.contains("表头映射") })
        val parsed = LocalQuestionParser().parse(extraction.text)
        assertEquals(1, parsed.questions.size)
        assertEquals("C", parsed.questions.single().answer)
        assertEquals(4, parsed.questions.single().options.size)
        assertEquals("表格解析，含逗号", parsed.questions.single().explanation)
    }

    @Test
    fun `CSV without header uses documented common column order`() = runBlocking {
        val csv = """
            1,无表头虚构题干是否完整？,甲,乙,丙,丁,B,无表头解析,单选题
        """.trimIndent()

        val extraction = CsvDocumentExtractor().extract(
            ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8)),
            "questions.csv",
        )

        assertTrue(extraction.warnings.any { it.contains("未检测到表头") })
        val question = LocalQuestionParser().parse(extraction.text).questions.single()
        assertEquals("B", question.answer)
        assertEquals(4, question.options.size)
    }

    @Test
    fun `CSV reader handles quoted quotes and embedded newlines`() {
        val rows = CsvReader.parse(
            "题干,答案\n\"含\"\"引号\"\"和\n换行\",A\n",
        )

        assertEquals(2, rows.size)
        assertTrue(rows[1][0].contains("换行"))
        assertEquals("A", rows[1][1])
    }

    @Test
    fun `DOCX extracts paragraphs and table cell text`() = runBlocking {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body>
                <w:p><w:r><w:t>1. DOCX 虚构题干是否完整？</w:t></w:r></w:p>
                <w:tbl>
                  <w:tr>
                    <w:tc><w:p><w:r><w:t>A. 表格选项甲</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>B. 表格选项乙</w:t></w:r></w:p></w:tc>
                  </w:tr>
                </w:tbl>
                <w:p><w:r><w:t>答案：A</w:t></w:r></w:p>
              </w:body>
            </w:document>
        """.trimIndent()
        val docx = zipOf("word/document.xml" to documentXml.toByteArray(Charsets.UTF_8))

        val extraction = DocxDocumentExtractor().extract(ByteArrayInputStream(docx), "questions.docx")
        val parsed = LocalQuestionParser().parse(extraction.text)

        assertTrue(extraction.text.contains("表格选项甲"))
        assertEquals(1, parsed.questions.size)
        assertEquals("A", parsed.questions.single().answer)
    }

    @Test
    fun `XLSX reads shared strings and first sheet`() = runBlocking {
        val headers = listOf("题号", "题干", "选项A", "选项B", "选项C", "选项D", "答案", "解析", "题型")
        val values = headers + listOf(
            "1",
            "XLSX 虚构题干是否完整？",
            "甲",
            "乙",
            "丙",
            "丁",
            "D",
            "XLSX 解析",
            "单选题",
        )
        val sharedStringsXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            values.filter { it != "1" }.forEach { append("<si><t>").append(escapeXml(it)).append("</t></si>") }
            append("</sst>")
        }
        val sharedValues = values.filter { it != "1" }
        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
            append("""<row r="1">""")
            headers.forEachIndexed { index, _ ->
                append("""<c r="${column(index)}1" t="s"><v>$index</v></c>""")
            }
            append("</row><row r=\"2\">")
            append("""<c r="A2"><v>1</v></c>""")
            sharedValues.drop(headers.size).forEachIndexed { offset, _ ->
                val sharedIndex = headers.size + offset
                append("""<c r="${column(offset + 1)}2" t="s"><v>$sharedIndex</v></c>""")
            }
            append("</row></sheetData></worksheet>")
        }
        val xlsx = zipOf(
            "xl/sharedStrings.xml" to sharedStringsXml.toByteArray(Charsets.UTF_8),
            "xl/worksheets/sheet1.xml" to sheetXml.toByteArray(Charsets.UTF_8),
        )

        val extraction = XlsxDocumentExtractor().extract(ByteArrayInputStream(xlsx), "questions.xlsx")
        val question = LocalQuestionParser().parse(extraction.text).questions.single()

        assertEquals("D", question.answer)
        assertEquals(4, question.options.size)
        assertTrue(extraction.warnings.any { it.contains("表头映射") })
    }

    @Test
    fun `PDF extractor delegates to injected text layer and warns on blank text`() = runBlocking {
        val populated = PdfDocumentExtractor(
            textExtractor = object : PdfTextExtractor {
                override fun extract(input: java.io.InputStream) =
                    PdfTextResult("1. PDF 虚构题干是否完整？\n答案：A")
            },
        ).extract(ByteArrayInputStream(byteArrayOf()), "questions.pdf")
        assertEquals(DocumentKind.PDF, populated.kind)
        assertTrue(populated.text.contains("PDF 虚构题干"))

        val blank = PdfDocumentExtractor(
            textExtractor = object : PdfTextExtractor {
                override fun extract(input: java.io.InputStream) = PdfTextResult("")
            },
        ).extract(ByteArrayInputStream(byteArrayOf()), "scan.pdf")
        assertTrue(blank.warnings.any { it.contains("未能提取 PDF 文本层") })
    }

    @Test
    fun `registry routes images through OCR interface`() = runBlocking {
        val registry = DocumentExtractorRegistry(
            extractors = emptyMap(),
            ocrEngine = object : OcrEngine {
                override suspend fun recognize(input: java.io.InputStream, fileName: String) =
                    OcrResult("1. 图片虚构题干是否完整？\n答案：A")
            },
        )

        val result = registry.extract(
            ByteArrayInputStream(byteArrayOf(1, 2, 3)),
            "camera.jpg",
            DocumentKind.CAMERA,
        )

        assertEquals(DocumentKind.CAMERA, result.kind)
        assertTrue(result.text.contains("图片虚构题干"))
    }

    @Test
    fun `old Word format returns an actionable warning`() = runBlocking {
        val result = DocxDocumentExtractor().extract(ByteArrayInputStream(byteArrayOf()), "legacy.doc")

        assertTrue(result.text.isEmpty())
        assertTrue(result.warnings.single().contains(".docx"))
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

    private fun column(index: Int): String {
        var value = index + 1
        val builder = StringBuilder()
        while (value > 0) {
            val remainder = (value - 1) % 26
            builder.append(('A' + remainder))
            value = (value - 1) / 26
        }
        return builder.reverse().toString()
    }

    private fun escapeXml(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
