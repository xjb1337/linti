package com.xjb.linti.data.import

import java.io.InputStream

// 答案文件的读取层：只负责把 TXT/CSV/XLSX 解出原始文本或结构化行列，
// 不做题干假设、不自动编号、不转换成试题文本。
sealed interface AnswerFileSource {
    data class Lines(val text: String) : AnswerFileSource
    data class Grid(val rows: List<List<String>>, val sourceLabel: String) : AnswerFileSource
}

sealed interface AnswerFileReadResult {
    data class Ok(
        val source: AnswerFileSource,
        val kind: DocumentKind,
        val warnings: List<String> = emptyList(),
    ) : AnswerFileReadResult

    data class Failed(val error: String) : AnswerFileReadResult
}

class AnswerFileReader {
    fun read(input: InputStream, fileName: String): AnswerFileReadResult {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "txt" -> readTxt(input)
            "csv" -> readCsv(input)
            "xlsx" -> readXlsx(input)
            "xls" -> AnswerFileReadResult.Failed("暂不支持旧版 .xls，请另存为 .xlsx 或 CSV 后重试")
            else -> AnswerFileReadResult.Failed("答案文件仅支持 TXT、CSV、XLSX，当前为「$fileName」")
        }
    }

    private fun readTxt(input: InputStream): AnswerFileReadResult {
        val decoded = TextDecoder.decode(input.readBytes())
        if (decoded.text.isBlank()) {
            return AnswerFileReadResult.Failed("答案文件内容为空，请选择包含题号与答案的文件")
        }
        val warnings = if (decoded.usedFallbackEncoding) {
            listOf("答案文件不是有效 UTF-8，已使用 ${decoded.charsetName} 解码")
        } else {
            emptyList()
        }
        return AnswerFileReadResult.Ok(
            source = AnswerFileSource.Lines(decoded.text),
            kind = DocumentKind.TXT,
            warnings = warnings,
        )
    }

    private fun readCsv(input: InputStream): AnswerFileReadResult {
        val decoded = TextDecoder.decode(input.readBytes())
        if (decoded.text.isBlank()) {
            return AnswerFileReadResult.Failed("答案文件内容为空，请选择包含题号与答案的文件")
        }
        val rows = CsvReader.parse(decoded.text)
        if (rows.isEmpty()) {
            return AnswerFileReadResult.Failed("CSV 没有可读取的数据行，请检查文件内容")
        }
        val warnings = if (decoded.usedFallbackEncoding) {
            listOf("答案文件不是有效 UTF-8，已使用 ${decoded.charsetName} 解码")
        } else {
            emptyList()
        }
        return AnswerFileReadResult.Ok(
            source = AnswerFileSource.Grid(rows, "CSV"),
            kind = DocumentKind.CSV,
            warnings = warnings,
        )
    }

    private fun readXlsx(input: InputStream): AnswerFileReadResult {
        val sheet = XlsxWorksheetReader.read(input, XlsxWorksheetReader.SheetOrder.WORKBOOK_TAB)
        if (sheet.error != null) {
            return AnswerFileReadResult.Failed(sheet.error)
        }
        if (sheet.rows.isEmpty()) {
            return AnswerFileReadResult.Failed("XLSX 首个工作表没有可读取的行，请检查文件内容")
        }
        return AnswerFileReadResult.Ok(
            source = AnswerFileSource.Grid(sheet.rows, "XLSX"),
            kind = DocumentKind.XLSX,
        )
    }
}
