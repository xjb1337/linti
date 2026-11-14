package com.xjb.linti.ui.nav

object Routes {
    const val Home = "home"
    const val Banks = "banks"
    const val Practice = "practice"
    const val Settings = "settings"

    const val Review = "review"
    const val SplitImport = "split_import"
    const val ImageOcr = "image_ocr"
    const val PdfOcr = "pdf_ocr"
    const val Quiz = "quiz"
    const val AnswerSheet = "answer_sheet"
    const val Result = "result"
    const val ExamHistory = "exam_history"

    const val AiQuestionsPattern = "ai_questions/{bankId}"
    fun aiQuestions(bankId: Long) = "ai_questions/$bankId"

    const val PastPapersPattern = "past_papers/{bankId}"
    const val ChaptersPattern = "chapters/{bankId}"
    const val BankPattern = "bank/{bankId}"
    const val WrongBookPattern = "wrong_book/{bankId}"
    const val ExamSetupPattern = "exam_setup/{bankId}"
    const val TopicSetupPattern = "topic_setup/{bankId}"
    const val FlashcardsPattern = "flashcards/{bankId}"
    const val RecitePattern = "recite/{bankId}"
    const val FavoritesPattern = "favorites/{bankId}"
    const val NotesPattern = "notes/{bankId}"
    const val SearchPattern = "search/{bankId}"
    const val QuestionEditPattern = "question_edit/{questionId}"
    const val ExamHistoryDetailPattern = "exam_history/{sessionId}"
    const val ExamPaperReviewPattern = "exam_paper/{sessionId}?filter={filter}"

    fun pastPapers(bankId: Long) = "past_papers/$bankId"
    fun chapters(bankId: Long) = "chapters/$bankId"
    fun bank(bankId: Long) = "bank/$bankId"
    fun wrongBook(bankId: Long?) = "wrong_book/${bankId ?: -1L}"
    fun examSetup(bankId: Long) = "exam_setup/$bankId"
    fun topicSetup(bankId: Long?) = "topic_setup/${bankId ?: -1L}"
    fun examHistoryDetail(sessionId: Long) = "exam_history/$sessionId"
    fun examPaperReview(sessionId: Long, wrongOnly: Boolean = false) =
        "exam_paper/$sessionId?filter=" + if (wrongOnly) "wrong" else "all"
    fun flashcards(bankId: Long?) = "flashcards/${bankId ?: -1L}"
    fun recite(bankId: Long?) = "recite/${bankId ?: -1L}"
    fun favorites(bankId: Long?) = "favorites/${bankId ?: -1L}"
    fun notes(bankId: Long?) = "notes/${bankId ?: -1L}"
    fun search(bankId: Long?) = "search/${bankId ?: -1L}"
    fun questionEdit(questionId: Long) = "question_edit/$questionId"

    val topLevel = setOf(Home, Banks, Practice, Settings)
}
