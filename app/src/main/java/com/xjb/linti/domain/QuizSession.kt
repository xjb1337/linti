package com.xjb.linti.domain

data class AnswerState(
    val answers: Map<Long, String> = emptyMap(),
    val committedIds: Set<Long> = emptySet(),
    val answerViewedIds: Set<Long> = emptySet(),
)

data class AttemptStats(val total: Int = 0, val correct: Int = 0) {
    val accuracy: Double
        get() = if (total == 0) 0.0 else correct.toDouble() / total
}

object QuizSession {
    fun supportsRecite(mode: PracticeMode): Boolean = mode in setOf(
        PracticeMode.SEQUENCE, PracticeMode.TOPIC, PracticeMode.WRONG, PracticeMode.FAVORITE, PracticeMode.PAPER,
    )

    fun viewAnswer(state: AnswerState, questionId: Long): AnswerState =
        if (questionId in state.committedIds) state
        else state.copy(answerViewedIds = state.answerViewedIds + questionId)

    fun commitsEnabled(mode: PracticeMode, config: ExamConfig): Boolean =
        mode != PracticeMode.EXAM || config.showAnswerAfterAnswer

    fun isRevealed(
        state: AnswerState,
        questionId: Long,
        mode: PracticeMode,
        config: ExamConfig,
    ): Boolean = commitsEnabled(mode, config) && questionId in state.committedIds

    fun needsConfirm(type: QuestionType, mode: PracticeMode, config: ExamConfig): Boolean =
        commitsEnabled(mode, config) &&
            (type == QuestionType.MULTI || type == QuestionType.FILL)

    fun canCommit(
        state: AnswerState,
        questionId: Long,
        mode: PracticeMode,
        config: ExamConfig,
    ): Boolean = commitsEnabled(mode, config) &&
        questionId !in state.committedIds &&
        state.answers[questionId].orEmpty().isNotBlank()

    fun select(
        state: AnswerState,
        questionId: Long,
        type: QuestionType,
        rawAnswer: String,
        mode: PracticeMode,
        config: ExamConfig,
    ): AnswerState {
        if (isRevealed(state, questionId, mode, config)) return state
        val normalized = normalize(type, state.answers[questionId], rawAnswer)
        val answers = state.answers + (questionId to normalized)
        val autoCommit = commitsEnabled(mode, config) &&
            (type == QuestionType.SINGLE || type == QuestionType.JUDGE) &&
            normalized.isNotBlank()
        val committed = if (autoCommit) state.committedIds + questionId else state.committedIds
        return state.copy(answers = answers, committedIds = committed)
    }

    fun setText(
        state: AnswerState,
        questionId: Long,
        text: String,
        mode: PracticeMode,
        config: ExamConfig,
    ): AnswerState {
        if (isRevealed(state, questionId, mode, config)) return state
        return state.copy(answers = state.answers + (questionId to text))
    }

    fun commit(
        state: AnswerState,
        questionId: Long,
        mode: PracticeMode,
        config: ExamConfig,
    ): AnswerState? {
        if (!canCommit(state, questionId, mode, config)) return null
        return state.copy(committedIds = state.committedIds + questionId)
    }

    fun retry(state: AnswerState, questionId: Long): AnswerState = state.copy(
        answers = state.answers - questionId,
        committedIds = state.committedIds - questionId,
        answerViewedIds = if (questionId in state.committedIds) state.answerViewedIds + questionId else state.answerViewedIds,
    )

    fun mapAnswerToOriginal(answer: String, labelMap: Map<String, String>): String {
        if (labelMap.isEmpty()) return answer
        return AnswerNormalizer.lettersIn(answer)
            .map { labelMap[it] ?: it }
            .sorted()
            .joinToString("")
    }

    private fun normalize(type: QuestionType, current: String?, raw: String): String = when (type) {
        QuestionType.MULTI -> {
            val toggled = AnswerNormalizer.lettersIn(current).toMutableSet()
            AnswerNormalizer.lettersIn(raw).forEach { letter ->
                if (!toggled.add(letter)) toggled.remove(letter)
            }
            toggled.sorted().joinToString("")
        }
        QuestionType.SINGLE -> AnswerNormalizer.normalizeLetters(raw).take(1)
        QuestionType.JUDGE -> AnswerNormalizer.normalizeJudge(raw).orEmpty()
        QuestionType.FILL -> AnswerNormalizer.normalizeFillForStorage(raw)
    }
}
