package com.xjb.linti.data.aigeneration

import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import org.junit.Assert.*
import org.junit.Test

class MaterialChunksTest {
    @Test fun splitAtParagraphsAndRespectsBound() {
        val text = "第一章 植物\n虚构叶片生长。\n\n第二章 光照\n" + "日".repeat(75)
        val chunks = MaterialChunks.split(text, 20)
        assertTrue(chunks.size > 3)
        assertTrue(chunks.all { it.length <= 20 && text.contains(it) })
        assertEquals(text.filterNot(Char::isWhitespace), chunks.joinToString("").filterNot(Char::isWhitespace))
    }

    @Test fun distributesRemaindersAcrossTypes() {
        val counts = mapOf(QuestionType.SINGLE to 5, QuestionType.MULTI to 2, QuestionType.JUDGE to 0, QuestionType.FILL to 3, QuestionType.ESSAY to 1)
        val batches = MaterialChunks.plan("甲".repeat(90), counts, 20)
        assertEquals(5, batches.size)
        counts.forEach { (type, count) -> assertEquals(count, batches.sumOf { it.counts[type] ?: 0 }) }
        assertTrue(batches.maxOf { it.counts.values.sum() } - batches.minOf { it.counts.values.sum() } <= 1)
        assertTrue(runCatching { MaterialChunks.plan("甲", mapOf(QuestionType.SINGLE to 0)) }.isFailure)
        assertTrue(runCatching { MaterialChunks.plan("甲", mapOf(QuestionType.SINGLE to 21)) }.isFailure)
    }

    @Test fun validatesFiveTypesAndSourceWithoutGuessing() {
        val base = AiDraftFields(type = "SINGLE", stem = "虚构问题", options = listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")), answer = "A", explanation = "解析", material = true, materialText = "虚构材料：甲与乙。", sourceExcerpt = "甲与乙")
        assertTrue(AiDraftValidator.validate(base).isEmpty())
        assertTrue(AiDraftValidator.validate(base.copy(sourceExcerpt = "不存在")).contains("出处片段不在材料中"))
        assertTrue(AiDraftValidator.validate(base.copy(type = "MULTI", answer = "A")).contains("多选题须至少有两个答案"))
        assertTrue(AiDraftValidator.validate(base.copy(type = "JUDGE", options = emptyList(), answer = "true")).isEmpty())
        val fill = base.copy(type = "FILL", options = emptyList(), stem = "____ 和 ____", acceptedAnswers = listOf(listOf("甲", "同义甲"), listOf("乙", "同义乙")))
        assertTrue(AiDraftValidator.validate(fill).isEmpty())
        assertTrue(AiDraftValidator.validate(fill.copy(acceptedAnswers = listOf(listOf("甲")))).contains("空数与答案数不一致"))
        assertTrue(AiDraftValidator.validate(base.copy(type = "ESSAY", options = emptyList(), answer = "")).contains("待补参考答案"))
    }
}
