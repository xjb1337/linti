package com.xjb.linti.data.ocr

/**
 * OCR 上传文件名：必须与上传副本的实际内容一致。
 * 图片上传副本一律编码为 JPEG，因此无论原图是 PNG/HEIC/WEBP，
 * 文件名与 MIME 都统一为 .jpg / image/jpeg（由 client 按扩展名推导）；
 * PDF 保持 .pdf。避免「PNG 内容 + .png 文件名 + image/png MIME」的不一致上传。
 */
object OcrUploadNaming {

    fun imageUploadFileName(displayName: String): String = forUpload(displayName, "jpg")

    fun pdfUploadFileName(displayName: String): String = forUpload(displayName, "pdf")

    private fun forUpload(displayName: String, extension: String): String {
        val base = displayName.trim().substringBeforeLast('.')
        val sanitized = base
            .take(MAX_BASE_LENGTH)
            .map { char ->
                when {
                    char.isLetterOrDigit() -> char
                    char in " _-（）()·、：:." -> char
                    else -> '_'
                }
            }
            .joinToString("")
            .trim()
            .trim('_')
        if (sanitized.isEmpty()) {
            return "linti_upload.$extension"
        }
        return "$sanitized.$extension"
    }

    private const val MAX_BASE_LENGTH = 80
}
