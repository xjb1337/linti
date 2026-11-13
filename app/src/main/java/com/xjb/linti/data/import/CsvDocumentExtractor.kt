package com.xjb.linti.data.import

import java.io.InputStream

class CsvDocumentExtractor : DocumentExtractor {
    override suspend fun extract(input: InputStream, fileName: String): ExtractionResult {
        val decoded = TextDecoder.decode(input.readBytes())
        val rows = CsvReader.parse(decoded.text)
        val conversion = TabularQuestionMapper.convert(rows, "CSV")
        val warnings = buildList {
            if (decoded.usedFallbackEncoding) {
                add("CSV 不是有效 UTF-8，已使用 ${decoded.charsetName} 解码")
            }
            addAll(conversion.warnings)
        }
        return ExtractionResult(
            text = conversion.text,
            kind = DocumentKind.CSV,
            warnings = warnings,
        )
    }
}
