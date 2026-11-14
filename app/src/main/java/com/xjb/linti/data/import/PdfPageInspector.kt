package com.xjb.linti.data.import

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.xjb.linti.data.ocr.OcrLimits
import java.io.File

/** 单页文字检查结果。index 为 0 起页序；usable=false 时 reason 给出原因。 */
data class PdfPageTextInfo(
    val index: Int,
    val text: String,
    val usable: Boolean,
    val reason: String? = null,
)

data class PdfPageInspection(
    val pageCount: Int,
    val pages: List<PdfPageTextInfo>,
    val encrypted: Boolean = false,
    val loadError: String? = null,
    // 页数超上限：跳过逐页提取，只保留页数供拦截判断
    val tooManyPages: Boolean = false,
) {
    val scanPages: List<Int> get() = pages.filterNot { it.usable }.map { it.index }
    val usablePageCount: Int get() = pages.count { it.usable }
}

/**
 * 逐页读取 PDF 文字层的来源抽象：单测注入假实现，运行时用 PDFBox。
 * 实现 close 前必须能重复读取任意页。
 */
interface PdfPageTextSource : AutoCloseable {
    val pageCount: Int
    val isEncrypted: Boolean
    fun pageText(index: Int): String
    override fun close()
}

class PdfBoxPageTextSource(
    input: java.io.InputStream,
) : PdfPageTextSource {
    private val document: PDDocument
    private val stripper = PDFTextStripper()

    init {
        // 一次性读入内存：逐页抽取需要重复打开同一文档，同时限制读取量
        val bytes = input.readBytes().also { bytes ->
            if (bytes.size > OcrLimits.MAX_FILE_BYTES) {
                throw IllegalArgumentException("PDF 超过大小上限")
            }
        }
        document = PDDocument.load(bytes)
    }

    override val pageCount: Int get() = document.numberOfPages
    override val isEncrypted: Boolean get() = document.isEncrypted

    override fun pageText(index: Int): String {
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        return stripper.getText(document)
    }

    override fun close() {
        document.close()
    }
}

/** 直接从文件加载（PDDocument.load(File) 流式解析），避免在内存里再持有一份完整字节。 */
class PdfBoxFilePageTextSource(
    private val file: File,
) : PdfPageTextSource {
    private val document: PDDocument
    private val stripper = PDFTextStripper()

    init {
        if (file.length() > OcrLimits.MAX_FILE_BYTES) {
            throw IllegalArgumentException("PDF 超过大小上限")
        }
        document = PDDocument.load(file)
    }

    override val pageCount: Int get() = document.numberOfPages
    override val isEncrypted: Boolean get() = document.isEncrypted

    override fun pageText(index: Int): String {
        stripper.startPage = index + 1
        stripper.endPage = index + 1
        return stripper.getText(document)
    }

    override fun close() {
        document.close()
    }
}
/**
 * 保守的「可用正文」判定：
 * - 空白页 → 扫描页；
 * - 整页只有页码/「第 X 页/共 Y 页」等页脚样式（且总长极短）→ 疑似页码/水印，不可用；
 * - 其余一律视为可用正文（不试图猜正文内容，避免误伤真实文字页）。
 */
object PdfPageTextRules {
    const val PAGE_NUMBER_ONLY_LIMIT = 40

    private val BARE_NUMBER = Regex("""^\s*[-–—－－]?\s*\d{1,4}\s*[-–—－－]?\s*$""")
    private val NUMBER_SLASH_TOTAL = Regex("""^\s*\d{1,4}\s*/\s*\d{1,4}\s*$""")
    private val CHAPTER_PAGE = Regex("""^\s*第\s*\d{1,4}\s*页(\s*[,，/；;]?\s*(共\s*\d{1,4}\s*页)?)?\s*$""")

    fun classify(index: Int, text: String): PdfPageTextInfo {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return PdfPageTextInfo(index, text, usable = false, reason = "扫描页：未提取到文字层")
        }
        if (trimmed.length <= PAGE_NUMBER_ONLY_LIMIT && looksLikePageNumberOrWatermark(trimmed)) {
            return PdfPageTextInfo(index, text, usable = false, reason = "疑似页码/水印，未提取到正文")
        }
        return PdfPageTextInfo(index, text, usable = true)
    }

    private fun looksLikePageNumberOrWatermark(trimmed: String): Boolean =
        BARE_NUMBER.matches(trimmed) ||
            NUMBER_SLASH_TOTAL.matches(trimmed) ||
            CHAPTER_PAGE.matches(trimmed)
}

class PdfPageInspector(
    private val sourceFactory: (java.io.InputStream) -> PdfPageTextSource = ::PdfBoxPageTextSource,
) {
    /**
     * 打开 PDF 并逐页检查文字层。任何打开/读取失败都转为结果内的 loadError，
     * 不抛出：调用方据此给「可处理提示」或整份在线识别入口。
     */
    fun inspect(input: java.io.InputStream): PdfPageInspection {
        val source = try {
            sourceFactory(input)
        } catch (error: InvalidPasswordException) {
            return encryptedInspection()
        } catch (error: Exception) {
            return brokenInspection(error)
        }
        return inspectSource(source)
    }

    /**
     * 从文件直接检查：PDDocument.load(File) 流式解析，不在内存里重复持有整份字节。
     * 页数超上限时在逐页提取前拦截，只返回页数。
     */
    fun inspectFile(
        file: File,
        fileSourceFactory: (File) -> PdfPageTextSource = ::PdfBoxFilePageTextSource,
    ): PdfPageInspection {
        val source = try {
            fileSourceFactory(file)
        } catch (error: InvalidPasswordException) {
            return encryptedInspection()
        } catch (error: IllegalArgumentException) {
            return PdfPageInspection(
                pageCount = 0,
                pages = emptyList(),
                loadError = error.message ?: "PDF 超过大小上限",
            )
        } catch (error: Exception) {
            return brokenInspection(error)
        }
        return inspectSource(source)
    }

    private fun inspectSource(source: PdfPageTextSource): PdfPageInspection = source.use { pages ->
        // 页数超上限：逐页提取前拦截（官方 OCR 也拒绝超 1000 页的文件）
        if (pages.pageCount > OcrLimits.MAX_PAGES) {
            return PdfPageInspection(
                pageCount = pages.pageCount,
                pages = emptyList(),
                encrypted = pages.isEncrypted,
                tooManyPages = true,
            )
        }
        val infos = (0 until pages.pageCount).map { index ->
            val text = runCatching { pages.pageText(index) }.getOrElse { "" }
            PdfPageTextRules.classify(index, text)
        }
        PdfPageInspection(pageCount = pages.pageCount, pages = infos, encrypted = pages.isEncrypted)
    }

    private fun encryptedInspection() = PdfPageInspection(
        pageCount = 0,
        pages = emptyList(),
        encrypted = true,
        loadError = "PDF 已加密，请先解除密码保护后重新导入",
    )

    private fun brokenInspection(error: Exception) = PdfPageInspection(
        pageCount = 0,
        pages = emptyList(),
        encrypted = false,
        loadError = "PDF 无法解析，文件可能损坏：${error.message ?: "未知错误"}",
    )
}

/** PDF 导入决策：全部可用走本地；含扫描页给在线识别入口；损坏/加密给可处理提示。 */
sealed interface PdfOcrPlan {
    /** 全部页面有可用文字层：完全沿用现有本地导入路径，不发起任何网络请求。 */
    data class AllTextUsable(val pageCount: Int) : PdfOcrPlan

    /** 含扫描页（或页码/水印页）：给「扫描页在线识别」入口，用户确认后才上传。 */
    data class NeedsOcr(
        val pageCount: Int,
        val scanPages: List<Int>,
        val usablePageCount: Int,
    ) : PdfOcrPlan

    /** 本地解析失败（损坏）：保留「整份在线识别」尝试入口，页数未知。 */
    data class LoadFailed(val message: String) : PdfOcrPlan

    /** 加密：本地与官方 OCR 都无法处理，给明确提示。 */
    data class Encrypted(val message: String) : PdfOcrPlan

    /** 没有页面可读。 */
    data class Empty(val message: String) : PdfOcrPlan

    /** 页数超上限：在线识别不可用（官方拒绝超 1000 页），逐页提取已被跳过。 */
    data class TooManyPages(val pageCount: Int) : PdfOcrPlan
}

object PdfOcrPlanner {
    fun plan(inspection: PdfPageInspection): PdfOcrPlan = when {
        inspection.encrypted -> PdfOcrPlan.Encrypted("PDF 已加密，请先解除密码保护后重新导入")
        inspection.tooManyPages -> PdfOcrPlan.TooManyPages(inspection.pageCount)
        inspection.loadError != null -> PdfOcrPlan.LoadFailed(inspection.loadError)
        inspection.pageCount == 0 -> PdfOcrPlan.Empty("PDF 没有可读取的页面")
        inspection.scanPages.isEmpty() -> PdfOcrPlan.AllTextUsable(inspection.pageCount)
        else -> PdfOcrPlan.NeedsOcr(
            pageCount = inspection.pageCount,
            scanPages = inspection.scanPages,
            usablePageCount = inspection.usablePageCount,
        )
    }
}
