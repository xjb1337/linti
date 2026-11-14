package com.xjb.linti.data.import

import com.xjb.linti.data.ocr.OcrPageData

/** 混合 PDF 合并模式：只把扫描页换成 OCR 文字，或整份采用 OCR。 */
enum class PdfOcrMergeMode(val label: String) {
    SCANS_ONLY("识别扫描页并保留文字页"),
    FULL_OCR("整份采用在线识别"),
}

data class MergedPdfPage(
    val index: Int,
    val text: String,
    val fromOcr: Boolean,
)

data class PdfOcrMergeResult(
    val pages: List<MergedPdfPage>,
    val warnings: List<String>,
) {
    /** 供统一解析的整份文本：每页只取一份文字，按页序拼接。 */
    val joinedText: String get() = pages.joinToString("\n") { it.text }
}

/**
 * 把 OCR 逐页结果与本地逐页文字按页序合并：
 * - SCANS_ONLY：可用文字页保留本地提取，扫描页/页码水印页采用 OCR；
 * - FULL_OCR：全部页面采用 OCR；
 * - OCR 返回的页数必须与本地页数一致（客户端已校验，这里兜底）；
 * - 真实空白页允许保留页位并提示；缺页在上传前已按 PAGE_MISMATCH 拒绝。
 */
object PdfOcrMerger {
    fun merge(
        inspection: PdfPageInspection,
        ocrPages: List<OcrPageData>,
        mode: PdfOcrMergeMode,
    ): PdfOcrMergeResult {
        require(inspection.pageCount == ocrPages.size) {
            "OCR 页数（${ocrPages.size}）与本地页数（${inspection.pageCount}）不一致"
        }
        val warnings = mutableListOf<String>()
        val pages = inspection.pages.map { localPage ->
            val ocrPage = ocrPages[localPage.index]
            val useOcr = mode == PdfOcrMergeMode.FULL_OCR || !localPage.usable
            val text = (if (useOcr) ocrPage.text else localPage.text).trim()
            if (text.isEmpty()) {
                warnings += "第 ${localPage.index + 1} 页未识别到文字（可能为空白页），已保留页位"
            }
            MergedPdfPage(
                index = localPage.index,
                text = text,
                fromOcr = useOcr,
            )
        }
        when (mode) {
            PdfOcrMergeMode.SCANS_ONLY -> {
                val ocrCount = pages.count { it.fromOcr }
                val localCount = pages.count { !it.fromOcr }
                if (localCount > 0) {
                    warnings += "已保留 $localCount 个文字页的本地提取，$ocrCount 个扫描页采用在线识别"
                } else {
                    warnings += "全部 ${pages.size} 页均为扫描页，已采用在线识别文字"
                }
            }
            PdfOcrMergeMode.FULL_OCR -> warnings += "整份 PDF（${pages.size} 页）已采用在线识别文字"
        }
        return PdfOcrMergeResult(pages = pages, warnings = warnings.distinct())
    }
}
