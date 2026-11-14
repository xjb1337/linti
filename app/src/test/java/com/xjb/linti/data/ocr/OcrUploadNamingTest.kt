package com.xjb.linti.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrUploadNamingTest {

    @Test
    fun `png input is renamed to jpg to match jpeg upload copy`() {
        // 裁切副本一律编码为 JPEG：PNG 输入也不得带 .png 上传
        assertEquals("screenshot.jpg", OcrUploadNaming.imageUploadFileName("screenshot.png"))
        assertEquals("扫描件.jpg", OcrUploadNaming.imageUploadFileName("扫描件.PDF"))
        assertTrue(OcrUploadNaming.imageUploadFileName("photo.heic").endsWith(".jpg"))
    }

    @Test
    fun `filename without usable base falls back to generic jpg`() {
        assertEquals("linti_upload.jpg", OcrUploadNaming.imageUploadFileName(".png"))
        assertEquals("linti_upload.jpg", OcrUploadNaming.imageUploadFileName(""))
        assertEquals("linti_upload.jpg", OcrUploadNaming.imageUploadFileName("///"))
    }

    @Test
    fun `pdf upload keeps pdf extension with normalized base`() {
        assertEquals("scan.pdf", OcrUploadNaming.pdfUploadFileName("scan.PDF"))
        assertEquals("试卷文件.pdf", OcrUploadNaming.pdfUploadFileName("试卷文件.pdf"))
        assertEquals("linti_upload.pdf", OcrUploadNaming.pdfUploadFileName(""))
    }

    @Test
    fun `unsafe characters are sanitized and length is bounded`() {
        val weird = OcrUploadNaming.imageUploadFileName("""a"b/c\d?e%f*.jpg""")
        assertTrue(!weird.contains('"') && !weird.contains('*') && !weird.contains('/') && !weird.contains('%'))
        assertTrue(weird.endsWith(".jpg"))
        val long = OcrUploadNaming.imageUploadFileName("很长的名字".repeat(40) + ".png")
        assertTrue("基础名长度应有上限", long.length <= 80 + ".jpg".length)
    }
}
