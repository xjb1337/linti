package com.xjb.linti.data.import

import java.io.InputStream

class TxtDocumentExtractor : DocumentExtractor {
    override suspend fun extract(input: InputStream, fileName: String): ExtractionResult {
        val bytes = input.readBytes()
        val decoded = TextDecoder.decode(bytes)
        val warnings = if (decoded.usedFallbackEncoding) {
            listOf("TXT 不是有效 UTF-8，已使用 ${decoded.charsetName} 解码")
        } else {
            emptyList()
        }
        return ExtractionResult(
            text = decoded.text,
            kind = DocumentKind.TXT,
            warnings = warnings,
        )
    }
}
