package com.xjb.linti.domain

import kotlin.random.Random
import java.util.Locale

class ExamBuilder {
    fun build(
        config: ExamConfig,
        questions: List<Question>,
        wrongQuestionIds: Set<Long> = emptySet(),
        answeredQuestionIds: Set<Long> = emptySet(),
        random: Random = Random.Default,
    ): ExamBuildResult {
        if (config.totalQuestions == 0) {
            return ExamBuildResult(error = "未设置任何题型数量，请先配置试卷")
        }
        if (questions.isEmpty()) {
            return ExamBuildResult(error = "题库中没有可抽取的题目")
        }

        val eligibleQuestions = questions.filter(ExamEligibility::isEligible)
        val warnings = mutableListOf<String>()
        val chosen = mutableListOf<Question>()

        QuestionType.entries.forEach { type ->
            val requested = config.countFor(type)
            if (requested <= 0) return@forEach

            val available = eligibleQuestions.filter { it.type == type }
            if (available.isEmpty()) {
                warnings += "${type.label}题不足：需要 $requested 道，当前 0 道"
                return@forEach
            }

            val shuffled = available.shuffled(random)
            val wrongFirst = if (config.wrongFirst) {
                shuffled.filter { it.id in wrongQuestionIds }
            } else {
                emptyList()
            }
            val unansweredFirst = if (config.unansweredFirst) {
                shuffled.filter {
                    it.id !in answeredQuestionIds && it.id !in wrongFirst.map(Question::id)
                }
            } else {
                emptyList()
            }
            val used = (wrongFirst + unansweredFirst).mapTo(mutableSetOf()) { it.id }
            val rest = shuffled.filter { it.id !in used }
            val ordered = wrongFirst + unansweredFirst + rest
            val takeCount = minOf(requested, ordered.size)
            if (takeCount < requested) {
                warnings += "${type.label}题不足：需要 $requested 道，实际抽取 $takeCount 道"
            }
            chosen += ordered.take(takeCount)
        }

        if (chosen.isEmpty()) {
            return ExamBuildResult(
                warnings = warnings,
                error = "题库中没有符合当前配置的题目",
            )
        }

        val quizQuestions = chosen.map { question ->
            val points = config.pointsFor(question.type)
            if (config.shuffleOptions &&
                (question.type == QuestionType.SINGLE || question.type == QuestionType.MULTI)
            ) {
                shuffleOptions(question, points, random)
            } else {
                question.toQuizQuestion(points)
            }
        }
        val actualMaxScore = quizQuestions.sumOf { it.points }
        if (quizQuestions.any { !it.points.isFinite() || it.points < 0.0 } || !actualMaxScore.isFinite() ||
            !config.passScore.isFinite() || config.passScore < 0.0
        ) {
            return ExamBuildResult(warnings = warnings, error = "试卷分值或及格分无效，请重新设置")
        }
        if (config.passScore > actualMaxScore) {
            return ExamBuildResult(
                warnings = warnings,
                error = "及格分不能超过实际试卷满分 ${actualMaxScore.toString().removeSuffix(".0")} 分",
            )
        }
        return ExamBuildResult(questions = quizQuestions, warnings = warnings)
    }

    private fun shuffleOptions(
        question: Question,
        points: Double,
        random: Random,
    ): QuizQuestion {
        if (question.options.size < 2) return question.toQuizQuestion(points)

        val correctOldLabels = AnswerNormalizer.lettersIn(question.answer)
        val shuffled = question.options.shuffled(random)
        val newOptions = shuffled.mapIndexed { index, option ->
            option.copy(label = ('A' + index).toString())
        }
        val newAnswer = shuffled.mapIndexedNotNull { index, option ->
            if (option.label.uppercase(Locale.ROOT) in correctOldLabels) ('A' + index).toString() else null
        }.sorted().joinToString("")
        val labelMap = shuffled.mapIndexed { index, option ->
            ('A' + index).toString() to option.label
        }.toMap()

        return question.toQuizQuestion(
            points = points,
            options = newOptions,
            answer = newAnswer.ifBlank { question.answer },
            labelMap = labelMap,
        )
    }

    private fun Question.toQuizQuestion(
        points: Double,
        options: List<QuestionOption> = this.options,
        answer: String = this.answer,
        labelMap: Map<String, String> = emptyMap(),
    ): QuizQuestion = QuizQuestion(
        questionId = id,
        bankId = bankId,
        sourceNumber = sourceNumber,
        type = type,
        stem = stem,
        options = options,
        correctAnswer = answer,
        explanation = explanation,
        tags = tags,
        points = points,
        origin = origin,
        labelMap = labelMap,
        acceptedAnswers = acceptedAnswers,
    )
}
