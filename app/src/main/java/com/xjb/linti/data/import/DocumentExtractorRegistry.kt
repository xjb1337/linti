package com.xjb.linti.data.import

import java.io.InputStream

class DocumentExtractorRegistry(
    private val extractors: Map<DocumentKind, DocumentExtractor>,
) {
    suspend fun extract(
        input: InputStream,
        fileName: String,
        kindOverride: DocumentKind? = null,
    ): ExtractionResult {
        val kind = kindOverride ?: DocumentKind.fromFileName(fileName)
            ?: return ExtractionResult(
                text = "",
                kind = DocumentKind.TXT,
                warnings = listOf("无法根据文件名识别格式「$fileName」，请改用 TXT/DOCX/CSV/XLSX"),
            )
        val extractor = extractors[kind]
            ?: return ExtractionResult(
                text = "",
                kind = kind,
                warnings = listOf("${kind.label} 格式暂不支持"),
            )
        return extractor.extract(input, fileName)
    }
}
