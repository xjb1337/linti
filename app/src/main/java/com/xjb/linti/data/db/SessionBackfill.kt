package com.xjb.linti.data.db

import com.xjb.linti.domain.Grader
import com.xjb.linti.domain.MultiSelectRule
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizQuestion

data class LegacySessionRow(
    val id: Long,
    val mode: String,
    val startedAt: Long,
    val endedAt: Long?,
    val questionIdsJson: String,
    val answersJson: String,
)

data class LegacyQuestionRow(
    val id: Long,
    val bankId: Long,
    val type: String,
    val answer: String,
    val optionsJson: String,
)

data class AttemptSeed(
    val questionId: Long,
    val bankId: Long,
    val mode: String,
    val answer: String,
    val isCorrect: Boolean,
    val answeredAt: Long,
    val sessionId: Long? = null,
    val id: Long = 0,
    val answerViewed: Boolean = false,
)

data class LegacyProgressRow(
    val bankId: Long,
    val mode: String,
    val roundStartedAt: Long,
)

data class LegacyBackfillData(
    val sessions: List<LegacySessionRow>,
    val questions: List<LegacyQuestionRow>,
    val progress: List<LegacyProgressRow>,
)

object SessionBackfill {
    const val SEQUENCE = "SEQUENCE"
    const val EXAM = "EXAM"
    const val COMPLETED = "COMPLETED"

    fun buildAttempts(
        sessions: List<LegacySessionRow>,
        questions: Map<Long, LegacyQuestionRow>,
    ): List<AttemptSeed> {
        val result = mutableListOf<AttemptSeed>()
        sessions.forEach { session ->
            if (session.mode == EXAM) return@forEach
            val answers = JsonCodec.decodeAnswers(session.answersJson)
            val ids = (JsonCodec.decodeLongList(session.questionIdsJson) + answers.keys).distinct()
            ids.forEach questionLoop@{ questionId ->
                val answer = answers[questionId] ?: return@questionLoop
                val question = questions[questionId] ?: return@questionLoop
                val quizQuestion = QuizQuestion(
                    questionId = question.id,
                    bankId = question.bankId,
                    sourceNumber = "",
                    type = questionTypeOf(question.type),
                    stem = "",
                    options = JsonCodec.decodeOptions(question.optionsJson),
                    correctAnswer = question.answer,
                    explanation = "",
                    points = 1.0,
                )
                val grade = Grader.gradeQuestion(
                    question = quizQuestion,
                    answer = answer,
                    multiSelectRule = MultiSelectRule.LESS_NO_SCORE,
                )
                result += AttemptSeed(
                    questionId = questionId,
                    bankId = question.bankId,
                    mode = session.mode,
                    answer = answer,
                    isCorrect = grade.outcome == QuestionOutcome.CORRECT,
                    answeredAt = session.endedAt ?: session.startedAt,
                    sessionId = session.id,
                )
            }
        }
        return result
    }

    fun recomputeSequenceCounts(
        progressRows: List<LegacyProgressRow>,
        attempts: List<AttemptSeed>,
        existingQuestionIds: Set<Long>,
    ): List<Pair<Long, Int>> = progressRows
        .filter { it.mode == SEQUENCE }
        .map { row ->
            row.bankId to attempts
                .asSequence()
                .filter { it.bankId == row.bankId }
                .filter { it.mode == SEQUENCE }
                .filter { it.answeredAt >= row.roundStartedAt }
                .filter { it.questionId in existingQuestionIds }
                .map { it.questionId }
                .distinct()
                .count()
        }

    fun backfill(data: LegacyBackfillData): Pair<List<AttemptSeed>, List<Pair<Long, Int>>> {
        val questionMap = data.questions.associateBy { it.id }
        val seeds = buildAttempts(data.sessions, questionMap)
        val counts = recomputeSequenceCounts(data.progress, seeds, questionMap.keys)
        return seeds to counts
    }

    fun latestPerQuestion(attempts: List<AttemptSeed>): Map<Long, AttemptSeed> {
        val result = LinkedHashMap<Long, AttemptSeed>()
        attempts
            .sortedWith(
                compareByDescending<AttemptSeed> { it.answeredAt }
                    .thenByDescending { it.id },
            )
            .forEach { seed -> result.putIfAbsent(seed.questionId, seed) }
        return result
    }

    private fun questionTypeOf(value: String): QuestionType =
        QuestionType.entries.firstOrNull { it.name == value } ?: QuestionType.SINGLE
}

fun AttemptSeed.toEntity(): QuestionAttemptEntity = QuestionAttemptEntity(
    questionId = questionId,
    bankId = bankId,
    mode = mode,
    answer = answer,
    isCorrect = isCorrect,
    answeredAt = answeredAt,
    sessionId = sessionId,
    answerViewed = answerViewed,
)

fun QuestionAttemptEntity.toSeed(): AttemptSeed = AttemptSeed(
    questionId = questionId,
    bankId = bankId,
    mode = mode,
    answer = answer,
    isCorrect = isCorrect,
    answeredAt = answeredAt,
    sessionId = sessionId,
    id = id,
    answerViewed = answerViewed,
)
