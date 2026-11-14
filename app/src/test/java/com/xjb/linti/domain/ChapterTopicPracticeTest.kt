package com.xjb.linti.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterTopicPracticeTest {
    private val questions = listOf(
        q(1, 1, 11, QuestionType.SINGLE), q(2, 1, 11, QuestionType.MULTI),
        q(3, 1, 12, QuestionType.SINGLE), q(4, 1, null, QuestionType.MULTI),
        q(5, 2, null, QuestionType.SINGLE), q(6, 1, 11, QuestionType.SINGLE).copy(needsReview = true),
    )
    private fun q(id: Long, bank: Long, chapter: Long?, type: QuestionType) = Question(
        id = id, bankId = bank, chapterId = chapter, type = type, stem = "虚构题 $id",
        options = listOf(QuestionOption("A", "甲"), QuestionOption("B", "乙")),
        answer = if (type == QuestionType.MULTI) "AB" else "A",
    )
    @Test fun chapterAndTypeFiltersAndCounts() {
        assertEquals(listOf(2L), TopicPractice.eligibleQuestions(questions, setOf(1), setOf(QuestionType.MULTI), setOf(11)).map { it.id })
        assertEquals(1, TopicPractice.countsByType(questions, setOf(1), setOf(11)).getValue(QuestionType.SINGLE))
        assertEquals(1, TopicPractice.countsByType(questions, setOf(1), setOf(11)).getValue(QuestionType.MULTI))
        assertEquals(listOf(2L, 4L), TopicPractice.eligibleQuestions(questions, setOf(1), setOf(QuestionType.MULTI), setOf(11, null)).map { it.id })
    }
    @Test fun uncategorizedAndEmptySelections() {
        assertEquals(listOf(4L), TopicPractice.eligibleQuestions(questions, setOf(1), QuestionType.entries.toSet(), setOf(null)).map { it.id })
        assertEquals(0, TopicPractice.eligibleQuestions(questions, setOf(1), QuestionType.entries.toSet(), emptySet()).size)
    }
    @Test fun multipleBanksIgnoreChapterFilter() {
        assertEquals(listOf(1L, 3L, 5L), TopicPractice.eligibleQuestions(questions, setOf(1, 2), setOf(QuestionType.SINGLE), emptySet()).map { it.id })
    }
}
