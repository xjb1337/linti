package com.xjb.linti.data.aigeneration

import com.xjb.linti.domain.QuestionType

data class MaterialBatch(val index: Int, val text: String, val counts: Map<QuestionType, Int>)

object MaterialChunks {
    const val MAX_CHARS = 3000
    private val headings = Regex("(?m)^[ \t]*(?:第[一二三四五六七八九十百千0-9]+(?:章|节|篇|部分)|#{1,6}[ \t]+|[一二三四五六七八九十]+[、.．])")
    private val paragraphBreak = Regex("\\r?\\n[ \t]*\\r?\\n+")

    fun split(text: String, maxChars: Int = MAX_CHARS): List<String> {
        require(maxChars >= 2)
        // 所有块直接截取原文，保证跨行引用也可在原材料中逐字找到。
        val boundaries = (headings.findAll(text).map { it.range.first } +
            paragraphBreak.findAll(text).map { it.range.last + 1 }).distinct().sorted().toList()
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var limit = minOf(start + maxChars, text.length)
            if (limit < text.length && Character.isHighSurrogate(text[limit - 1]) && Character.isLowSurrogate(text[limit])) limit--
            val boundary = if (limit == text.length) limit else boundaries.lastOrNull {
                it > start && it <= limit && text.substring(start, it).isNotBlank()
            } ?: limit
            text.substring(start, boundary).trim().takeIf { it.isNotEmpty() }?.let(chunks::add)
            start = boundary
        }
        return chunks
    }

    fun plan(text: String, counts: Map<QuestionType, Int>, maxChars: Int = MAX_CHARS): List<MaterialBatch> {
        require(counts.values.all { it in 0..20 } && counts.values.sum() in 1..50)
        val chunks = split(text, maxChars)
        require(chunks.isNotEmpty())
        val allocations = chunks.map { mutableMapOf<QuestionType, Int>() }
        var offset = 0
        QuestionType.entries.forEach { type ->
            val count = counts[type] ?: 0
            repeat(count) { index ->
                val slot = (offset + index) % chunks.size
                allocations[slot][type] = (allocations[slot][type] ?: 0) + 1
            }
            offset = (offset + count) % chunks.size
        }
        return chunks.mapIndexedNotNull { index, chunk ->
            allocations[index].takeIf { it.isNotEmpty() }?.let { MaterialBatch(index + 1, chunk, it.toMap()) }
        }
    }
}
