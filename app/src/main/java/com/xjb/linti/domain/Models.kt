package com.xjb.linti.domain

enum class QuestionType(val label: String) {
    SINGLE("单选"),
    MULTI("多选"),
    JUDGE("判断"),
    FILL("填空"),
}

data class QuestionOption(
    val label: String,
    val text: String,
)

data class Question(
    val id: Long = 0,
    val bankId: Long = 0,
    val sourceNumber: String = "",
    val type: QuestionType = QuestionType.SINGLE,
    val stem: String = "",
    val options: List<QuestionOption> = emptyList(),
    val answer: String = "",
    val explanation: String = "",
    val tags: String? = null,
    val knowledgePoint: String? = null,
    val confidence: Double = 1.0,
    val needsReview: Boolean = false,
    val reviewReasons: List<String> = emptyList(),
    val sourceFileName: String = "",
    val rawSnippet: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)

data class DraftQuestion(
    val sourceNumber: String,
    val type: QuestionType,
    // 文件里显式标注的题型（如「多选题」字样）；null 表示由解析推断。
    // 分文件导入用严格校验外部答案时依赖它区分「显式题型」与「推断题型」。
    val explicitType: QuestionType? = null,
    val stem: String,
    val options: List<QuestionOption> = emptyList(),
    val answer: String = "",
    val explanation: String = "",
    val tags: String? = null,
    val knowledgePoint: String? = null,
    val confidence: Double = 1.0,
    val needsReview: Boolean = false,
    val reviewReasons: List<String> = emptyList(),
    val sourceFileName: String = "",
    val rawSnippet: String? = null,
)

data class ParseResult(
    val questions: List<DraftQuestion>,
    val warnings: List<String> = emptyList(),
)

data class QuestionBank(
    val id: Long = 0,
    val name: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val note: String? = null,
)

data class BankSummary(
    val id: Long,
    val name: String,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val questionCount: Int,
    val wrongCount: Int,
    val pendingWrongCount: Int,
    val correctedWrongCount: Int,
    val favoriteCount: Int,
    val noteCount: Int,
    val sequenceIndex: Int,
    val sequenceAnsweredCount: Int,
    val lastOpenedAt: Long?,
)

enum class PracticeMode(val label: String) {
    SEQUENCE("顺序练习"),
    EXAM("模拟考试"),
    WRONG("错题重练"),
    FAVORITE("收藏练习"),
    TOPIC("专项练习"),
    FLASHCARD("试题闪卡"),
    RECITE("背题模式"),
}

enum class ImportMethod(val label: String, val storageValue: String) {
    LOCAL_ONLY("本地识别", "LOCAL"),
    AI_ONLY("AI 识别", "AI"),
    LOCAL_THEN_AI("本地识别 + AI 校对", "LOCAL_THEN_AI");

    companion object {
        fun fromStorage(value: String?): ImportMethod =
            entries.firstOrNull { it.storageValue == value } ?: LOCAL_ONLY
    }
}

enum class ImportStatus(val label: String) {
    REVIEW("待校对"),
    CONFIRMED("已入库"),
    FAILED("失败"),
}

data class ImportJob(
    val id: Long = 0,
    val fileName: String,
    val mimeType: String?,
    val kind: String,
    val method: ImportMethod,
    val status: ImportStatus,
    val recognizedCount: Int,
    val confirmedCount: Int,
    val pendingCount: Int,
    val failedCount: Int,
    val createdAt: Long,
    val targetBankId: Long? = null,
    val warnings: List<String> = emptyList(),
)

enum class MultiSelectRule(val label: String) {
    LESS_NO_SCORE("少选不得分"),
    LESS_PARTIAL("少选得部分分"),
}

data class ExamConfig(
    val singleCount: Int = 0,
    val multiCount: Int = 0,
    val judgeCount: Int = 0,
    val fillCount: Int = 0,
    val singlePoints: Double = 2.0,
    val multiPoints: Double = 2.0,
    val judgePoints: Double = 2.0,
    val fillPoints: Double = 2.0,
    val durationMinutes: Int = 60,
    val passScore: Double = 60.0,
    val shuffleOptions: Boolean = false,
    val wrongFirst: Boolean = false,
    val unansweredFirst: Boolean = false,
    val multiSelectRule: MultiSelectRule = MultiSelectRule.LESS_NO_SCORE,
    val showAnswerAfterAnswer: Boolean = false,
) {
    val totalQuestions: Int
        get() = singleCount + multiCount + judgeCount + fillCount

    val totalPoints: Double
        get() = singleCount * singlePoints +
            multiCount * multiPoints +
            judgeCount * judgePoints +
            fillCount * fillPoints

    fun countFor(type: QuestionType): Int = when (type) {
        QuestionType.SINGLE -> singleCount
        QuestionType.MULTI -> multiCount
        QuestionType.JUDGE -> judgeCount
        QuestionType.FILL -> fillCount
    }

    fun pointsFor(type: QuestionType): Double = when (type) {
        QuestionType.SINGLE -> singlePoints
        QuestionType.MULTI -> multiPoints
        QuestionType.JUDGE -> judgePoints
        QuestionType.FILL -> fillPoints
    }

    fun withCount(type: QuestionType, count: Int): ExamConfig = when (type) {
        QuestionType.SINGLE -> copy(singleCount = count.coerceAtLeast(0))
        QuestionType.MULTI -> copy(multiCount = count.coerceAtLeast(0))
        QuestionType.JUDGE -> copy(judgeCount = count.coerceAtLeast(0))
        QuestionType.FILL -> copy(fillCount = count.coerceAtLeast(0))
    }

    fun withPoints(type: QuestionType, points: Double): ExamConfig = when (type) {
        QuestionType.SINGLE -> copy(singlePoints = points.coerceAtLeast(0.0))
        QuestionType.MULTI -> copy(multiPoints = points.coerceAtLeast(0.0))
        QuestionType.JUDGE -> copy(judgePoints = points.coerceAtLeast(0.0))
        QuestionType.FILL -> copy(fillPoints = points.coerceAtLeast(0.0))
    }
}

data class QuizQuestion(
    val questionId: Long,
    val bankId: Long,
    val sourceNumber: String,
    val type: QuestionType,
    val stem: String,
    val options: List<QuestionOption>,
    val correctAnswer: String,
    val explanation: String,
    val tags: String? = null,
    val points: Double = 1.0,
    val labelMap: Map<String, String> = emptyMap(),
)

data class ExamBuildResult(
    val questions: List<QuizQuestion> = emptyList(),
    val warnings: List<String> = emptyList(),
    val error: String? = null,
)

enum class QuestionOutcome {
    CORRECT,
    WRONG,
    UNANSWERED,
}

data class QuestionGrade(
    val questionId: Long,
    val outcome: QuestionOutcome,
    val score: Double,
    val maxScore: Double,
    val userAnswer: String?,
    val correctAnswer: String,
)

data class TypeStats(
    val type: QuestionType,
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val unanswered: Int,
    val score: Double,
    val maxScore: Double,
) {
    val accuracy: Double
        get() = if (total == 0) 0.0 else correct.toDouble() / total
}

data class GradingResult(
    val totalScore: Double,
    val maxScore: Double,
    val passed: Boolean,
    val correctCount: Int,
    val wrongCount: Int,
    val unansweredCount: Int,
    val typeStats: List<TypeStats>,
    val outcomes: Map<Long, QuestionGrade>,
    val answeredCount: Int,
) {
    val accuracy: Double
        get() {
            val total = correctCount + wrongCount + unansweredCount
            return if (total == 0) 0.0 else correctCount.toDouble() / total
        }
}

data class WrongItemState(
    val questionId: Long,
    val bankId: Long,
    val wrongCount: Int,
    val lastWrongAt: Long,
    val corrected: Boolean,
    val pendingReview: Boolean,
)

data class WrongQuestionItem(
    val wrongItem: WrongItemState,
    val bankName: String,
    val sourceNumber: String,
    val type: QuestionType,
    val stem: String,
)

data class FavoriteQuestionItem(
    val questionId: Long,
    val bankId: Long,
    val bankName: String,
    val type: QuestionType,
    val stem: String,
    val answer: String,
    val createdAt: Long,
)

data class NoteQuestionItem(
    val questionId: Long,
    val bankId: Long,
    val bankName: String,
    val type: QuestionType,
    val stem: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
)
