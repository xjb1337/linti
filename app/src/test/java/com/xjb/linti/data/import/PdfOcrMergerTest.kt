package com.xjb.linti.data.import

import com.xjb.linti.data.ocr.OcrPageData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfOcrMergerTest {

    private fun inspection(pages: List<Pair<String, Boolean>>): PdfPageInspection =
        PdfPageInspection(
            pageCount = pages.size,
            pages = pages.mapIndexed { index, (text, usable) ->
                PdfPageTextInfo(index = index, text = text, usable = usable)
            },
        )

    private fun ocrPages(texts: List<String>): List<OcrPageData> =
        texts.mapIndexed { index, text ->
            OcrPageData(index = index, text = text, finishReason = "stop")
        }

    @Test
    fun `scans only keeps local text for usable pages and ocr for scan pages`() {
        val merged = PdfOcrMerger.merge(
            inspection = inspection(
                listOf(
                    "1. 本地文字页题干是否完整？\n答案：A" to true,
                    "（无文字层）" to false,
                ),
            ),
            ocrPages = ocrPages(
                listOf(
                    "页眉噪声 1. 本地文字页题干是否完整？\n答案：A",
                    "2. 扫描页识别出的题干是否完整？\n答案：B",
                ),
            ),
            mode = PdfOcrMergeMode.SCANS_ONLY,
        )
        assertEquals(2, merged.pages.size)
        assertTrue(!merged.pages[0].fromOcr)
        assertEquals("1. 本地文字页题干是否完整？\n答案：A", merged.pages[0].text)
        assertTrue(merged.pages[1].fromOcr)
        assertTrue(merged.pages[1].text.contains("扫描页识别出的题干"))
        assertTrue(merged.warnings.any { it.contains("1 个文字页") && it.contains("1 个扫描页") })
    }

    @Test
    fun `full ocr replaces every page`() {
        val merged = PdfOcrMerger.merge(
            inspection = inspection(
                listOf(
                    "1. 本地文字页题干是否完整？" to true,
                    "2. 第二页题干是否完整？" to true,
                ),
            ),
            ocrPages = ocrPages(listOf("OCR 第一页", "OCR 第二页")),
            mode = PdfOcrMergeMode.FULL_OCR,
        )
        assertTrue(merged.pages.all { it.fromOcr })
        assertEquals("OCR 第一页\nOCR 第二页", merged.joinedText)
        assertTrue(merged.warnings.any { it.contains("整份") })
    }

    @Test
    fun `blank ocr page keeps slot with warning`() {
        val merged = PdfOcrMerger.merge(
            inspection = inspection(listOf("" to false)),
            ocrPages = ocrPages(listOf("   ")),
            mode = PdfOcrMergeMode.FULL_OCR,
        )
        assertEquals(1, merged.pages.size)
        assertEquals("", merged.pages[0].text)
        assertTrue(merged.warnings.any { it.contains("第 1 页未识别到文字") })
    }

    @Test
    fun `page count mismatch is rejected`() {
        val error = runCatching {
            PdfOcrMerger.merge(
                inspection = inspection(listOf("文字" to true, "文字" to true)),
                ocrPages = ocrPages(listOf("只识别出一页")),
                mode = PdfOcrMergeMode.SCANS_ONLY,
            )
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun `joined text keeps page order`() {
        val merged = PdfOcrMerger.merge(
            inspection = inspection(listOf("第一页" to false, "第二页" to false, "第三页" to false)),
            ocrPages = ocrPages(listOf("OCR-3", "OCR-1", "OCR-2")),
            mode = PdfOcrMergeMode.FULL_OCR,
        )
        assertEquals("OCR-3\nOCR-1\nOCR-2", merged.joinedText)
    }
}
