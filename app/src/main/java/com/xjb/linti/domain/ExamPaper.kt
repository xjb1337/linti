package com.xjb.linti.domain

/**
 * 已交卷模考的原卷逐题快照。所有字段取自交卷那一刻：选项字母与顺序、
 * 作答与正确答案都是考场上的字母（乱序开启时可能与题库不同），
 * 判分沿用既有结果，回看时不读实时题库、不重新判分。
 */
data class ExamPaperQuestion(
    val index: Int,
    val questionId: Long,
    val sourceNumber: String,
    val type: QuestionType,
    val stem: String,
    val options: List<QuestionOption>,
    val userAnswer: String?,
    val correctAnswer: String,
    val explanation: String?,
    val points: Double,
    val outcome: QuestionOutcome,
    val score: Double,
    val maxScore: Double,
    val origin: QuestionOrigin = QuestionOrigin.IMPORTED,
)

/** 比较回看快照与题库内容，允许模考调整选项顺序和字母。 */
fun ExamPaperQuestion.matchesCurrentQuestion(current: Question): Boolean {
    if (type != current.type || stem != current.stem || explanation.orEmpty() != current.explanation) return false
    if (options.map { it.text }.sorted() != current.options.map { it.text }.sorted()) return false
    if (type == QuestionType.SINGLE || type == QuestionType.MULTI) {
        fun selected(options: List<QuestionOption>, answer: String): List<String> {
            val labels = AnswerNormalizer.lettersIn(answer)
            return options.filter { it.label.uppercase() in labels }.map { it.text }.sorted()
        }
        val snapshotSelected = selected(options, correctAnswer)
        val currentSelected = selected(current.options, current.answer)
        return if (snapshotSelected.isEmpty() || currentSelected.isEmpty()) {
            snapshotSelected == currentSelected && correctAnswer == current.answer
        } else snapshotSelected == currentSelected
    }
    return AnswerNormalizer.normalizeForStorage(type, correctAnswer) == AnswerNormalizer.normalizeForStorage(type, current.answer)
}

/**
 * 回看页数据：会话维度信息（题库名、交卷时间）取自 quiz_sessions，
 * 逐题信息取自原卷快照；不使用 lastResult 作为数据源。
 */
data class ExamPaperReview(
    val sessionId: Long,
    val bankName: String,
    val submittedAt: Long?,
    val questions: List<ExamPaperQuestion>,
) {
    // 「本场错题」= 当场判为 WRONG 的题：少选拿到部分分但判错的题也在内，
    // 保留实际得分；UNANSWERED 只出现在「全部试题」。
    val wrongQuestions: List<ExamPaperQuestion>
        get() = questions.filter { it.outcome == QuestionOutcome.WRONG }
}
