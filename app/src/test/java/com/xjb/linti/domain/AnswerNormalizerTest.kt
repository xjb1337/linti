package com.xjb.linti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnswerNormalizerTest {
    @Test
    fun `normalizes sorted letters and fullwidth characters`() {
        assertEquals("ABC", AnswerNormalizer.normalizeLetters("ｃ、A、b"))
        assertEquals("", AnswerNormalizer.normalizeLetters("没有字母"))
    }

    @Test
    fun `normalizes common judge spellings`() {
        assertEquals("true", AnswerNormalizer.normalizeJudge("正确"))
        assertEquals("true", AnswerNormalizer.normalizeJudge("√"))
        assertEquals("true", AnswerNormalizer.normalizeJudge("T"))
        assertEquals("false", AnswerNormalizer.normalizeJudge("错误"))
        assertEquals("false", AnswerNormalizer.normalizeJudge("×"))
        assertEquals("false", AnswerNormalizer.normalizeJudge("false"))
        assertNull(AnswerNormalizer.normalizeJudge("也许"))
    }

    @Test
    fun `fill comparison ignores case width and repeated spaces`() {
        assertEquals(
            AnswerNormalizer.normalizeFillForComparison(" ＡＢＣ　 测试 "),
            AnswerNormalizer.normalizeFillForComparison("abc 测试"),
        )
    }
}
