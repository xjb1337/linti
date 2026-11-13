package com.xjb.linti.data.import

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

data class PdfTextResult(
    val text: String,
    val warning: String? = null,
)

interface PdfTextExtractor {
    fun extract(input: InputStream): PdfTextResult
}

class PdfBoxTextExtractor : PdfTextExtractor {
    override fun extract(input: InputStream): PdfTextResult {
        return runCatching {
            PDDocument.load(input).use { document ->
                PdfTextResult(PDFTextStripper().getText(document))
            }
        }.getOrElse { error ->
            PdfTextResult(
                text = "",
                warning = "PDF 文本层提取失败：${error.message ?: "文件可能受密码保护或已损坏"}",
            )
        }
    }
}

class PdfDocumentExtractor(
    private val textExtractor: PdfTextExtractor = PdfBoxTextExtractor(),
) : DocumentExtractor {
    override suspend fun extract(input: InputStream, fileName: String): ExtractionResult {
        val result = textExtractor.extract(input)
        if (result.text.isBlank()) {
            return ExtractionResult(
                text = "",
                kind = DocumentKind.PDF,
                warnings = listOfNotNull(
                    result.warning,
                    "未能提取 PDF 文本层，可改用图片 OCR 或 TXT",
                ).distinct(),
            )
        }
        return ExtractionResult(
            text = result.text,
            kind = DocumentKind.PDF,
            warnings = listOfNotNull(result.warning),
        )
    }
}
