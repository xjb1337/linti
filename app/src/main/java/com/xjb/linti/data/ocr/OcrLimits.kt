package com.xjb.linti.data.ocr

/**
 * DeepSeek OCR 官方限制与客户端侧超时/上限。
 * 官方：单文件 ≤100 MiB（超出 413）、≤1000 页、上传须 120 秒内完成、总时长 180 秒。
 * App 未额外收紧文件大小/页数（按官方上限展示给用户）。
 */
object OcrLimits {
    const val MAX_FILE_BYTES: Long = 100L * 1024 * 1024
    const val MAX_PAGES: Int = 1000
    const val UPLOAD_TIMEOUT_SECONDS: Long = 120
    const val TOTAL_TIMEOUT_SECONDS: Long = 180

    /** 响应体读取上限（识别结果为纯文本 JSON，远小于该值；超限视为异常响应）。 */
    const val MAX_RESPONSE_BYTES: Long = 64L * 1024 * 1024

    /** 上传副本最长边（像素）：仅作用于上传副本，保证文字可读同时控制体积。 */
    const val UPLOAD_MAX_DIMENSION_PX: Int = 2400

    /** 上传副本 JPEG 压缩质量。 */
    const val UPLOAD_JPEG_QUALITY: Int = 85

    /** 解码预览时的像素上限（防超大图 OOM；按 4:2:0 近似 4 字节/像素）。 */
    const val PREVIEW_MAX_PIXELS: Int = 24_000_000
}
