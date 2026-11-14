package com.xjb.linti.data.import

import com.xjb.linti.domain.ParseResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDraftPostProcessorTest {

    private val parser = LocalQuestionParser()

    @Test
    fun `cross page question parsed from joined text stays single`() {
        // 题干在第 1 页末尾，答案在第 2 页开头：按页序拼接后统一解析，不得误拆
        val page1 = "1. 下列关于虚构药物作用的说法，正确的是哪一项？\nA. 选项甲 B. 选项乙"
        val page2 = "C. 选项丙 D. 选项丁\n答案：C\n解析：教学解析"
        val parse = parser.parse("$page1\n$page2")
        assertEquals(1, parse.questions.size)
        val post = OcrDraftPostProcessor.process(parse, listOf(page1, page2))
        assertEquals(1, post.drafts.size)
        assertEquals("C", post.drafts.single().answer)
        assertTrue(post.drafts.single().options.size == 4)
    }

    @Test
    fun `missing answer is flagged for review and never treated as valid`() {
        val text = "1. 缺答案的虚构题干是否完整？\nA. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁"
        val parse = parser.parse(text)
        val post = OcrDraftPostProcessor.process(parse, listOf(text))
        val draft = post.drafts.single()
        assertTrue(draft.needsReview)
        assertTrue(draft.answer.isBlank())
        assertTrue(post.warnings.any { it.contains("待核对") || it.contains("OCR") })
    }

    @Test
    fun `ocr noise around answer letters is rejected by strict validation`() {
        // OCR 杂文混入答案行：不能用宽松字母提取把杂文变有效答案
        val text = "1. 答案行混入杂文的虚构题干是否完整？\nA. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁\n答案：本题选B因为教材第3章"
        val parse = parser.parse(text)
        val post = OcrDraftPostProcessor.process(parse, listOf(text))
        val draft = post.drafts.single()
        assertTrue(draft.needsReview)
        assertTrue(draft.reviewReasons.any { it.contains("答案") })
    }

    @Test
    fun `fixed answer clears strict violation on revalidation`() {
        // 用户在校对页修正答案后，OCR 严格复检理由随重新验证解除，不再卡入库门槛
        val text = "1. 答案行混入杂文的虚构题干是否完整？\nA. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁\n答案：本题选B因为教材第3章"
        val parse = parser.parse(text)
        val post = OcrDraftPostProcessor.process(parse, listOf(text))
        val flagged = post.drafts.single()
        assertTrue(flagged.needsReview)

        val fixed = flagged.copy(answer = "B")
        val validation = DraftValidator.validate(fixed)
        val reasons = DraftValidator.mergeReasons(fixed.reviewReasons, validation)
        assertTrue(reasons.isEmpty())
        // VM 编辑路径：afterEdit 按最终理由重算 needsReview，修好即解除
        val edited = SplitDraftResolver.afterEdit(fixed, flagged, null)
        assertTrue(!edited.draft.needsReview)
        assertTrue(edited.draft.reviewReasons.isEmpty())
    }

    @Test
    fun `valid question passes strict revalidation`() {
        val text = "1. 合规虚构题干是否完整？\nA. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁\n答案：B\n解析：教学解析"
        val parse = parser.parse(text)
        val post = OcrDraftPostProcessor.process(parse, listOf(text))
        val draft = post.drafts.single()
        assertTrue(!draft.needsReview)
        assertEquals("B", draft.answer)
    }

    @Test
    fun `drafts map back to source pages via snippet`() {
        val page1 = "1. 第一页虚构题干是否完整？\nA. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁\n答案：A"
        val page2 = "2. 第二页虚构题干是否完整？\nA. 选项甲 B. 选项乙 C. 选项丙 D. 选项丁\n答案：D"
        val parse = parser.parse("$page1\n$page2")
        val post = OcrDraftPostProcessor.process(parse, listOf(page1, page2))
        assertEquals(2, post.drafts.size)
        assertEquals(1, post.sourcePages[0])
        assertEquals(2, post.sourcePages[1])
    }

    @Test
    fun `empty parse result produces empty drafts`() {
        val post = OcrDraftPostProcessor.process(
            ParseResult(questions = emptyList(), warnings = listOf("未识别到题目")),
            listOf("纯噪声页面"),
        )
        assertTrue(post.drafts.isEmpty())
    }
}
