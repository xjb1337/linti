package com.xjb.linti.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FlashcardAnswerTest {
    @Test
    fun `single choice resolves its only label`() {
        val answer = resolveFlashcardAnswer(
            question(type = QuestionType.SINGLE, answer = " B "),
        )

        assertEquals(FlashcardAnswer.Resolved(highlightedLabels = setOf("B")), answer)
    }

    @Test
    fun `multiple choice accepts common separators and casing`() {
        listOf(
            "ACD" to setOf("A", "C", "D"),
            "A,C" to setOf("A", "C"),
            "a c" to setOf("A", "C"),
            "A、C" to setOf("A", "C"),
            "Ａ，Ｃ" to setOf("A", "C"),
        ).forEach { (raw, labels) ->
            val answer = resolveFlashcardAnswer(
                question(type = QuestionType.MULTI, answer = raw),
            )

            assertEquals(
                raw,
                FlashcardAnswer.Resolved(highlightedLabels = labels),
                answer,
            )
        }
    }

    @Test
    fun `judge answers resolve to readable labels`() {
        listOf("√", "对", "T", "true", "正确").forEach { raw ->
            assertEquals(
                raw,
                FlashcardAnswer.Resolved(displayAnswer = "正确"),
                resolveFlashcardAnswer(question(type = QuestionType.JUDGE, answer = raw)),
            )
        }
        listOf("×", "错", "F", "false", "错误").forEach { raw ->
            assertEquals(
                raw,
                FlashcardAnswer.Resolved(displayAnswer = "错误"),
                resolveFlashcardAnswer(question(type = QuestionType.JUDGE, answer = raw)),
            )
        }
    }

    @Test
    fun `fill answer is trimmed and kept as entered`() {
        assertEquals(
            FlashcardAnswer.Resolved(displayAnswer = "细胞膜"),
            resolveFlashcardAnswer(question(type = QuestionType.FILL, answer = "  细胞膜  ")),
        )
    }

    @Test
    fun `choice with a label missing from options is unresolvable`() {
        assertEquals(
            FlashcardAnswer.Unresolvable("E"),
            resolveFlashcardAnswer(question(type = QuestionType.SINGLE, answer = "E")),
        )
    }

    @Test
    fun `single choice with multiple labels is unresolvable`() {
        assertEquals(
            FlashcardAnswer.Unresolvable("AB"),
            resolveFlashcardAnswer(question(type = QuestionType.SINGLE, answer = "AB")),
        )
    }

    @Test
    fun `unrecognized judge value is unresolvable`() {
        assertEquals(
            FlashcardAnswer.Unresolvable("也许"),
            resolveFlashcardAnswer(question(type = QuestionType.JUDGE, answer = "也许")),
        )
    }

    @Test
    fun `judge sentences are not guessed from keywords`() {
        listOf("正确答案见教材", "这个说法错误吗").forEach { raw ->
            assertEquals(
                FlashcardAnswer.Unresolvable(raw),
                resolveFlashcardAnswer(question(type = QuestionType.JUDGE, answer = raw)),
            )
        }
    }

    @Test
    fun `empty answers are unresolvable without guessing`() {
        QuestionType.entries.forEach { type ->
            val result = resolveFlashcardAnswer(question(type = type, answer = "   "))
            assertEquals(
                type.name,
                FlashcardAnswer.Unresolvable("   "),
                result,
            )
        }
    }

    @Test
    fun `invalid choice characters are unresolvable and raw is preserved`() {
        listOf("A1", "A#", "A-B").forEach { raw ->
            assertEquals(
                raw,
                FlashcardAnswer.Unresolvable(raw),
                resolveFlashcardAnswer(question(type = QuestionType.MULTI, answer = raw)),
            )
        }
    }

    @Test
    fun `explanation is exposed only when it has content`() {
        val withExplanation = resolveFlashcardAnswer(
            question(
                type = QuestionType.SINGLE,
                answer = "A",
                explanation = "  解析内容  ",
            ),
        )
        assertEquals("解析内容", withExplanation.explanation)

        val withoutExplanation = resolveFlashcardAnswer(
            question(
                type = QuestionType.SINGLE,
                answer = "A",
                explanation = " \n ",
            ),
        )
        assertEquals(null, withoutExplanation.explanation)
        assertTrue(withoutExplanation is FlashcardAnswer.Resolved)
    }

    private fun question(
        type: QuestionType,
        answer: String,
        explanation: String = "",
    ): Question = Question(
        type = type,
        stem = "虚构题干",
        options = listOf(
            QuestionOption("A", "选项 A"),
            QuestionOption("B", "选项 B"),
            QuestionOption("C", "选项 C"),
            QuestionOption("D", "选项 D"),
        ),
        answer = answer,
        explanation = explanation,
    )
}
