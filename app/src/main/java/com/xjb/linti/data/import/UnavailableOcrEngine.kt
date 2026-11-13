package com.xjb.linti.data.import

import java.io.InputStream

class UnavailableOcrEngine : OcrEngine {
    override suspend fun recognize(input: InputStream, fileName: String): OcrResult =
        OcrResult(
            text = "",
            warnings = listOf("本地 OCR 接口已预留，图片文字识别将在后续批次接入；本批请先使用 TXT/DOCX/CSV"),
            supported = false,
        )
}
