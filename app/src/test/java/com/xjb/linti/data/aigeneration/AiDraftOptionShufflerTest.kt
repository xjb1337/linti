package com.xjb.linti.data.aigeneration

import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.QuestionOption
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class AiDraftOptionShufflerTest {
    // 原标签刻意乱序、不连续，并保留大小写和空白，检验换算基于标签而非原下标。
    private val options = listOf(
        QuestionOption(" H ", "甲"), QuestionOption("b", "乙"),
        QuestionOption("E", "丙"), QuestionOption("d", "丁"),
    )
    private val single = AiDraftFields("SINGLE", "虚构题干", options, "b", "虚构解析")

    private class DrawRandom(private val draws: List<Int>) : Random() {
        private var index = 0
        override fun nextBits(bitCount: Int): Int = error("不应请求原始随机位")
        override fun nextInt(until: Int): Int = draws[index++].also { require(it in 0 until until) }
    }

    private fun allDraws(size: Int): List<List<Int>> {
        if (size <= 1) return listOf(emptyList())
        return (0 until size).flatMap { draw -> allDraws(size - 1).map { listOf(draw) + it } }
    }

    private fun correctTexts(draft: AiDraftFields): Set<String> {
        val answers = AnswerNormalizer.normalizeLetters(draft.answer).toSet()
        return draft.options.filter { it.label.trim().uppercase().single() in answers }.map { it.text }.toSet()
    }

    @Test fun singleAndMultiAnswersFollowEveryFourOptionPermutation() {
        val inputs = listOf("H", "b", "E", "d").map { single.copy(answer = it) } +
            listOf("H,b", "H、E", "H/d", "b,E", "b、d", "E,d", "H,b,E", "H,b,d", "H,E,d", "b,E,d", "H,b,E,d")
                .map { single.copy(type = "MULTI", answer = it) }
        val permutations = mutableSetOf<List<String>>()
        for (draws in allDraws(options.size)) {
            for (input in inputs) {
                assertTrue(AiDraftValidator.validate(input).toString(), AiDraftValidator.validate(input).isEmpty())
                val result = AiDraftOptionShuffler.shuffleIfSafe(input, DrawRandom(draws))
                assertEquals(listOf("A", "B", "C", "D"), result.options.map { it.label })
                assertEquals(input.options.map { it.text }.toSet(), result.options.map { it.text }.toSet())
                assertEquals(correctTexts(input), correctTexts(result))
                assertEquals(result.answer.toList().sorted().joinToString(""), result.answer)
                assertTrue(AiDraftValidator.validate(result).isEmpty())
                permutations += result.options.map { it.text }
            }
        }
        assertEquals(24, permutations.size)
    }

    @Test fun knownDrawsProduceExpectedSingleAndMultiAnswers() {
        val draws = listOf(0, 1, 0)
        val shuffledSingle = AiDraftOptionShuffler.shuffleIfSafe(single, DrawRandom(draws))
        assertEquals(listOf("丙", "丁", "乙", "甲"), shuffledSingle.options.map { it.text })
        assertEquals("C", shuffledSingle.answer)
        val shuffledMulti = AiDraftOptionShuffler.shuffleIfSafe(single.copy(type = "MULTI", answer = "d、Ｈ"), DrawRandom(draws))
        assertEquals("BD", shuffledMulti.answer)
        assertEquals(setOf("甲", "丁"), correctTexts(shuffledMulti))
    }

    @Test fun handlesEveryAllowedOptionCount() {
        for (size in 2..8) {
            val input = single.copy(options = (0 until size).map { QuestionOption(('A' + it).toString(), "内容$it") },
                answer = ('A' + size - 1).toString())
            val output = AiDraftOptionShuffler.shuffleIfSafe(input, Random(size))
            assertEquals((0 until size).map { ('A' + it).toString() }, output.options.map { it.label })
            assertEquals(correctTexts(input), correctTexts(output))
        }
    }

    @Test fun recognizedReferencesInExplanationPreventShuffle() {
        val explanations = listOf(
            "A 项符合条件", "选 B", "B、C 均正确", "选项Ａ正确", "a项正确", "故B正确", "（D）正确",
            "答案为BC", "正确答案是 AB", "选AC", "BC两项均符合条件", "A和B都符合条件",
        )
        for (explanation in explanations) {
            val input = single.copy(explanation = explanation)
            assertEquals(explanation, input, AiDraftOptionShuffler.shuffleIfSafe(input, DrawRandom(emptyList())))
        }
    }

    @Test fun latinWordsAndIdentifiersAreNotTreatedAsStandaloneLabels() {
        val input = single.copy(explanation = "API 与 DNA、H2O 和 A_value 均为示例")
        val result = AiDraftOptionShuffler.shuffleIfSafe(input, DrawRandom(listOf(0, 1, 0)))
        assertEquals(listOf("丙", "丁", "乙", "甲"), result.options.map { it.text })
    }

    @Test fun judgeAndInvalidDraftsAreNotShuffledOrRandomized() {
        val drafts = listOf(
            single.copy(type = "JUDGE", options = emptyList(), answer = "true"),
            single.copy(answer = ""), single.copy(answer = "A"), single.copy(answer = "bb"),
            single.copy(explanation = ""), single.copy(stem = ""), single.copy(invalidOptions = true),
            single.copy(expectedType = "MULTI"), single.copy(options = options + QuestionOption("B", "重复")),
        )
        for (draft in drafts) {
            assertEquals(draft, AiDraftOptionShuffler.shuffleIfSafe(draft, DrawRandom(emptyList())))
        }
    }

    @Test fun injectedSeedReproducesSequence() {
        val first = Random(20261114)
        val second = Random(20261114)
        val sequence1 = List(100) { AiDraftOptionShuffler.shuffleIfSafe(single, first) }
        val sequence2 = List(100) { AiDraftOptionShuffler.shuffleIfSafe(single, second) }
        assertEquals(sequence1, sequence2)
        assertTrue(sequence1.map { it.answer }.toSet().size > 1)
    }

    @Test fun fixedSeedSimulationSpreadsAnswersAcrossAllPositions() {
        val random = Random(20261114)
        val counts = IntArray(4)
        repeat(20_000) {
            val answer = AiDraftOptionShuffler.shuffleIfSafe(single, random).answer.single()
            counts[answer - 'A']++
        }
        assertEquals(20_000, counts.sum())
        assertTrue("答案位置计数：${counts.toList()}", counts.all { it in 4_500..5_500 })
    }
}
