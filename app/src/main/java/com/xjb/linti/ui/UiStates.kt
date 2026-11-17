package com.xjb.linti.ui

import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.data.import.SplitDraftProblem
import com.xjb.linti.data.ocr.OcrFailureKind
import com.xjb.linti.data.db.ExamDraftSnapshot
import com.xjb.linti.data.update.UpdateRelease
import com.xjb.linti.domain.AnswerState
import com.xjb.linti.domain.AttemptStats
import com.xjb.linti.domain.Chapter
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.EssayAnswer
import com.xjb.linti.domain.ExamAccuracyPoint
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.ExamHistoryDetail
import com.xjb.linti.domain.ExamHistoryEntry
import com.xjb.linti.domain.ExamPaperReview
import com.xjb.linti.domain.GradingResult
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizQuestion
import com.xjb.linti.domain.QuizSession
import com.xjb.linti.domain.recentExamAccuracyPoints

/** OCR 未配置 Key 时的统一提示（不发请求，引导设置）。 */
internal const val OCR_KEY_MISSING_MESSAGE =
    "尚未配置 DeepSeek OCR Key，请先在「我的 → DeepSeek OCR」中配置后再使用在线识别"

/** 上传/识别阶段被取消后的提示：重试按钮会要求先确认可能重复计费。 */
internal const val OCR_CANCELLED_BILLING_MESSAGE =
    "已取消识别；请求可能已送达服务端并继续识别计费，重试前请确认"

/** OCR 失败后重试前需提示「可能重复计费」的类型：超时/断线（官方：客户端断开不终止识别）。 */
internal val OCR_BILLING_RISK_KINDS = setOf(
    OcrFailureKind.TIMEOUT,
    OcrFailureKind.UPLOAD_TIMEOUT,
    OcrFailureKind.NETWORK,
)

data class ImportProgressUiState(
    val isImporting: Boolean = false,
    val isBackgrounded: Boolean = false,
    val fileName: String = "",
    val progressText: String = "",
    val method: ImportMethod = ImportMethod.LOCAL_ONLY,
    val kind: DocumentKind? = null,
    val error: String? = null,
)

enum class ImportNavigationTarget {
    REVIEW,
    PDF_OCR,
}

data class ImportUiState(
    val isImporting: Boolean = false,
    val progressText: String = "",
    val jobId: Long? = null,
    val fileName: String = "",
    val method: ImportMethod = ImportMethod.LOCAL_ONLY,
    val kind: DocumentKind? = null,
    val drafts: List<DraftQuestion> = emptyList(),
    val warnings: List<String> = emptyList(),
    val failedCount: Int = 0,
    val targetBankId: Long? = null,
    val newBankName: String = "",
    val isConfirming: Boolean = false,
    val confirmedBankId: Long? = null,
    val error: String? = null,
    val isSplitFile: Boolean = false,
    val answerFileName: String = "",
    // 分文件匹配绑定到草稿的问题（索引 → 问题）；解决后由 VM 移除。
    val splitProblems: Map<Int, SplitDraftProblem> = emptyMap(),
    // OCR 导入（DeepSeek 在线识别）专属：来源、严格保存门槛与原文对照信息
    val ocrSource: OcrImportSource? = null,
    val requiresValidAnswers: Boolean = false,
    val sourcePages: Map<Int, Int> = emptyMap(),
    val originalPreviewPath: String? = null,
    val pdfPreviewPath: String? = null,
) {
    val recognizedCount: Int get() = drafts.size
    val pendingCount: Int get() = drafts.count { it.needsReview }
    val confirmedCount: Int get() = drafts.count { !it.needsReview }

    // 与分文件导入相同的保存门槛：OCR 识别结果存在待核对或缺答案草稿时不允许入库
    val saveBlocked: Boolean
        get() = (isSplitFile || requiresValidAnswers) &&
            drafts.any { it.needsReview || it.answer.isBlank() }
}

data class SplitSlotUiState(
    val uri: String? = null,
    val fileName: String = "",
    val error: String? = null,
    // 选择请求的代际号：查询文件名的 IO 完成时仍与本槽位 token 一致才落盘，防乱序
    val selectionToken: Long = 0,
) {
    val isSelected: Boolean get() = uri != null
}

data class SplitImportUiState(
    val question: SplitSlotUiState = SplitSlotUiState(),
    val answer: SplitSlotUiState = SplitSlotUiState(),
    val isMatching: Boolean = false,
    val matchError: String? = null,
) {
    val canMatch: Boolean get() = question.isSelected && answer.isSelected && !isMatching
}

enum class OcrPhase(val label: String) {
    PREPARING("正在准备"),
    RUNNING("正在上传并识别"),
    FAILED("识别失败"),
}

enum class OcrImportSource(val label: String) {
    IMAGE("DeepSeek 图片识别"),
    PDF("DeepSeek 在线识别"),
}

data class ImageOcrUiState(
    val sourcePath: String? = null,
    val fileName: String = "",
    val kind: DocumentKind? = null,
    val importMethod: ImportMethod = ImportMethod.LOCAL_ONLY,
    val phase: OcrPhase? = null,
    val statusText: String = "",
    val error: String? = null,
    // 上次失败是否属于超时/断线：重试前需确认可能重复计费
    val pendingBillingConfirm: Boolean = false,
) {
    val isRunning: Boolean get() = phase == OcrPhase.PREPARING || phase == OcrPhase.RUNNING
    val hasSource: Boolean get() = sourcePath != null
}

data class PdfOcrUiState(
    val sourcePath: String? = null,
    val fileName: String = "",
    val pageCount: Int = 0,
    val usablePageCount: Int = 0,
    val scanPages: List<Int> = emptyList(),
    // 本地解析失败（损坏）时的提示；此时仅提供「整份在线识别」尝试入口（页数未知）
    val loadErrorMessage: String? = null,
    val importMethod: ImportMethod = ImportMethod.LOCAL_ONLY,
    val phase: OcrPhase? = null,
    val statusText: String = "",
    val error: String? = null,
    val pendingBillingConfirm: Boolean = false,
) {
    val isRunning: Boolean get() = phase == OcrPhase.PREPARING || phase == OcrPhase.RUNNING
    val hasEntry: Boolean get() = scanPages.isNotEmpty() || loadErrorMessage != null
}

/** PDF 导入分流结果：全文字层直接走本地；否则引导到扫描页确认页。 */
sealed interface PdfImportDecision {
    data object ProceedLocal : PdfImportDecision
    data object NeedsDecision : PdfImportDecision
    data class Failed(val message: String) : PdfImportDecision
}

sealed interface MaterialFileResult {
    data class Text(val text: String, val fileName: String = "") : MaterialFileResult
    data class NeedsOcr(val scanPages: Int, val totalPages: Int) : MaterialFileResult
    data class Failed(val message: String, val needsSettings: Boolean = false) : MaterialFileResult
}

data class QuizUiState(
    val active: Boolean = false,
    val sessionToken: Long = 0,
    val bankId: Long? = null,
    val bankName: String = "",
    val mode: PracticeMode = PracticeMode.SEQUENCE,
    val questions: List<QuizQuestion> = emptyList(),
    val config: ExamConfig = ExamConfig(),
    val answers: Map<Long, String> = emptyMap(),
    val flaggedIds: Set<Long> = emptySet(),
    val bookmarkedIds: Set<Long> = emptySet(),
    val committedIds: Set<Long> = emptySet(),
    val answerViewedIds: Set<Long> = emptySet(),
    val essayRevealedIds: Set<Long> = emptySet(),
    val reciting: Boolean = false,
    val attemptStats: Map<Long, AttemptStats> = emptyMap(),
    val autoSubmitted: Boolean = false,
    val currentIndex: Int = 0,
    val startedAt: Long = 0,
    val roundStartedAt: Long = 0,
    val examDraftId: String? = null,
    val paperId: Long? = null,
) {
    val currentQuestion: QuizQuestion?
        get() = questions.getOrNull(currentIndex)

    val answeredCount: Int
        get() = answers.count { (questionId, value) ->
            val question = questions.firstOrNull { it.questionId == questionId }
            value.isNotBlank() && question != null &&
                (question.type != QuestionType.ESSAY || EssayAnswer.isAssessed(value))
        }

    val currentFlagged: Boolean
        get() = currentQuestion?.questionId in flaggedIds

    val currentBookmarked: Boolean
        get() = currentQuestion?.questionId in bookmarkedIds

    val canSwitchRecite: Boolean
        get() = active && QuizSession.supportsRecite(mode)

    fun revealed(questionId: Long): Boolean =
        QuizSession.isRevealed(AnswerState(committedIds = committedIds), questionId, mode, config) ||
            (canSwitchRecite && reciting && currentQuestion?.questionId == questionId &&
                questionId in answerViewedIds)
}

internal data class PendingExamDraftWrite(val token: Long, val snapshot: ExamDraftSnapshot)

data class QuizStartResult(
    val started: Boolean,
    val message: String? = null,
    val warnings: List<String> = emptyList(),
)

data class ExamSetupData(
    val available: Map<QuestionType, Int>,
    val config: ExamConfig,
)

data class TopicSetupData(
    val banks: List<QuestionBank>,
    val questions: List<Question>,
    val chapters: List<Chapter> = emptyList(),
)

data class ResultUiState(
    val sessionId: Long,
    val grading: GradingResult,
    val bankId: Long?,
    val bankName: String,
    val mode: PracticeMode,
    val durationSeconds: Long,
    val config: ExamConfig,
    val questions: List<QuizQuestion>,
    val answers: Map<Long, String>,
    val autoSubmitted: Boolean = false,
)

data class ExamHistoryState(
    val loading: Boolean = false,
    val failed: Boolean = false,
    val entries: List<ExamHistoryEntry> = emptyList(),
) {
    // 「最近 10 次模考正确率」直接从列表已查询的会话数据派生，不逐点读取详情
    val accuracyPoints: List<ExamAccuracyPoint>
        get() = entries.recentExamAccuracyPoints()
}

data class ExamHistoryDetailState(
    val sessionId: Long = 0,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val detail: ExamHistoryDetail? = null,
)

data class ExamPaperReviewState(
    val sessionId: Long = 0,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val review: ExamPaperReview? = null,
)

data class QuestionCollectionState(
    val bankId: Long?,
    val title: String,
    val questions: List<Question> = emptyList(),
    val loading: Boolean = false,
)

data class StudySummary(
    val totalAnswered: Int,
    val todayAnswered: Int,
    val accuracy: Double,
    val pendingWrong: Int,
    val totalQuestions: Int,
    val sessionCount: Int,
    val streakDays: Int,
)

data class AiExplanationRequestState(
    val loading: Boolean = false,
    val error: AiExplanationError? = null,
)

data class UpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val release: UpdateRelease? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val dialogVisible: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val permissionRequired: Boolean = false,
)
