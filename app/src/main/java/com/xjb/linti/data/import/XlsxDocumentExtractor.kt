package com.xjb.linti.data.import

import java.io.InputStream

class XlsxDocumentExtractor : DocumentExtractor {
    override suspend fun extract(input: InputStream, fileName: String): ExtractionResult {
        if (fileName.substringAfterLast('.', "").equals("xls", ignoreCase = true)) {
            return ExtractionResult(
                text = "",
                kind = DocumentKind.XLSX,
                warnings = listOf("暂不支持旧版 .xls，请另存为 .xlsx 或 CSV 后重试"),
            )
        }
        val sheet = XlsxWorksheetReader.read(input)
        if (sheet.error != null) {
            return ExtractionResult(
                text = "",
                kind = DocumentKind.XLSX,
                warnings = listOf(sheet.error),
            )
        }
        if (sheet.rows.isEmpty()) {
            return ExtractionResult(
                text = "",
                kind = DocumentKind.XLSX,
                warnings = listOf("XLSX 第一张工作表没有可读取的行"),
            )
        }
        val conversion = TabularQuestionMapper.convert(sheet.rows, "XLSX")
        return ExtractionResult(
            text = conversion.text,
            kind = DocumentKind.XLSX,
            warnings = conversion.warnings,
        )
    }
}
