package com.xjb.linti.data.db

import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.QuestionOrigin
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizQuestion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamDraftCodecTest {
    @Test
    fun `round trip preserves reordered questions options answers and deadline across chunks`() {
        val question = QuizQuestion(
            origin = QuestionOrigin.AI_GENERATED,
            questionId = 11,
            bankId = 1,
            sourceNumber = "原题号",
            type = QuestionType.MULTI,
            stem = "😀𠀀".repeat(9_000),
            options = listOf(QuestionOption("A", "原丙"), QuestionOption("B", "原甲")),
            correctAnswer = "AB",
            explanation = "解析",
            tags = "标签",
            points = 1.5,
            labelMap = mapOf("A" to "C", "B" to "A"),
        )
        val snapshot = ExamDraftSnapshot(
            draftId = "draft-1",
            bankId = 1,
            bankName = "题库",
            config = ExamConfig(multiCount = 1, multiPoints = 1.5, durationMinutes = 5, passScore = 1.0),
            questions = listOf(question, question.copy(questionId = 12, stem = "x" + question.stem)),
            answers = mapOf(11L to "AB", 12L to ""),
            committedIds = setOf(11),
            flaggedIds = setOf(12),
            bookmarkedIds = setOf(11),
            currentIndex = 1,
            startedAt = 123456789L,
            revision = 4,
        )

        val (entity, rows) = ExamDraftCodec.encodeInitial(snapshot)
        assertTrue(rows.count { it.kind == "question" && it.ordinal == 0 } > 1)
        assertTrue(rows.any { it.kind == "question" && it.part == 0 && it.content.length == 15_999 })
        rows.forEach { row ->
            assertFalse(row.content.isNotEmpty() && Character.isHighSurrogate(row.content.last()))
            assertFalse(row.content.isNotEmpty() && Character.isLowSurrogate(row.content.first()))
        }
        assertEquals(snapshot, ExamDraftCodec.decode(entity, rows))
        assertTrue(ExamDraftCodec.encodeProgress(snapshot).all { it.kind == "progress" })
    }
    @Test fun `fill synonyms and essay settings survive exam resume`() {
        val question = QuizQuestion(1, 1, "1", QuestionType.FILL, "____", emptyList(), "甲", "解析", acceptedAnswers = listOf(listOf("甲", "同义甲")))
        val snapshot = ExamDraftSnapshot("material", 1, "虚构题库", ExamConfig(fillCount = 1, essayCount = 2, passScore = 0.0),
            listOf(question), emptyMap(), emptySet(), emptySet(), emptySet(), 0, 1, 0)
        val (entity, chunks) = ExamDraftCodec.encodeInitial(snapshot)
        assertEquals(snapshot, ExamDraftCodec.decode(entity, chunks))
    }

}
