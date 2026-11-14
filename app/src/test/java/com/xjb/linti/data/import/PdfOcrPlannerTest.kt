package com.xjb.linti.data.import

import com.xjb.linti.data.ocr.OcrLimits
import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfOcrPlannerTest {

    private class FakePageSource(
        private val pageTexts: List<String>,
        private val encrypted: Boolean = false,
    ) : PdfPageTextSource {
        var closed = false
            private set

        override val pageCount: Int get() = pageTexts.size
        override val isEncrypted: Boolean get() = encrypted
        override fun pageText(index: Int): String = pageTexts[index]
        override fun close() {
            closed = true
        }
    }

    private fun inspectWith(pages: List<String>, encrypted: Boolean = false): PdfPageInspection {
        var closed = false
        val inspection = PdfPageInspector(
            sourceFactory = { input ->
                val fake = FakePageSource(pages, encrypted)
                object : PdfPageTextSource by fake {
                    override fun close() {
                        fake.close()
                        closed = true
                    }
                }
            },
        ).inspect(ByteArrayInputStream(ByteArray(0)))
        assertTrue("inspect 结束后必须关闭文档来源", closed || pages.isEmpty())
        return inspection
    }

    @Test
    fun `page text rules treat blank and page-number-only pages as scan pages`() {
        val blank = PdfPageTextRules.classify(0, "   \n\t")
        assertFalse(blank.usable)
        assertTrue(blank.reason.orEmpty().contains("扫描页"))

        val bareNumber = PdfPageTextRules.classify(1, "- 3 -\n")
        assertFalse(bareNumber.usable)

        val slashTotal = PdfPageTextRules.classify(2, "3 / 10")
        assertFalse(slashTotal.usable)

        val chapterPage = PdfPageTextRules.classify(3, "第 4 页，共 12 页")
        assertFalse(chapterPage.usable)

        val realContent = PdfPageTextRules.classify(4, "1. 下列关于虚构疾病的说法哪项正确？")
        assertTrue(realContent.usable)
    }

    @Test
    fun `page with real content plus page footer stays usable`() {
        val text = buildString {
            appendLine("1. 虚构题干：下列哪项描述与教材一致？")
            appendLine("A. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁")
            appendLine("第 4 页 共 12 页")
        }
        val info = PdfPageTextRules.classify(0, text)
        assertTrue(info.usable)
    }

    @Test
    fun `inspection classifies each page and closes source`() {
        val inspection = inspectWith(
            listOf(
                "1. 文字页一：虚构题干是否完整？\n答案：A",
                "",
                "2. 文字页二：虚构题干是否完整？\n答案：B",
            ),
        )
        assertEquals(3, inspection.pageCount)
        assertTrue(inspection.pages[0].usable)
        assertFalse(inspection.pages[1].usable)
        assertTrue(inspection.pages[2].usable)
        assertEquals(listOf(1), inspection.scanPages)
        assertEquals(2, inspection.usablePageCount)
    }

    @Test
    fun `planner keeps local path when every page has usable text`() {
        val plan = PdfOcrPlanner.plan(
            PdfPageInspection(
                pageCount = 2,
                pages = listOf(
                    PdfPageTextRules.classify(0, "1. 文字页：题干完整。\n答案：A"),
                    PdfPageTextRules.classify(1, "2. 文字页：题干完整。\n答案：B"),
                ),
            ),
        )
        assertEquals(PdfOcrPlan.AllTextUsable(2), plan)
    }

    @Test
    fun `planner requests confirmation when scan pages exist`() {
        val inspection = PdfPageInspection(
            pageCount = 3,
            pages = listOf(
                PdfPageTextRules.classify(0, "1. 文字页：题干完整。\n答案：A"),
                PdfPageTextRules.classify(1, ""),
                PdfPageTextRules.classify(2, "- 2 -"),
            ),
        )
        val plan = PdfOcrPlanner.plan(inspection)
        assertTrue(plan is PdfOcrPlan.NeedsOcr)
        plan as PdfOcrPlan.NeedsOcr
        assertEquals(listOf(1, 2), plan.scanPages)
        assertEquals(3, plan.pageCount)
        assertEquals(1, plan.usablePageCount)
    }

    @Test
    fun `planner reports encrypted and broken files distinctly`() {
        val encrypted = PdfOcrPlanner.plan(
            PdfPageInspection(pageCount = 0, pages = emptyList(), encrypted = true, loadError = "已加密"),
        )
        assertTrue(encrypted is PdfOcrPlan.Encrypted)

        val broken = PdfOcrPlanner.plan(
            PdfPageInspection(pageCount = 0, pages = emptyList(), loadError = "无法解析"),
        )
        assertTrue(broken is PdfOcrPlan.LoadFailed)
    }

    @Test
    fun `planner rejects empty pdf`() {
        val plan = PdfOcrPlanner.plan(PdfPageInspection(pageCount = 0, pages = emptyList()))
        assertTrue(plan is PdfOcrPlan.Empty)
    }

    @Test
    fun `over limit pdf skips per page extraction and is intercepted`() {
        // 页数超上限：inspectFile 必须在逐页提取前拦截（pageText 一次都不调用）
        var pageTextCalls = 0
        var closed = false
        val fakeSource = object : PdfPageTextSource {
            override val pageCount: Int = OcrLimits.MAX_PAGES + 5
            override val isEncrypted: Boolean = false
            override fun pageText(index: Int): String {
                pageTextCalls++
                return "第 $index 页"
            }

            override fun close() {
                closed = true
            }
        }
        val inspection = PdfPageInspector().inspectFile(
            File("unused.pdf"),
            fileSourceFactory = { fakeSource },
        )
        assertEquals(OcrLimits.MAX_PAGES + 5, inspection.pageCount)
        assertTrue(inspection.tooManyPages)
        assertTrue(inspection.pages.isEmpty())
        assertEquals("超限时不得逐页提取", 0, pageTextCalls)
        assertTrue("来源必须关闭", closed)

        val plan = PdfOcrPlanner.plan(inspection)
        assertTrue(plan is PdfOcrPlan.TooManyPages)
        assertEquals(OcrLimits.MAX_PAGES + 5, (plan as PdfOcrPlan.TooManyPages).pageCount)
    }

    @Test
    fun `inspectFile passes through broken file results`() {
        // InvalidPasswordException 构造为包私有，加密路径由 planner 的直构 inspection 用例覆盖
        val broken = PdfPageInspector().inspectFile(
            File("unused.pdf"),
            fileSourceFactory = { throw IllegalStateException("corrupt") },
        )
        assertTrue(broken.loadError.orEmpty().contains("损坏"))
        assertTrue(!broken.encrypted)
    }
}
