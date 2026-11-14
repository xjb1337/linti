package com.xjb.linti.data.import

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class AnswerFileParserTest {
    // ---- TXT 一行一组 ----

    @Test
    fun `TXT parses one pair per line with common separators`() {
        val text = """
            01.A
            2、AC
            3：正确
            4 B
        """.trimIndent()

        val result = AnswerFileParser.parse(AnswerFileSource.Lines(text))

        assertNull(result.error)
        assertEquals(4, result.entries.size)
        assertEquals(1, result.entries[0].number)
        assertEquals("A", result.entries[0].rawAnswer)
        assertEquals(2, result.entries[1].number)
        assertEquals("AC", result.entries[1].rawAnswer)
        assertEquals(3, result.entries[2].number)
        assertEquals("正确", result.entries[2].rawAnswer)
        assertEquals(4, result.entries[3].number)
        assertEquals("B", result.entries[3].rawAnswer)
    }

    @Test
    fun `TXT accepts full width numbers and leading zeros`() {
        val text = "０１．A\n001、B"

        val result = AnswerFileParser.parse(AnswerFileSource.Lines(text))

        assertNull(result.error)
        assertEquals(listOf(1, 1), result.entries.map { it.number })
        assertEquals("０１", result.entries[0].numberText)
    }

    @Test
    fun `TXT blank answer row with valid number occupies a duplicate slot`() {
        val result = AnswerFileParser.parse(AnswerFileSource.Lines("1.B\n01,"))

        assertNull(result.error)
        assertEquals(2, result.entries.size)
        assertEquals(1, result.entries[0].number)
        assertEquals("B", result.entries[0].rawAnswer)
        assertEquals(1, result.entries[1].number)
        assertEquals("", result.entries[1].rawAnswer)
        assertTrue(result.diagnostics.any { it.contains("第 2 行") && it.contains("答案为空") })
    }

    @Test
    fun `XLSX blank answer row with valid number occupies a duplicate slot`() = runBlocking {
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <si><t>题号</t></si><si><t>答案</t></si>
            <si><t>０１</t></si><si><t>√</t></si>
            </sst>
        """.trimIndent()
        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
            <row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2" t="s"><v>3</v></c></row>
            <row r="3"><c r="A3"><v>1</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()
        val xlsx = zipOfWithWorkbook(
            "xl/sharedStrings.xml" to sharedStringsXml.toByteArray(Charsets.UTF_8),
            "xl/worksheets/sheet1.xml" to sheetXml.toByteArray(Charsets.UTF_8),
        )

        val read = AnswerFileReader().read(ByteArrayInputStream(xlsx), "answers.xlsx")
        assertTrue(read is AnswerFileReadResult.Ok)
        val parse = AnswerFileParser.parse((read as AnswerFileReadResult.Ok).source)

        assertNull(parse.error)
        assertEquals(2, parse.entries.size)
        assertEquals(1, parse.entries[0].number)
        assertEquals("√", parse.entries[0].rawAnswer)
        assertEquals(1, parse.entries[1].number)
        assertEquals("", parse.entries[1].rawAnswer)
        assertTrue(parse.diagnostics.any { it.contains("第 3 行") && it.contains("答案为空") })
    }

    @Test
    fun `TXT keeps a single entry without needing two pairs`() {
        val result = AnswerFileParser.parse(AnswerFileSource.Lines("7、C"))

        assertNull(result.error)
        assertEquals(7, result.entries.single().number)
        assertEquals("C", result.entries.single().rawAnswer)
    }

    @Test
    fun `TXT skips reference answer header and parses inline pairs`() {
        val text = """
            参考答案
            1.A
            2、D
        """.trimIndent()
        val inline = AnswerFileParser.parse(AnswerFileSource.Lines("参考答案：1.A 2、D"))

        val headered = AnswerFileParser.parse(AnswerFileSource.Lines(text))
        assertNull(headered.error)
        assertEquals(listOf(1, 2), headered.entries.map { it.number })
        assertNull(inline.error)
        assertEquals(listOf(1, 2), inline.entries.map { it.number })
        assertEquals("A", inline.entries[0].rawAnswer)
        assertEquals("D", inline.entries[1].rawAnswer)
    }

    @Test
    fun `TXT strips answer prefix inside the answer part`() {
        val result = AnswerFileParser.parse(AnswerFileSource.Lines("1. 答案：B"))

        assertNull(result.error)
        assertEquals("B", result.entries.single().rawAnswer)
    }

    @Test
    fun `TXT keeps diagnostics for invalid lines blank answers and bad numbers`() {
        val text = """
            1.A
            这行没有题号
            2.
            0.C
            xyz
        """.trimIndent()

        val result = AnswerFileParser.parse(AnswerFileSource.Lines(text))

        assertNull(result.error)
        // 有效题号 2 的空答案行也占位（计入重复），其余行仅诊断
        assertEquals(2, result.entries.size)
        assertEquals("", result.entries[1].rawAnswer)
        assertEquals(2, result.entries[1].number)
        assertTrue(result.diagnostics.any { it.contains("第 2 行") && it.contains("无法识别题号") })
        assertTrue(result.diagnostics.any { it.contains("第 3 行") && it.contains("答案为空") })
        assertTrue(result.diagnostics.any { it.contains("第 4 行") && it.contains("题号无效") })
        assertTrue(result.diagnostics.any { it.contains("第 5 行") })
    }

    @Test
    fun `TXT blank content fails with actionable error`() {
        val result = AnswerFileParser.parse(AnswerFileSource.Lines("   \n "))

        assertNotNull(result.error)
        assertTrue(result.error.orEmpty().contains("空") || result.error.orEmpty().contains("题号"))
    }

    // ---- CSV / XLSX 结构化行列 ----

    @Test
    fun `CSV requires explicit number and answer headers and supports swapped columns`() {
        val csv = """
            正确答案,题目,原题号,备注
            B,列可调换的虚构说明,02,忽略列
        """.trimIndent()

        val result = AnswerFileParser.parse(AnswerFileSource.Grid(CsvReader.parse(csv), "CSV"))

        assertNull(result.error)
        assertEquals(1, result.entries.size)
        assertEquals(2, result.entries.single().number)
        assertEquals("B", result.entries.single().rawAnswer)
    }

    @Test
    fun `CSV missing header fails and header only file fails`() {
        val noHeader = AnswerFileParser.parse(
            AnswerFileSource.Grid(CsvReader.parse("1,A\n2,B"), "CSV"),
        )
        val headerOnly = AnswerFileParser.parse(
            AnswerFileSource.Grid(CsvReader.parse("题号,答案"), "CSV"),
        )

        assertNotNull(noHeader.error)
        assertTrue(noHeader.error.orEmpty().contains("表头"))
        assertNotNull(headerOnly.error)
        assertTrue(headerOnly.error.orEmpty().contains("没有可解析"))
    }

    @Test
    fun `CSV handles quotes escapes and keeps empty cells in place`() {
        val csv = "题号,答案,备注\n1,\"A\"\"B\"\"含逗号, 引号\",keep\n2,,\n3, C ,\n"

        val result = AnswerFileParser.parse(AnswerFileSource.Grid(CsvReader.parse(csv), "CSV"))

        assertNull(result.error)
        assertEquals(3, result.entries.size)
        assertEquals("A\"B\"含逗号, 引号", result.entries[0].rawAnswer)
        assertTrue(result.diagnostics.any { it.contains("第 3 行") && it.contains("答案为空") })
        assertEquals("", result.entries[1].rawAnswer)
        assertEquals("C", result.entries[2].rawAnswer)
    }

    @Test
    fun `duplicate synonym headers are rejected with an explicit error`() {
        val twoAnswers = AnswerFileParser.parse(
            AnswerFileSource.Grid(CsvReader.parse("题号,答案,正确答案\n1,A,B"), "CSV"),
        )
        val twoNumbers = AnswerFileParser.parse(
            AnswerFileSource.Grid(CsvReader.parse("题号,原题号,答案\n1,1,A"), "CSV"),
        )

        assertNotNull(twoAnswers.error)
        assertTrue(twoAnswers.error.orEmpty().contains("多个同义"))
        assertTrue(twoAnswers.error.orEmpty().contains("「答案」"))
        assertNotNull(twoNumbers.error)
        assertTrue(twoNumbers.error.orEmpty().contains("多个同义"))
        assertTrue(twoNumbers.error.orEmpty().contains("「题号」"))
    }

    @Test
    fun `XLSX answer grid follows workbook tab order instead of file names`() = runBlocking {
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <si><t>题号</t></si><si><t>答案</t></si><si><t>占位</t></si>
            <si><t>1</t></si><si><t>C</t></si><si><t>干扰</t></si>
            </sst>
        """.trimIndent()
        // sheet2 是工作簿页签里的第一个；按文件名顺序会错读 sheet1
        val sheet2Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
            <row r="2"><c r="A2" t="s"><v>3</v></c><c r="B2" t="s"><v>4</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()
        val sheet1Xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1" t="s"><v>2</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()
        val workbookXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
            <sheets>
            <sheet name="答案页" sheetId="2" r:id="rId2"/>
            <sheet name="占位页" sheetId="1" r:id="rId1"/>
            </sheets></workbook>
        """.trimIndent()
        val workbookRelsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
            <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
            </Relationships>
        """.trimIndent()
        val xlsx = zipOf(
            "xl/sharedStrings.xml" to sharedStringsXml.toByteArray(Charsets.UTF_8),
            "xl/workbook.xml" to workbookXml.toByteArray(Charsets.UTF_8),
            "xl/_rels/workbook.xml.rels" to workbookRelsXml.toByteArray(Charsets.UTF_8),
            "xl/worksheets/sheet1.xml" to sheet1Xml.toByteArray(Charsets.UTF_8),
            "xl/worksheets/sheet2.xml" to sheet2Xml.toByteArray(Charsets.UTF_8),
        )

        val read = AnswerFileReader().read(ByteArrayInputStream(xlsx), "answers.xlsx")
        assertTrue(read is AnswerFileReadResult.Ok)
        val parse = AnswerFileParser.parse((read as AnswerFileReadResult.Ok).source)

        assertNull(parse.error)
        assertEquals(1, parse.entries.single().number)
        assertEquals("C", parse.entries.single().rawAnswer)
    }

    @Test
    fun `CSV full width numbers normalize and invalid rows stay as diagnostics`() {
        val csv = """
            题号,答案
            ０１,对
            x,错
        """.trimIndent()

        val result = AnswerFileParser.parse(AnswerFileSource.Grid(CsvReader.parse(csv), "CSV"))

        assertNull(result.error)
        assertEquals(1, result.entries.single().number)
        assertEquals("对", result.entries.single().rawAnswer)
        assertTrue(result.diagnostics.any { it.contains("第 3 行") && it.contains("题号无效") })
    }

    @Test
    fun `XLSX answer grid reads first sheet with headers and keeps column positions`() = runBlocking {
        val sheetXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
            <row r="2"><c r="A2" t="s"><v>3</v></c><c r="B2" t="s"><v>4</v></c><c r="C2" t="s"><v>5</v></c></row>
            <row r="3"><c r="A3"><v>2</v></c><c r="C3" t="s"><v>6</v></c></row>
            <row r="4"><c r="A4" t="s"><v>7</v></c><c r="B4" t="s"><v>8</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <si><t>题号</t></si><si><t>参考答案</t></si><si><t>说明</t></si>
            <si><t>０1</t></si><si><t>√</t></si><si><t>有备注</t></si>
            <si><t>缺失单元格不左移</t></si><si><t>3</t></si><si><t>错误</t></si>
            </sst>
        """.trimIndent()
        val xlsx = zipOfWithWorkbook(
            "xl/sharedStrings.xml" to sharedStringsXml.toByteArray(Charsets.UTF_8),
            "xl/worksheets/sheet1.xml" to sheetXml.toByteArray(Charsets.UTF_8),
        )

        val read = AnswerFileReader().read(ByteArrayInputStream(xlsx), "answers.xlsx")
        assertTrue(read is AnswerFileReadResult.Ok)
        val parse = AnswerFileParser.parse((read as AnswerFileReadResult.Ok).source)

        assertNull(parse.error)
        // 第 3 行题号有效但答案为空：占位计入重复，位置不左移
        assertEquals(3, parse.entries.size)
        assertEquals(1, parse.entries[0].number)
        assertEquals("√", parse.entries[0].rawAnswer)
        assertEquals(2, parse.entries[1].number)
        assertEquals("", parse.entries[1].rawAnswer)
        assertEquals(3, parse.entries[2].number)
        assertEquals("错误", parse.entries[2].rawAnswer)
        assertTrue(parse.diagnostics.any { it.contains("第 3 行") && it.contains("答案为空") })
    }

    @Test
    fun `reader rejects unsupported extensions and legacy xls`() {
        val unsupported = AnswerFileReader().read(ByteArrayInputStream(byteArrayOf(1)), "answers.pdf")
        val legacy = AnswerFileReader().read(ByteArrayInputStream(byteArrayOf(1)), "answers.xls")

        assertTrue(unsupported is AnswerFileReadResult.Failed)
        assertTrue((unsupported as AnswerFileReadResult.Failed).error.contains("TXT、CSV、XLSX"))
        assertTrue(legacy is AnswerFileReadResult.Failed)
        assertTrue((legacy as AnswerFileReadResult.Failed).error.contains(".xls"))
    }

    @Test
    fun `WORKBOOK_TAB reading errors when first tab relationship or file is missing`() = runBlocking {
        val sheet1 = """
            <?xml version="1.0" encoding="UTF-8"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1"><c r="A1"><v>1</v></c></row>
            </sheetData></worksheet>
        """.trimIndent()

        // 关系文件整体缺失
        val noRels = zipOf(
            "xl/workbook.xml" to workbookXml().toByteArray(Charsets.UTF_8),
            "xl/worksheets/sheet1.xml" to sheet1.toByteArray(Charsets.UTF_8),
        )
        // 首个页签引用的关系不在 rels 中
        val relsMissingId = zipOf(
            "xl/workbook.xml" to workbookXml().toByteArray(Charsets.UTF_8),
            "xl/_rels/workbook.xml.rels" to workbookRelsXml("sheet9.xml").toByteArray(Charsets.UTF_8),
            "xl/worksheets/sheet1.xml" to sheet1.toByteArray(Charsets.UTF_8),
        )
        // 首个页签目标文件缺失：即使别的 sheet 存在也不得跳读或按文件名兜底
        val targetMissing = zipOf(
            "xl/workbook.xml" to workbookXml().toByteArray(Charsets.UTF_8),
            "xl/_rels/workbook.xml.rels" to workbookRelsXml("sheet3.xml").toByteArray(Charsets.UTF_8),
            "xl/worksheets/sheet1.xml" to sheet1.toByteArray(Charsets.UTF_8),
        )

        listOf(noRels, relsMissingId, targetMissing).forEach { xlsx ->
            val read = AnswerFileReader().read(ByteArrayInputStream(xlsx), "answers.xlsx")
            assertTrue(read is AnswerFileReadResult.Failed)
            assertTrue((read as AnswerFileReadResult.Failed).error.contains("首个工作表"))
        }
    }

    private fun workbookXml(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
            xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
        <sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>
    """.trimIndent()

    private fun workbookRelsXml(firstTarget: String): String = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
        <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/$firstTarget"/>
        </Relationships>
    """.trimIndent()

    private fun zipOfWithWorkbook(vararg entries: Pair<String, ByteArray>): ByteArray {
        return zipOf(
            *entries,
            "xl/workbook.xml" to workbookXml().toByteArray(Charsets.UTF_8),
            "xl/_rels/workbook.xml.rels" to workbookRelsXml("sheet1.xml").toByteArray(Charsets.UTF_8),
        )
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
