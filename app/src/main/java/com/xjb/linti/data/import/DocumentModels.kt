package com.xjb.linti.data.import

import java.io.InputStream
import java.util.Locale

enum class DocumentKind(
    val storageValue: String,
    val label: String,
    val extensions: Set<String>,
) {
    PDF("pdf", "PDF", setOf("pdf")),
    DOCX("docx", "Word", setOf("docx", "doc")),
    XLSX("xlsx", "Excel", setOf("xlsx", "xls")),
    CSV("csv", "CSV", setOf("csv")),
    TXT("txt", "TXT", setOf("txt")),
    IMAGE("image", "图片", setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")),
    CAMERA("camera", "拍照", emptySet());

    companion object {
        fun fromFileName(fileName: String): DocumentKind? {
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return entries.firstOrNull { extension in it.extensions }
        }

        fun fromStorage(value: String?): DocumentKind? =
            entries.firstOrNull { it.storageValue == value }
    }
}

data class ExtractionResult(
    val text: String,
    val kind: DocumentKind,
    val warnings: List<String> = emptyList(),
)

interface DocumentExtractor {
    suspend fun extract(input: InputStream, fileName: String): ExtractionResult
}

data class OcrResult(
    val text: String,
    val warnings: List<String> = emptyList(),
    val supported: Boolean = true,
)

interface OcrEngine {
    suspend fun recognize(input: InputStream, fileName: String): OcrResult
}
