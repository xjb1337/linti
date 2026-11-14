package com.xjb.linti.domain

object Grader {
    fun grade(
        questions: List<QuizQuestion>,
        answers: Map<Long, String>,
        config: ExamConfig,
    ): GradingResult {
        val grades = questions.map { question ->
            gradeQuestion(
                question = question,
                answer = answers[question.questionId],
                multiSelectRule = config.multiSelectRule,
            )
        }
        val byId = grades.associateBy { it.questionId }
        val typeStats = QuestionType.entries.mapNotNull { type ->
            val typed = grades.filter { grade ->
                questions.firstOrNull { it.questionId == grade.questionId }?.type == type
            }
            if (typed.isEmpty()) return@mapNotNull null
            TypeStats(
                type = type,
                total = typed.size,
                correct = typed.count { it.outcome == QuestionOutcome.CORRECT },
                wrong = typed.count { it.outcome == QuestionOutcome.WRONG },
                unanswered = typed.count { it.outcome == QuestionOutcome.UNANSWERED },
                score = typed.sumOf { it.score },
                maxScore = typed.sumOf { it.maxScore },
            )
        }
        val totalScore = grades.sumOf { it.score }
        return GradingResult(
            totalScore = totalScore,
            maxScore = grades.sumOf { it.maxScore },
            passed = totalScore >= config.passScore,
            correctCount = grades.count { it.outcome == QuestionOutcome.CORRECT },
            wrongCount = grades.count { it.outcome == QuestionOutcome.WRONG },
            unansweredCount = grades.count { it.outcome == QuestionOutcome.UNANSWERED },
            typeStats = typeStats,
            outcomes = byId,
            answeredCount = grades.count { it.outcome != QuestionOutcome.UNANSWERED },
        )
    }

    fun gradeQuestion(
        question: QuizQuestion,
        answer: String?,
        multiSelectRule: MultiSelectRule,
    ): QuestionGrade {
        val userAnswer = answer?.takeIf { it.isNotBlank() && (question.type != QuestionType.ESSAY || EssayAnswer.isAssessed(it)) }
        if (userAnswer == null) {
            return QuestionGrade(
                questionId = question.questionId,
                outcome = QuestionOutcome.UNANSWERED,
                score = 0.0,
                maxScore = question.points,
                userAnswer = null,
                correctAnswer = question.correctAnswer,
            )
        }

        val isCorrect = when (question.type) {
            QuestionType.SINGLE -> {
                val expected = AnswerNormalizer.normalizeLetters(question.correctAnswer)
                val actual = AnswerNormalizer.normalizeLetters(userAnswer)
                expected.isNotEmpty() && actual == expected
            }
            QuestionType.JUDGE -> AnswerNormalizer.judgeMatches(userAnswer, question.correctAnswer)
            QuestionType.FILL -> AnswerNormalizer.fillMatches(userAnswer, question.correctAnswer, question.acceptedAnswers)
            QuestionType.ESSAY -> EssayAnswer.isCorrect(userAnswer)
            QuestionType.MULTI -> false
        }

        val score = when (question.type) {
            QuestionType.MULTI -> multiScore(question, userAnswer, multiSelectRule)
            else -> if (isCorrect) question.points else 0.0
        }
        val outcome = when {
            question.type == QuestionType.MULTI && score >= question.points -> QuestionOutcome.CORRECT
            question.type == QuestionType.MULTI && score > 0.0 -> QuestionOutcome.WRONG
            isCorrect -> QuestionOutcome.CORRECT
            else -> QuestionOutcome.WRONG
        }
        return QuestionGrade(
            questionId = question.questionId,
            outcome = outcome,
            score = score,
            maxScore = question.points,
            userAnswer = userAnswer,
            correctAnswer = question.correctAnswer,
        )
    }

    private fun multiScore(
        question: QuizQuestion,
        userAnswer: String,
        rule: MultiSelectRule,
    ): Double {
        val expected = AnswerNormalizer.lettersIn(question.correctAnswer)
        val actual = AnswerNormalizer.lettersIn(userAnswer)
        if (expected.isEmpty() || actual.isEmpty()) return 0.0
        if (actual == expected) return question.points
        if (actual.any { it !in expected }) return 0.0
        return when (rule) {
            MultiSelectRule.LESS_NO_SCORE -> 0.0
            MultiSelectRule.LESS_PARTIAL ->
                question.points * actual.size.toDouble() / expected.size.toDouble()
        }
    }
}
