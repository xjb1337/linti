package com.xjb.linti.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xjb.linti.BuildConfig
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.import.ConfirmImportResult
import com.xjb.linti.data.import.ConfirmTarget
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.data.import.ImportRun
import com.xjb.linti.data.import.MergedPdfPage
import com.xjb.linti.data.import.OcrDraftPostProcessor
import com.xjb.linti.data.import.PdfOcrMergeMode
import com.xjb.linti.data.import.PdfOcrMergeResult
import com.xjb.linti.data.import.PdfOcrMerger
import com.xjb.linti.data.import.PdfOcrPlan
import com.xjb.linti.data.import.PdfOcrPlanner
import com.xjb.linti.data.import.PdfPageInspection
import com.xjb.linti.data.import.PdfPageInspector
import com.xjb.linti.data.import.SplitDraftProblem
import com.xjb.linti.data.import.SplitDraftResolver
import com.xjb.linti.data.import.SplitFileMatcher
import com.xjb.linti.data.import.SplitImportOutcome
import com.xjb.linti.data.import.reason
import com.xjb.linti.data.ocr.CropRect
import com.xjb.linti.data.ocr.ImageOcrBitmaps
import com.xjb.linti.data.ocr.OcrException
import com.xjb.linti.data.ocr.OcrFailureKind
import com.xjb.linti.data.ocr.OcrKeyResolver
import com.xjb.linti.data.ocr.OcrKeySource
import com.xjb.linti.data.ocr.OcrLimits
import com.xjb.linti.data.ocr.DeepSeekOcrClient
import com.xjb.linti.data.ocr.OcrUploadNaming
import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.data.prefs.AiExplanationReasoningEffort
import com.xjb.linti.data.prefs.SettingsSnapshot
import com.xjb.linti.data.explanation.AiExplanationError
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.data.explanation.AiExplanationRepository
import com.xjb.linti.data.db.SessionBackfill
import com.xjb.linti.data.db.ExamDraftSnapshot
import com.xjb.linti.data.repo.ExamDraftSummary
import com.xjb.linti.data.repo.ExamRecord
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.PracticeCommit
import com.xjb.linti.data.repo.SessionSnapshot
import com.xjb.linti.data.update.UpdateCheck
import com.xjb.linti.data.update.UpdateException
import com.xjb.linti.data.update.UpdateFailure
import com.xjb.linti.data.update.UpdateProtocol
import com.xjb.linti.data.update.UpdateRelease
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.AnswerState
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.AttemptStats
import com.xjb.linti.domain.BankSummary
import com.xjb.linti.domain.ColorTheme
import com.xjb.linti.domain.CountdownState
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ExamBuilder
import com.xjb.linti.domain.ExamBuildResult
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.ExamEligibility
import com.xjb.linti.domain.ExamAccuracyPoint
import com.xjb.linti.domain.ExamHistoryDetail
import com.xjb.linti.domain.ExamPaperReview
import com.xjb.linti.domain.ExamHistoryEntry
import com.xjb.linti.domain.recentExamAccuracyPoints
import com.xjb.linti.domain.ExamNavigationEvent
import com.xjb.linti.domain.ExamSubmissionCoordinator
import com.xjb.linti.domain.FavoriteQuestionItem
import com.xjb.linti.domain.FlashcardSchedule
import com.xjb.linti.domain.FontScale
import com.xjb.linti.domain.Grader
import com.xjb.linti.domain.GradingResult
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.NoteQuestionItem
import com.xjb.linti.domain.PastPaperSource
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.Chapter
import com.xjb.linti.domain.QuestionBank
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizClock
import com.xjb.linti.domain.QuizCountdown
import com.xjb.linti.domain.QuizQuestion
import com.xjb.linti.domain.QuizSession
import com.xjb.linti.domain.SystemQuizClock
import com.xjb.linti.domain.TopicDrawResult
import com.xjb.linti.domain.TopicPractice
import com.xjb.linti.domain.WrongQuestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

/** OCR 未配置 Key 时的统一提示（不发请求，引导设置）。 */
private const val OCR_KEY_MISSING_MESSAGE =
    "尚未配置 DeepSeek OCR Key，请先在「我的 → DeepSeek OCR」中配置后再使用在线识别"

/** 上传/识别阶段被取消后的提示：重试按钮会要求先确认可能重复计费。 */
private const val OCR_CANCELLED_BILLING_MESSAGE =
    "已取消识别；请求可能已送达服务端并继续识别计费，重试前请确认"

data class ImportUiState(
    val isImporting: Boolean = false,
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

// ===== DeepSeek 在线 OCR =====

enum class OcrPhase(val label: String) {
    PREPARING("正在准备"),
    RUNNING("正在上传并识别"),
    FAILED("识别失败"),
}

enum class OcrImportSource(val label: String) {
    IMAGE("DeepSeek 图片识别"),
    PDF("DeepSeek 在线识别"),
}

/** OCR 失败后重试前需提示「可能重复计费」的类型：超时/断线（官方：客户端断开不终止识别）。 */
private val OCR_BILLING_RISK_KINDS = setOf(
    com.xjb.linti.data.ocr.OcrFailureKind.TIMEOUT,
    com.xjb.linti.data.ocr.OcrFailureKind.UPLOAD_TIMEOUT,
    com.xjb.linti.data.ocr.OcrFailureKind.NETWORK,
)

data class ImageOcrUiState(
    val sourcePath: String? = null,
    val fileName: String = "",
    val kind: DocumentKind? = null,
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
    val reciting: Boolean = false,
    val attemptStats: Map<Long, AttemptStats> = emptyMap(),
    val autoSubmitted: Boolean = false,
    val currentIndex: Int = 0,
    val startedAt: Long = 0,
    val roundStartedAt: Long = 0,
    val examDraftId: String? = null,
) {
    val currentQuestion: QuizQuestion?
        get() = questions.getOrNull(currentIndex)

    val answeredCount: Int
        get() = answers.count { (questionId, value) ->
            value.isNotBlank() && questions.any { it.questionId == questionId }
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

private data class PendingExamDraftWrite(val token: Long, val snapshot: ExamDraftSnapshot)

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

class LintiViewModel(
    application: Application,
    private val container: AppContainer,
    private val clock: QuizClock = SystemQuizClock,
    private val ocrClientFactory: () -> DeepSeekOcrClient? = container::createOcrClient,
    private val ocrKeySourceOverride: OcrKeySource? = null,
    private val flashcardZone: ZoneId = ZoneId.systemDefault(),
    private val aiExplanationRepository: AiExplanationRepository = container.aiExplanationRepository,
) : AndroidViewModel(application) {
    val aiQuestionRepository = container.aiQuestionRepository
    private val repository = container.repository
    private val _aiExplanationRequests = MutableStateFlow<Map<Long, AiExplanationRequestState>>(emptyMap())
    val aiExplanationRequests = _aiExplanationRequests.asStateFlow()

    fun observeAiExplanation(questionId: Long) = aiExplanationRepository.observe(questionId)

    fun requestAiExplanation(questionId: Long, onOpenSettings: () -> Unit) {
        if (_aiExplanationRequests.value[questionId]?.loading == true) return
        val configurationError = aiExplanationRepository.configurationError()
        if (configurationError != null) {
            _aiExplanationRequests.value = _aiExplanationRequests.value + (questionId to AiExplanationRequestState(error = configurationError))
            onOpenSettings()
            return
        }
        _aiExplanationRequests.value = _aiExplanationRequests.value + (questionId to AiExplanationRequestState(loading = true))
        viewModelScope.launch {
            val result = try {
                aiExplanationRepository.generate(questionId)
                AiExplanationRequestState()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: AiExplanationException) {
                AiExplanationRequestState(error = e.reason)
            } catch (_: Exception) {
                AiExplanationRequestState(error = AiExplanationError.NETWORK)
            }
            _aiExplanationRequests.value = _aiExplanationRequests.value + (questionId to result)
            if (result.error?.needsSettings == true) onOpenSettings()
        }
    }

    fun deleteAiExplanation(questionId: Long) {
        if (_aiExplanationRequests.value[questionId]?.loading == true) return
        viewModelScope.launch {
            aiExplanationRepository.delete(questionId)
            _aiExplanationRequests.value = _aiExplanationRequests.value - questionId
        }
    }

    suspend fun loadQuestionForExplanation(questionId: Long) = repository.getQuestion(questionId)
    fun observeQuestionForExplanation(questionId: Long) = repository.observeQuestion(questionId)
    private val examBuilder = ExamBuilder()
    private val examSubmissions = ExamSubmissionCoordinator(viewModelScope)
    private val updateInstaller = container.updateInstaller
    private val examNavigation = ExamNavigationEvent()
    private var examCountdownJob: Job? = null
    private var updateCheckJob: Job? = null
    private var updateDownloadJob: Job? = null
    private var verifiedUpdateFile: File? = null
    private var awaitingInstallPermission = false
    private var examDraftRevision = 0L
    private var leavingExamToken: Long? = null
    private val examDraftWrites = Channel<PendingExamDraftWrite>(Channel.CONFLATED)
    private var sessionTokenCounter = 0L
    private val notifiedCommitted = mutableSetOf<Long>()

    val banks: StateFlow<List<BankSummary>> = repository.bankSummaries.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val recentImports: StateFlow<List<ImportJob>> = repository.recentImportJobs.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val wrongQuestions: StateFlow<List<WrongQuestionItem>> = repository.wrongQuestions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val favoriteQuestions: StateFlow<List<FavoriteQuestionItem>> = repository.favoriteQuestions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val noteQuestions: StateFlow<List<NoteQuestionItem>> = repository.noteQuestions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val examDrafts: StateFlow<List<ExamDraftSummary>?> = repository.examDrafts.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        null,
    )

    private val _settings = MutableStateFlow(container.settingsStore.snapshot())
    val settings: StateFlow<SettingsSnapshot> = _settings.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _splitImportState = MutableStateFlow(SplitImportUiState())
    val splitImportState: StateFlow<SplitImportUiState> = _splitImportState.asStateFlow()

    // 分文件匹配的代际号：换文件或离开选择页时自增，
    // 旧请求完成后发现代际不符即丢弃结果，不覆盖新状态、不触发导航。
    private val splitMatchGeneration = AtomicLong()

    private val _imageOcrState = MutableStateFlow(ImageOcrUiState())
    val imageOcrState: StateFlow<ImageOcrUiState> = _imageOcrState.asStateFlow()

    private val _imageOcrPreview = MutableStateFlow<android.graphics.Bitmap?>(null)
    val imageOcrPreview: StateFlow<android.graphics.Bitmap?> = _imageOcrPreview.asStateFlow()

    private val _pdfOcrState = MutableStateFlow(PdfOcrUiState())
    val pdfOcrState: StateFlow<PdfOcrUiState> = _pdfOcrState.asStateFlow()

    // OCR 上传识别的代际号：取消/离开/换文件时自增，
    // 晚到的响应直接丢弃——不导航、不覆盖状态、不留可保存草稿。
    private val ocrGeneration = AtomicLong()
    private var ocrJob: Job? = null
    private var pdfInspection: PdfPageInspection? = null

    private val _quizState = MutableStateFlow(QuizUiState())
    val quizState: StateFlow<QuizUiState> = _quizState.asStateFlow()

    private val _lastResult = MutableStateFlow<ResultUiState?>(null)
    val lastResult: StateFlow<ResultUiState?> = _lastResult.asStateFlow()

    private val _submittingExamToken = MutableStateFlow<Long?>(null)
    val submittingExamToken: StateFlow<Long?> = _submittingExamToken.asStateFlow()

    val pendingExamNavigation: StateFlow<Long?> = examNavigation.pending

    fun consumeExamNavigation(token: Long): Boolean =
        examNavigation.consume(token, _quizState.value.sessionToken)

    private val _questionCollection = MutableStateFlow<QuestionCollectionState?>(null)
    val questionCollection: StateFlow<QuestionCollectionState?> = _questionCollection.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _updateState = MutableStateFlow(UpdateUiState())
    val updateState: StateFlow<UpdateUiState> = _updateState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (write in examDraftWrites) {
                try {
                    if (!repository.saveExamDraft(write.snapshot)) {
                        reportExamDraftSaveFailure(write.token)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    reportExamDraftSaveFailure(write.token)
                }
            }
        }
        if (container.quizPreferences.autoCheckUpdates.value) {
            checkForUpdates(silent = true)
        }
    }

    private val _pagerJump = MutableStateFlow(0L)
    val pagerJump: StateFlow<Long> = _pagerJump.asStateFlow()

    val fontScale: StateFlow<FontScale> = container.quizPreferences.fontScale
    val appearanceMode: StateFlow<AppearanceMode> = container.quizPreferences.appearanceMode
    val colorTheme: StateFlow<ColorTheme> = container.quizPreferences.colorTheme
    val autoNextOnCorrect: StateFlow<Boolean> = container.quizPreferences.autoNextOnCorrect
    val autoCheckUpdates: StateFlow<Boolean> = container.quizPreferences.autoCheckUpdates
    val masteryThreshold: StateFlow<Int?> = container.quizPreferences.masteryThreshold
    val dailyNewCards: StateFlow<Int?> = container.quizPreferences.dailyNewCards

    fun setFontScale(value: FontScale) = container.quizPreferences.setFontScale(value)

    fun setAppearanceMode(value: AppearanceMode) = container.quizPreferences.setAppearanceMode(value)

    fun setColorTheme(value: ColorTheme) = container.quizPreferences.setColorTheme(value)

    fun setAutoNextOnCorrect(value: Boolean) = container.quizPreferences.setAutoNextOnCorrect(value)

    fun setMasteryThreshold(value: Int?) = container.quizPreferences.setMasteryThreshold(value)

    fun setDailyNewCards(value: Int?) = container.quizPreferences.setDailyNewCards(value)

    fun removeWrongItem(questionId: Long) {
        viewModelScope.launch { repository.markWrongCorrected(questionId, clock.nowMillis()) }
    }

    fun setAutoCheckUpdates(value: Boolean) = container.quizPreferences.setAutoCheckUpdates(value)

    fun checkForUpdates(silent: Boolean = false) {
        if (updateCheckJob?.isActive == true || _updateState.value.downloading) return
        _updateState.update {
            it.copy(
                checking = true,
                release = null,
                dialogVisible = false,
                message = null,
                messageIsError = false,
                permissionRequired = false,
            )
        }
        verifiedUpdateFile = null
        updateCheckJob = viewModelScope.launch {
            try {
                when (val result = container.updateClient.check(BuildConfig.VERSION_NAME)) {
                    is UpdateCheck.Available -> {
                        verifiedUpdateFile = null
                        _updateState.update {
                            it.copy(
                                checking = false,
                                release = result.release,
                                dialogVisible = true,
                                message = null,
                                messageIsError = false,
                                permissionRequired = false,
                            )
                        }
                    }
                    UpdateCheck.Latest -> {
                        if (!silent) {
                            _updateState.update {
                                it.copy(
                                    checking = false,
                                    release = null,
                                    dialogVisible = true,
                                    message = "已是最新版本 v${BuildConfig.VERSION_NAME}",
                                    messageIsError = false,
                                )
                            }
                        } else {
                            _updateState.update { it.copy(checking = false) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _updateState.update {
                    it.copy(
                        checking = false,
                        dialogVisible = !silent,
                        message = if (silent) null else UpdateProtocol.message(error),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun downloadAndInstallUpdate() {
        if (updateDownloadJob?.isActive == true) return
        val release = _updateState.value.release ?: return
        val existing = verifiedUpdateFile?.takeIf { it.isFile }
        if (existing != null) {
            installDownloadedUpdate(existing)
            return
        }
        updateDownloadJob = viewModelScope.launch {
            _updateState.update {
                it.copy(
                    downloading = true,
                    downloadedBytes = 0L,
                    totalBytes = release.apkSize ?: -1L,
                    dialogVisible = true,
                    message = null,
                    messageIsError = false,
                    permissionRequired = false,
                )
            }
            try {
                val file = container.updateClient.download(release) { downloaded, total ->
                    _updateState.update {
                        it.copy(
                            downloadedBytes = downloaded,
                            totalBytes = total,
                        )
                    }
                }
                verifiedUpdateFile = file
                _updateState.update { it.copy(downloading = false) }
                installDownloadedUpdate(file)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                _updateState.update {
                    it.copy(
                        downloading = false,
                        dialogVisible = true,
                        message = UpdateProtocol.message(error),
                        messageIsError = true,
                    )
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        updateDownloadJob?.cancel()
        updateDownloadJob = null
        verifiedUpdateFile = null
        _updateState.update {
            it.copy(
                downloading = false,
                downloadedBytes = 0L,
                totalBytes = -1L,
                dialogVisible = false,
                message = null,
                messageIsError = false,
            )
        }
        _message.value = "下载已取消"
    }

    fun dismissUpdateDialog() {
        if (_updateState.value.downloading) return
        _updateState.update {
            it.copy(
                dialogVisible = false,
                message = null,
                messageIsError = false,
                permissionRequired = false,
            )
        }
    }

    fun openInstallPermissionSettings() {
        awaitingInstallPermission = true
        try {
            updateInstaller.openInstallPermissionSettings()
        } catch (error: Exception) {
            awaitingInstallPermission = false
            _updateState.update {
                it.copy(
                    dialogVisible = true,
                    message = UpdateProtocol.message(error),
                    messageIsError = true,
                )
            }
        }
    }

    fun onAppResumed() {
        if (!awaitingInstallPermission) return
        awaitingInstallPermission = false
        if (updateInstaller.canInstallPackages()) {
            verifiedUpdateFile?.takeIf { it.isFile }?.let(::installDownloadedUpdate)
        } else {
            _updateState.update {
                it.copy(
                    dialogVisible = true,
                    permissionRequired = true,
                    message = "安装权限未开启",
                    messageIsError = true,
                )
            }
        }
    }

    private fun installDownloadedUpdate(file: File) {
        if (!updateInstaller.canInstallPackages()) {
            awaitingInstallPermission = true
            _updateState.update {
                it.copy(
                    dialogVisible = true,
                    permissionRequired = true,
                    message = null,
                    messageIsError = false,
                )
            }
            return
        }
        try {
            updateInstaller.install(file)
            _updateState.update {
                it.copy(
                    dialogVisible = false,
                    permissionRequired = false,
                    message = null,
                    messageIsError = false,
                )
            }
        } catch (error: Exception) {
            val message = when (error) {
                is UpdateException -> when (error.failure) {
                    UpdateFailure.INSTALL -> error.message.orEmpty()
                    else -> UpdateProtocol.message(error)
                }
                else -> UpdateProtocol.message(error)
            }
            _updateState.update {
                it.copy(
                    dialogVisible = true,
                    message = message.ifBlank { "无法打开系统安装器" },
                    messageIsError = true,
                )
            }
        }
    }

    suspend fun importFromUri(
        uri: Uri,
        kind: DocumentKind?,
        method: ImportMethod,
    ): Boolean {
        deleteOcrPreviewFiles()
        _importState.value = ImportUiState(isImporting = true, method = method, kind = kind)
        return try {
            withContext(Dispatchers.IO) {
                val resolver = getApplication<Application>().contentResolver
                val fileName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "导入文件"
                val mimeType = resolver.getType(uri)
                val run = resolver.openInputStream(uri)?.use { input ->
                    container.importPipeline.run(input, fileName, method, kind)
                } ?: error("无法打开所选文件")
                val banks = repository.getBanks()
                val now = System.currentTimeMillis()
                val job = ImportJob(
                    fileName = fileName,
                    mimeType = mimeType,
                    kind = run.extraction.kind.storageValue,
                    method = method,
                    status = if (run.questions.isEmpty()) {
                        ImportStatus.FAILED
                    } else {
                        ImportStatus.REVIEW
                    },
                    recognizedCount = run.recognizedCount,
                    confirmedCount = run.confirmedCount,
                    pendingCount = run.pendingCount,
                    failedCount = run.failedCount,
                    createdAt = now,
                    warnings = run.warnings,
                )
                val jobId = repository.insertImportJob(job)
                _importState.value = ImportUiState(
                    jobId = jobId,
                    fileName = fileName,
                    method = method,
                    kind = run.extraction.kind,
                    drafts = run.questions,
                    warnings = run.warnings,
                    failedCount = run.failedCount,
                    targetBankId = banks.firstOrNull()?.id,
                    newBankName = if (banks.isEmpty()) "导入题库" else "",
                )
            }
            true
        } catch (error: Throwable) {
            val message = "导入失败：${error.message ?: "未知错误"}"
            _importState.update { state ->
                state.copy(isImporting = false, error = message)
            }
            _message.value = message
            false
        }
    }

    suspend fun startManualEntry(): Boolean {
        deleteOcrPreviewFiles()
        val banks = repository.getBanks()
        _importState.value = ImportUiState(
            fileName = "手动录入",
            method = ImportMethod.LOCAL_ONLY,
            drafts = listOf(
                DraftQuestion(
                    sourceNumber = "1",
                    type = QuestionType.SINGLE,
                    stem = "",
                    options = listOf(
                        QuestionOption("A", ""),
                        QuestionOption("B", ""),
                        QuestionOption("C", ""),
                        QuestionOption("D", ""),
                    ),
                    answer = "",
                    explanation = "",
                    confidence = 0.3,
                    needsReview = true,
                    reviewReasons = listOf("手动录入，请核对题干、选项与答案"),
                    sourceFileName = "手动录入",
                ),
            ),
            warnings = listOf("手动录入仅保存在本机，确认入库前请逐项核对"),
            targetBankId = banks.firstOrNull()?.id,
            newBankName = if (banks.isEmpty()) "手动录入题库" else "",
        )
        return true
    }

    fun updateImportDraft(index: Int, draft: DraftQuestion) {
        _importState.update { state ->
            if (index !in state.drafts.indices) return@update state
            // OCR 导入与分文件一致：按当前草稿重新验证，修好即解除待核对
            if (!state.isSplitFile && !state.requiresValidAnswers) {
                return@update state.copy(drafts = state.drafts.toMutableList().also { it[index] = draft })
            }
            val old = state.drafts[index]
            val result = SplitDraftResolver.afterEdit(draft, old, state.splitProblems[index])
            state.copy(
                drafts = state.drafts.toMutableList().also { it[index] = result.draft },
                splitProblems = if (result.resolved) state.splitProblems - index else state.splitProblems,
            )
        }
    }

    // 冲突题的逐题解决：采用答案文件的值（严格校验后填入）或保留题内答案。
    fun resolveSplitConflict(index: Int, useExternalAnswer: Boolean) {
        _importState.update { state ->
            if (!state.isSplitFile || index !in state.drafts.indices) return@update state
            val problem = state.splitProblems[index] as? SplitDraftProblem.Conflict ?: return@update state
            val draft = state.drafts[index]
            val candidate = if (useExternalAnswer) {
                SplitFileMatcher.applyExternalAnswer(draft, problem.externalAnswer)
                    ?: return@update state.copy(
                        drafts = state.drafts.toMutableList().also { current ->
                            current[index] = current[index].copy(
                                needsReview = true,
                                reviewReasons = (
                                    current[index].reviewReasons +
                                        "外部答案「${problem.externalAnswer}」无法用于当前题型"
                                    ).distinct(),
                            )
                        },
                    )
            } else {
                draft
            }
            val merged = SplitDraftResolver.afterConflictResolve(candidate, problem)
            state.copy(
                drafts = state.drafts.toMutableList().also { it[index] = merged },
                splitProblems = state.splitProblems - index,
            )
        }
    }

    fun updateImportTargetBank(bankId: Long?) {
        _importState.update { it.copy(targetBankId = bankId) }
    }

    fun updateImportNewBankName(name: String) {
        _importState.update { it.copy(newBankName = name) }
    }

    fun selectSplitQuestionFile(uri: Uri?) = selectSplitSlot(isQuestion = true, uri = uri)

    fun selectSplitAnswerFile(uri: Uri?) = selectSplitSlot(isQuestion = false, uri = uri)

    // 取消选择（uri 为 null）保留原值；换文件时当下自增代际号，
    // 作废进行中的匹配请求与更早的选择查询（元数据不会乱序覆盖）。
    // URI、临时名与 token 必须在启动 IO 前同步落槽；IO 只回填当前 token 的显示文件名。
    private fun selectSplitSlot(isQuestion: Boolean, uri: Uri?) {
        if (uri == null) return
        val token = splitMatchGeneration.incrementAndGet()
        val provisionalName = uri.lastPathSegment?.substringAfterLast('/') ?: "所选文件"
        _splitImportState.update { state ->
            val slot = SplitSlotUiState(
                uri = uri.toString(),
                fileName = provisionalName,
                error = null,
                selectionToken = token,
            )
            val updated = if (isQuestion) state.copy(question = slot) else state.copy(answer = slot)
            updated.copy(isMatching = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val displayName = queryDisplayName(uri) ?: return@launch
            _splitImportState.update { state ->
                val current = if (isQuestion) state.question else state.answer
                if (current.selectionToken != token || current.uri != uri.toString()) {
                    return@update state
                }
                val slot = current.copy(fileName = displayName)
                if (isQuestion) state.copy(question = slot) else state.copy(answer = slot)
            }
        }
    }

    fun onSplitScreenLeft() {
        splitMatchGeneration.incrementAndGet()
        _splitImportState.update { it.copy(isMatching = false) }
    }

    suspend fun matchSplitFiles(): Boolean {
        if (_splitImportState.value.isMatching) return false
        val slots = _splitImportState.value
        val questionUri = slots.question.uri?.let(Uri::parse)
        val answerUri = slots.answer.uri?.let(Uri::parse)
        if (questionUri == null || answerUri == null) return false
        val generation = splitMatchGeneration.get()
        _splitImportState.update {
            it.copy(
                isMatching = true,
                matchError = null,
                question = it.question.copy(error = null),
                answer = it.answer.copy(error = null),
            )
        }
        try {
            val resolver = getApplication<Application>().contentResolver
            // 打不开的文件把错误落到对应槽位，另一份文件保留
            val questionStream = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(questionUri) }.getOrNull()
            }
            if (splitMatchGeneration.get() != generation) {
                questionStream?.close()
                return false
            }
            if (questionStream == null) {
                _splitImportState.update {
                    it.copy(
                        question = it.question.copy(error = "无法打开试题文件，请更换文件后重试"),
                    )
                }
                return false
            }
            val answerStream = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(answerUri) }.getOrNull()
            }
            if (splitMatchGeneration.get() != generation) {
                questionStream.close()
                answerStream?.close()
                return false
            }
            if (answerStream == null) {
                questionStream.close()
                _splitImportState.update {
                    it.copy(
                        answer = it.answer.copy(error = "无法打开答案文件，请更换文件后重试"),
                    )
                }
                return false
            }
            val outcome = withContext(Dispatchers.IO) {
                questionStream.use { questionInput ->
                    answerStream.use { answerInput ->
                        container.splitFileImporter.import(
                            questionInput = questionInput,
                            questionFileName = slots.question.fileName,
                            answerInput = answerInput,
                            answerFileName = slots.answer.fileName,
                        )
                    }
                }
            }
            if (splitMatchGeneration.get() != generation) return false
            when (outcome) {
                is SplitImportOutcome.SlotErrors -> {
                    _splitImportState.update {
                        it.copy(
                            question = it.question.copy(error = outcome.question.error),
                            answer = it.answer.copy(error = outcome.answer.error),
                        )
                    }
                    return false
                }
                is SplitImportOutcome.Success -> {
                    // DB 挂起点前后都核对代际号；已被取代则不留校对状态、清掉刚写入的任务
                    if (splitMatchGeneration.get() != generation) return false
                    val banks = repository.getBanks()
                    if (splitMatchGeneration.get() != generation) return false
                    val jobId = repository.insertImportJob(
                        ImportJob(
                            fileName = "${slots.question.fileName} ＋ ${slots.answer.fileName}",
                            mimeType = null,
                            kind = outcome.questionKind.storageValue,
                            method = ImportMethod.LOCAL_ONLY,
                            status = ImportStatus.REVIEW,
                            recognizedCount = outcome.drafts.size,
                            confirmedCount = 0,
                            pendingCount = outcome.drafts.count { it.needsReview },
                            failedCount = 0,
                            createdAt = System.currentTimeMillis(),
                            warnings = outcome.warnings,
                        ),
                    )
                    if (splitMatchGeneration.get() != generation) {
                        repository.deleteImportJob(jobId)
                        return false
                    }
                    // 按当前草稿重新验证，并把匹配问题写回对应草稿：待核对状态与原因直接可见
                    val drafts = outcome.drafts.mapIndexed { index, draft ->
                        SplitDraftResolver.applyMatchProblems(draft, outcome.problems[index])
                    }
                    deleteOcrPreviewFiles()
                    _importState.value = ImportUiState(
                        jobId = jobId,
                        fileName = slots.question.fileName,
                        method = ImportMethod.LOCAL_ONLY,
                        kind = outcome.questionKind,
                        drafts = drafts,
                        warnings = outcome.warnings,
                        failedCount = 0,
                        targetBankId = banks.firstOrNull()?.id,
                        newBankName = if (banks.isEmpty()) "导入题库" else "",
                        isSplitFile = true,
                        answerFileName = slots.answer.fileName,
                        splitProblems = outcome.problems,
                    )
                    return true
                }
            }
        } finally {
            _splitImportState.update { it.copy(isMatching = false) }
        }
    }

    suspend fun confirmImport(): ConfirmImportResult {
        val state = _importState.value
        if (state.isSplitFile || state.requiresValidAnswers) {
            val blocking = state.drafts.count { it.needsReview || it.answer.isBlank() }
            if (blocking > 0) {
                val message = "还有 $blocking 题待处理（待核对或缺少答案），全部解决后才能入库"
                _importState.update { it.copy(error = message) }
                return ConfirmImportResult.Failure(message)
            }
        }
        _importState.update { it.copy(isConfirming = true, error = null) }
        val result = container.confirmImportUseCase.execute(
            drafts = state.drafts,
            target = ConfirmTarget(
                bankId = state.targetBankId,
                newBankName = state.newBankName,
            ),
            sourceFileName = state.fileName,
            importJobId = state.jobId,
            onlyValidAnswers = state.isSplitFile || state.requiresValidAnswers,
        )
        when (result) {
            is ConfirmImportResult.Success -> {
                _importState.update {
                    it.copy(
                        isConfirming = false,
                        confirmedBankId = result.value.bankId,
                    )
                }
                _message.value = "已入库 ${result.value.questionIds.size} 道题"
            }
            is ConfirmImportResult.Failure -> {
                _importState.update {
                    it.copy(isConfirming = false, error = result.message)
                }
                _message.value = result.message
            }
        }
        return result
    }

    fun clearImport() {
        deleteOcrPreviewFiles()
        _importState.value = ImportUiState()
    }

    // ===== DeepSeek 在线 OCR（图片/拍照 与 扫描 PDF）=====

    fun ocrKeyState(): OcrKeySource {
        ocrKeySourceOverride?.let { return it }
        val snapshot = container.settingsStore.snapshot()
        return OcrKeyResolver.resolve(
            provider = snapshot.provider,
            baseUrl = snapshot.baseUrl,
            aiKeyPresent = snapshot.hasApiKey,
            dedicatedKeyPresent = container.settingsStore.ocrApiKeyForRequest().isNotBlank(),
        )
    }

    fun saveOcrApiKey(key: String) {
        container.settingsStore.saveOcrApiKey(key)
        _settings.value = container.settingsStore.snapshot()
        _message.value = "DeepSeek OCR Key 已保存"
    }

    fun clearOcrApiKey() {
        container.settingsStore.clearOcrApiKey()
        _settings.value = container.settingsStore.snapshot()
        _message.value = "DeepSeek OCR Key 已清除"
    }

    /**
     * 图片/拍照进入 OCR 预处理：仅复制文件并解码预览图（扶正 EXIF），
     * 不发起任何网络请求；上传只发生在 ImageOcrScreen 的「上传并识别」按钮。
     */
    suspend fun prepareImageOcr(uri: Uri, kind: DocumentKind): Boolean {
        invalidateOcrRequest()
        // 更换图片：清掉上一张的源临时副本，避免缓存泄漏
        _imageOcrState.value.sourcePath?.let { path -> runCatching { File(path).delete() } }
        return runCatching {
            val temp = withContext(Dispatchers.IO) { copyUriToOcrTemp(uri) }
            val preview = withContext(Dispatchers.IO) {
                ImageOcrBitmaps.decodeExifCorrectedPreview(temp.absolutePath)
            }
            _imageOcrPreview.value = preview
            _imageOcrState.value = ImageOcrUiState(
                sourcePath = temp.absolutePath,
                fileName = queryDisplayName(uri) ?: temp.name,
                kind = kind,
            )
            true
        }.getOrElse { error ->
            _message.value = "无法读取所选图片：${error.message ?: "文件不可读"}"
            false
        }
    }

    fun onImageOcrScreenLeft() {
        val source = _imageOcrState.value.sourcePath
        invalidateOcrRequest()
        _imageOcrState.value = ImageOcrUiState()
        _imageOcrPreview.value = null
        source?.let { path -> runCatching { File(path).delete() } }
    }

    /**
     * 上传并识别：只在用户点击时执行。取消/离开/换文件后代际号变化，
     * 迟到响应被直接丢弃（不导航、不覆盖状态、不留可保存草稿）。
     */
    suspend fun submitImageOcr(
        rotationDegrees: Int,
        previewCropRect: CropRect,
        previewWidth: Float,
        previewHeight: Float,
        billingConfirmed: Boolean = false,
    ): Boolean {
        val state = _imageOcrState.value
        if (state.isRunning || state.sourcePath == null) return false
        if (state.pendingBillingConfirm && !billingConfirmed) return false
        val client = ocrClientFactory()
        if (client == null) {
            _imageOcrState.update {
                it.copy(error = OCR_KEY_MISSING_MESSAGE)
            }
            return false
        }
        val generation = ocrGeneration.get()
        val sourcePath = state.sourcePath ?: return false
        val kind = state.kind ?: DocumentKind.IMAGE
        val fileName = state.fileName.ifBlank { "图片" }
        _imageOcrState.update {
            it.copy(
                phase = OcrPhase.PREPARING,
                statusText = "正在准备上传副本…",
                error = null,
                pendingBillingConfirm = false,
            )
        }
        var uploadFile: File? = null
        val deferred = viewModelScope.async {
            try {
                withContext(Dispatchers.IO) {
                    ImageOcrBitmaps.prepareUploadCopy(
                        sourcePath = sourcePath,
                        rotationDegrees = rotationDegrees,
                        previewCropRect = previewCropRect,
                        previewWidth = previewWidth,
                        previewHeight = previewHeight,
                        outputDir = ocrTempDir(),
                    ).also { uploadFile = it }
                }
                if (ocrGeneration.get() != generation) {
                    uploadFile?.delete()
                    return@async false
                }
                _imageOcrState.update {
                    it.copy(phase = OcrPhase.RUNNING, statusText = "图片上传至 DeepSeek 识别中…")
                }
                val success = client.recognizeFile(
                    file = uploadFile!!,
                    // 上传副本一律 JPEG：文件名/MIME 统一 .jpg / image/jpeg，与内容一致
                    fileName = OcrUploadNaming.imageUploadFileName(fileName),
                    knownPageCount = 1,
                )
                if (ocrGeneration.get() != generation) return@async false
                if (success.pages.size != 1) {
                    throw OcrException(
                        OcrFailureKind.PAGE_MISMATCH,
                        "OCR 返回页数与图片不符（期望 1 页，实际 ${success.pages.size} 页），本次结果不可用",
                    )
                }
                val text = success.pages.single().text.trim()
                val importState = buildOcrImportState(
                    source = OcrImportSource.IMAGE,
                    kind = kind,
                    fileName = fileName,
                    pageTexts = listOf(text),
                    extraWarnings = listOf("题目文字来自 DeepSeek 在线识别，已上传至 DeepSeek 官方服务"),
                    previewPath = uploadFile?.absolutePath,
                    pdfPath = null,
                    sourcePagesAllFirst = true,
                )
                if (importState == null) {
                    uploadFile?.delete()
                    _imageOcrState.update {
                        it.copy(
                            phase = OcrPhase.FAILED,
                            statusText = "",
                            error = "OCR 未识别到题目，本次结果不入库；请确认图片内容后重试",
                        )
                    }
                    return@async false
                }
                // 压缩副本转为「对照原图」，随校对保留，入库/清空时删除
                deleteOcrPreviewFiles()
                _importState.value = importState
                uploadFile = null
                _imageOcrState.update { it.copy(phase = null, statusText = "", error = null) }
                true
            } catch (error: Throwable) {
                uploadFile?.delete()
                if (ocrGeneration.get() != generation || error is CancellationException) {
                    return@async false
                }
                handleImageOcrFailure(error)
                false
            }
        }
        ocrJob = deferred
        return try {
            deferred.await()
        } catch (error: CancellationException) {
            false
        } finally {
            if (ocrJob === deferred) ocrJob = null
        }
    }

    /**
     * PDF 导入分流（全部在 IO 线程）：先把原文件有界落盘为临时副本（整份上传与校对对照共用），
     * 再从文件逐页检查文字层（PDDocument.load(File) 流式解析，不在内存里重复持有整份字节）。
     * 全部页面可用时完全沿用现有本地导入（保留用户选择的识别方式）；
     * 含扫描页/本地解析失败时进入确认页，由用户主动确认后才整份上传。
     */
    suspend fun importPdfFromUri(uri: Uri, method: ImportMethod): PdfImportDecision {
        invalidateOcrRequest()
        val fileName = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "PDF 文件"
        val temp = try {
            withContext(Dispatchers.IO) { copyUriToPdfTemp(uri) }
        } catch (error: OcrFileTooLargeException) {
            return PdfImportDecision.Failed(
                "PDF 超过 ${OcrLimits.MAX_FILE_BYTES / (1024 * 1024)} MB 上限，请压缩或拆分后再试",
            )
        } catch (error: Exception) {
            return PdfImportDecision.Failed("无法读取所选 PDF：${error.message ?: "文件不可读"}")
        }
        val inspection = withContext(Dispatchers.IO) { PdfPageInspector().inspectFile(temp) }
        return when (val plan = PdfOcrPlanner.plan(inspection)) {
            is PdfOcrPlan.AllTextUsable -> {
                // 全部页面有可用文字层：不请求 OCR，走既有本地导入路径；临时副本不再需要
                temp.delete()
                if (importFromUri(uri, DocumentKind.PDF, method)) {
                    PdfImportDecision.ProceedLocal
                } else {
                    PdfImportDecision.Failed("PDF 本地解析失败，请检查文件后重试")
                }
            }
            is PdfOcrPlan.NeedsOcr, is PdfOcrPlan.LoadFailed -> {
                pdfInspection = if (plan is PdfOcrPlan.NeedsOcr) inspection else null
                _pdfOcrState.value = PdfOcrUiState(
                    sourcePath = temp.absolutePath,
                    fileName = fileName,
                    pageCount = inspection.pageCount,
                    usablePageCount = inspection.usablePageCount,
                    scanPages = (plan as? PdfOcrPlan.NeedsOcr)?.scanPages.orEmpty(),
                    loadErrorMessage = (plan as? PdfOcrPlan.LoadFailed)?.message,
                )
                PdfImportDecision.NeedsDecision
            }
            is PdfOcrPlan.TooManyPages -> {
                // 页数超上限：官方拒绝超 1000 页的识别文件。先试本地导入（可能有完整文字层），
                // 提取不出题目再明确拒绝并提示拆分
                temp.delete()
                val localOk = importFromUri(uri, DocumentKind.PDF, method)
                if (localOk && _importState.value.drafts.isNotEmpty()) {
                    PdfImportDecision.ProceedLocal
                } else {
                    PdfImportDecision.Failed(
                        "PDF 超过 ${OcrLimits.MAX_PAGES} 页在线识别上限" +
                            "（当前 ${plan.pageCount} 页），且未能提取到可用题目；请拆分后重试",
                    )
                }
            }
            is PdfOcrPlan.Encrypted -> {
                temp.delete()
                PdfImportDecision.Failed(plan.message)
            }
            is PdfOcrPlan.Empty -> {
                temp.delete()
                PdfImportDecision.Failed(plan.message)
            }
        }
    }

    fun onPdfOcrScreenLeft() {
        val source = _pdfOcrState.value.sourcePath
        invalidateOcrRequest()
        _pdfOcrState.value = PdfOcrUiState()
        pdfInspection = null
        source?.let { path -> runCatching { File(path).delete() } }
    }

    /**
     * 用户确认后的整份 PDF 在线识别：整份文件上传至 DeepSeek，
     * 合并时逐页优先保留可用原始文字，只有扫描页采用 OCR（FULL_OCR 则整份采用）。
     */
    suspend fun submitPdfOcr(mode: PdfOcrMergeMode, billingConfirmed: Boolean = false): Boolean {
        val state = _pdfOcrState.value
        if (state.isRunning || state.sourcePath == null) return false
        if (state.pendingBillingConfirm && !billingConfirmed) return false
        val client = ocrClientFactory()
        if (client == null) {
            _pdfOcrState.update {
                it.copy(error = OCR_KEY_MISSING_MESSAGE)
            }
            return false
        }
        val generation = ocrGeneration.get()
        val sourcePath = state.sourcePath ?: return false
        val fileName = state.fileName.ifBlank { "scan.pdf" }
        val inspection = pdfInspection
        val effectiveMode = if (inspection == null) PdfOcrMergeMode.FULL_OCR else mode
        _pdfOcrState.update {
            it.copy(
                phase = OcrPhase.PREPARING,
                statusText = "正在准备上传整份 PDF…",
                error = null,
                pendingBillingConfirm = false,
            )
        }
        val file = File(sourcePath)
        val deferred = viewModelScope.async {
            try {
                if (file.length() > OcrLimits.MAX_FILE_BYTES) {
                    throw OcrException(
                        OcrFailureKind.TOO_LARGE,
                        "PDF 超过 ${OcrLimits.MAX_FILE_BYTES / (1024 * 1024)} MB 上限，请压缩或拆分后再试",
                    )
                }
                if (inspection != null && inspection.pageCount > OcrLimits.MAX_PAGES) {
                    throw OcrException(
                        OcrFailureKind.TOO_MANY_PAGES,
                        "PDF 超过 ${OcrLimits.MAX_PAGES} 页上限（当前 ${inspection.pageCount} 页），请拆分后再试",
                    )
                }
                _pdfOcrState.update {
                    it.copy(phase = OcrPhase.RUNNING, statusText = "整份 PDF 上传至 DeepSeek 识别中…")
                }
                val success = client.recognizeFile(
                    file = file,
                    fileName = OcrUploadNaming.pdfUploadFileName(fileName),
                    knownPageCount = inspection?.pageCount,
                )
                if (ocrGeneration.get() != generation) return@async false
                val merged = if (inspection != null) {
                    PdfOcrMerger.merge(inspection, success.pages, effectiveMode)
                } else {
                    PdfOcrMergeResult(
                        pages = success.pages.map { page ->
                            MergedPdfPage(index = page.index, text = page.text.trim(), fromOcr = true)
                        },
                        warnings = listOf("整份 PDF 已采用在线识别文字（本地未解析出文字层）"),
                    )
                }
                val importState = buildOcrImportState(
                    source = OcrImportSource.PDF,
                    kind = DocumentKind.PDF,
                    fileName = fileName,
                    pageTexts = merged.pages.map { it.text },
                    extraWarnings = merged.warnings +
                        "题目文字来自 DeepSeek 在线识别，整份 PDF 已上传至 DeepSeek 官方服务",
                    previewPath = null,
                    pdfPath = file.absolutePath,
                    sourcePagesAllFirst = false,
                )
                if (importState == null) {
                    _pdfOcrState.update {
                        it.copy(
                            phase = OcrPhase.FAILED,
                            statusText = "",
                            error = "OCR 未识别到题目，本次结果不入库；请确认文件内容后重试",
                        )
                    }
                    return@async false
                }
                deleteOcrPreviewFiles()
                _importState.value = importState
                // 临时 PDF 的清理责任移交校对页：sourcePath 置空后离开确认页不再删除，
                // 由入库/放弃校对（onReviewScreenLeft）或下一次导入统一清理
                pdfInspection = null
                _pdfOcrState.update { it.copy(sourcePath = null, phase = null, statusText = "", error = null) }
                true
            } catch (error: Throwable) {
                if (ocrGeneration.get() != generation || error is CancellationException) {
                    return@async false
                }
                handlePdfOcrFailure(error)
                false
            }
        }
        ocrJob = deferred
        return try {
            deferred.await()
        } catch (error: CancellationException) {
            false
        } finally {
            if (ocrJob === deferred) ocrJob = null
        }
    }

    /**
     * 取消进行中的识别：中断请求并释放资源。
     * 已进入上传/识别阶段（RUNNING）的取消：请求可能已送达服务端并继续识别计费，
     * 本次会话的下次提交前强制确认「可能重复计费」；仅本地准备阶段（PREPARING）取消则无需提示。
     */
    fun cancelOcr() {
        val imageState = _imageOcrState.value
        val pdfState = _pdfOcrState.value
        if (!imageState.isRunning && !pdfState.isRunning) return
        val imageRisk = imageState.phase == OcrPhase.RUNNING
        val pdfRisk = pdfState.phase == OcrPhase.RUNNING
        cancelOcrRequest()
        _imageOcrState.update {
            it.copy(
                phase = if (imageRisk) OcrPhase.FAILED else null,
                statusText = "",
                pendingBillingConfirm = imageRisk,
                error = if (imageRisk) OCR_CANCELLED_BILLING_MESSAGE else null,
            )
        }
        _pdfOcrState.update {
            it.copy(
                phase = if (pdfRisk) OcrPhase.FAILED else null,
                statusText = "",
                pendingBillingConfirm = pdfRisk,
                error = if (pdfRisk) OCR_CANCELLED_BILLING_MESSAGE else null,
            )
        }
    }

    /** 校对页退出（入库成功或放弃校对）：删除 OCR 对照用的临时原图/原页，重置导入状态。 */
    fun onReviewScreenLeft() {
        val state = _importState.value
        if (state.ocrSource != null) clearImport()
    }

    /** OCR 导入后的「对照原图/原页」临时文件；入库或清空时删除，不写入题库与备份。 */
    private fun deleteOcrPreviewFiles() {
        val state = _importState.value
        state.originalPreviewPath?.let { path -> runCatching { File(path).delete() } }
        state.pdfPreviewPath?.let { path -> runCatching { File(path).delete() } }
    }

    private fun cancelOcrRequest() {
        ocrGeneration.incrementAndGet()
        ocrJob?.cancel()
        ocrJob = null
    }

    private fun invalidateOcrRequest() {
        cancelOcrRequest()
        _imageOcrPreview.value = null
        pdfInspection = null
    }

    private suspend fun buildOcrImportState(
        source: OcrImportSource,
        kind: DocumentKind,
        fileName: String,
        pageTexts: List<String>,
        extraWarnings: List<String>,
        previewPath: String?,
        pdfPath: String?,
        sourcePagesAllFirst: Boolean,
    ): ImportUiState? {
        // 按页序拼接后统一解析：跨页题不会被拆开
        val joined = pageTexts.joinToString("\n")
        val parse = container.parser.parse(joined)
        if (parse.questions.isEmpty()) return null
        val post = OcrDraftPostProcessor.process(parse, pageTexts, extraWarnings)
        val banks = repository.getBanks()
        val job = ImportJob(
            fileName = fileName,
            mimeType = null,
            kind = kind.storageValue,
            method = ImportMethod.LOCAL_ONLY,
            status = ImportStatus.REVIEW,
            recognizedCount = post.drafts.size,
            confirmedCount = post.drafts.count { !it.needsReview },
            pendingCount = post.drafts.count { it.needsReview },
            failedCount = 0,
            createdAt = System.currentTimeMillis(),
            warnings = parse.warnings + post.warnings,
        )
        val jobId = repository.insertImportJob(job)
        val sourcePages = if (sourcePagesAllFirst) {
            post.drafts.indices.associate { it to 1 }
        } else {
            post.sourcePages
        }
        return ImportUiState(
            jobId = jobId,
            fileName = fileName,
            method = ImportMethod.LOCAL_ONLY,
            kind = kind,
            drafts = post.drafts,
            warnings = parse.warnings + post.warnings,
            failedCount = 0,
            targetBankId = banks.firstOrNull()?.id,
            newBankName = if (banks.isEmpty()) "导入题库" else "",
            ocrSource = source,
            requiresValidAnswers = true,
            sourcePages = sourcePages,
            originalPreviewPath = previewPath,
            pdfPreviewPath = pdfPath,
        )
    }

    private fun handleImageOcrFailure(error: Throwable) {
        val failure = toOcrFailure(error)
        _imageOcrState.update {
            it.copy(
                phase = OcrPhase.FAILED,
                statusText = "",
                error = failure.message,
                pendingBillingConfirm = failure.kind in OCR_BILLING_RISK_KINDS,
            )
        }
    }

    private fun handlePdfOcrFailure(error: Throwable) {
        val failure = toOcrFailure(error)
        _pdfOcrState.update {
            it.copy(
                phase = OcrPhase.FAILED,
                statusText = "",
                error = failure.message,
                pendingBillingConfirm = failure.kind in OCR_BILLING_RISK_KINDS,
            )
        }
    }

    private fun toOcrFailure(error: Throwable): OcrException = when (error) {
        is OcrException -> error
        is IllegalStateException, is IllegalArgumentException ->
            OcrException(OcrFailureKind.BAD_REQUEST, error.message ?: "文件无法处理")
        else -> OcrException(
            OcrFailureKind.NETWORK,
            "OCR 请求失败：${error.message ?: "未知错误"}，请检查网络后重试",
        )
    }

    private fun ocrTempDir(): File =
        File(getApplication<Application>().cacheDir, "ocr_import").apply { mkdirs() }

    private class OcrFileTooLargeException :
        Exception("文件超过大小上限")

    /** PDF 原文件有界落盘：临时副本供整份上传与校对对照共用，避免在内存里持有整份字节。 */
    private fun copyUriToPdfTemp(uri: Uri): File {
        val resolver = getApplication<Application>().contentResolver
        val file = File.createTempFile("ocr_pdf_", ".pdf", ocrTempDir())
        resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > OcrLimits.MAX_FILE_BYTES) {
                        file.delete()
                        throw OcrFileTooLargeException()
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: run {
            file.delete()
            error("无法打开所选文件")
        }
        if (file.length() <= 0L) {
            file.delete()
            error("所选 PDF 为空")
        }
        return file
    }

    private suspend fun copyUriToOcrTemp(uri: Uri): File {
        val resolver = getApplication<Application>().contentResolver
        val displayName = queryDisplayName(uri)
        val extension = displayName?.substringAfterLast('.', "")?.lowercase()?.takeIf {
            it.matches(Regex("[a-z0-9]{1,8}"))
        } ?: "jpg"
        val file = File.createTempFile("ocr_img_", ".$extension", ocrTempDir())
        resolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > OcrLimits.MAX_FILE_BYTES) {
                        file.delete()
                        throw OcrFileTooLargeException()
                    }
                    output.write(buffer, 0, read)
                }
            }
        } ?: run {
            file.delete()
            error("无法打开所选图片")
        }
        if (file.length() <= 0L) {
            file.delete()
            error("所选图片为空")
        }
        return file
    }

    suspend fun startSequenceQuiz(bankId: Long): QuizStartResult {
        return runCatching {
            val bank = repository.getBank(bankId)
                ?: return QuizStartResult(false, "题库不存在")
            val questions = repository.getQuestions(bankId)
            if (questions.isEmpty()) {
                return QuizStartResult(false, "题库中还没有题目，请先导入")
            }
            val progress = repository.getSequenceProgress(bankId)
            val roundStartedAt = progress?.roundStartedAt ?: 0L
            val quizQuestions = questions.map { it.toQuizQuestion(1.0) }
            val startIndex = progress?.currentIndex?.coerceIn(0, quizQuestions.lastIndex) ?: 0
            val favoriteIds = repository.getFavoriteIdsForBank(bankId)
            val config = ExamConfig(passScore = 0.0)
            val roundAttempts = repository.getSequenceRoundAttempts(bankId, roundStartedAt)
            val latest = SessionBackfill.latestPerQuestion(roundAttempts)
            val prefilledAnswers = latest.mapValues { (_, seed) -> seed.answer }
            val prefilledCommitted = latest.keys
            val attemptStats = attemptStatsFor(quizQuestions)
            startQuiz(
                bankId = bankId,
                bankName = bank.name,
                mode = PracticeMode.SEQUENCE,
                questions = quizQuestions,
                config = config,
                bookmarkedIds = favoriteIds,
                startIndex = startIndex,
                answers = prefilledAnswers,
                committedIds = prefilledCommitted,
                answerViewedIds = latest.filterValues { it.answerViewed }.keys,
                attemptStats = attemptStats,
                roundStartedAt = roundStartedAt,
            )
            repository.saveSequencePosition(
                bankId = bankId,
                currentIndex = startIndex,
                now = clock.nowMillis(),
            )
            QuizStartResult(true)
        }.getOrElse { error ->
            QuizStartResult(false, "无法开始练习：${error.message ?: "未知错误"}")
        }
    }

    suspend fun startPastPaperQuiz(paperId: Long): QuizStartResult = runCatching {
        val paper = repository.getPastPaper(paperId) ?: return QuizStartResult(false, "真题卷不存在")
        val questions = repository.getPastPaperQuestions(paperId)
        if (questions.isEmpty()) return QuizStartResult(false, "卷中还没有题目")
        val quizQuestions = questions.map { it.toQuizQuestion(1.0) }
        startQuiz(
            bankId = paper.bankId, bankName = paper.name, mode = PracticeMode.PAPER,
            questions = quizQuestions, config = ExamConfig(passScore = 0.0),
            bookmarkedIds = repository.getFavoriteIdsForBank(paper.bankId),
            attemptStats = attemptStatsFor(quizQuestions),
        )
        QuizStartResult(true)
    }.getOrElse { error -> QuizStartResult(false, "无法开始真题练习：${error.message ?: "未知错误"}") }

    suspend fun startWrongQuiz(
        bankId: Long? = null,
        questionId: Long? = null,
        questionIds: Set<Long>? = null,
    ): QuizStartResult {
        return runCatching {
            val rows = repository.getPendingWrongRows(bankId, questionId, questionIds)
            if (rows.isEmpty()) return QuizStartResult(false, "当前筛选范围没有错题")
            val questions = repository.getQuestionsByIds(
                rows.map { it.wrongItem.questionId },
            )
            if (questions.isEmpty()) return QuizStartResult(false, "错题对应的题目已被删除")
            val name = if (bankId != null) {
                repository.getBank(bankId)?.name ?: "错题重练"
            } else {
                "全部错题"
            }
            val quizQuestions = questions.map { it.toQuizQuestion(1.0) }
            startQuiz(
                bankId = bankId,
                bankName = name,
                mode = PracticeMode.WRONG,
                questions = quizQuestions,
                config = ExamConfig(passScore = 0.0),
                attemptStats = attemptStatsFor(quizQuestions),
            )
            QuizStartResult(true)
        }.getOrElse { error -> QuizStartResult(false, "无法开始错题练习：${error.message}") }
    }

    suspend fun startFavoriteQuiz(
        bankId: Long? = null,
        questionId: Long? = null,
    ): QuizStartResult {
        return runCatching {
            val ids = if (questionId != null) {
                listOf(questionId).filter { repository.isFavorite(it) }
            } else if (bankId != null) {
                repository.getFavoriteIdsForBank(bankId).toList()
            } else {
                repository.getAllFavoriteIds().toList()
            }
            if (ids.isEmpty()) return QuizStartResult(false, "当前范围没有收藏题")
            val questions = repository.getQuestionsByIds(ids)
            if (questions.isEmpty()) return QuizStartResult(false, "收藏题已被删除")
            val name = bankId?.let { repository.getBank(it)?.name } ?: "收藏练习"
            val quizQuestions = questions.map { it.toQuizQuestion(1.0) }
            startQuiz(
                bankId = bankId,
                bankName = name,
                mode = PracticeMode.FAVORITE,
                questions = quizQuestions,
                config = ExamConfig(passScore = 0.0),
                attemptStats = attemptStatsFor(quizQuestions),
            )
            QuizStartResult(true)
        }.getOrElse { error -> QuizStartResult(false, "无法开始收藏练习：${error.message}") }
    }

    suspend fun topicSetupData(): TopicSetupData = withContext(Dispatchers.IO) {
        TopicSetupData(
            banks = repository.getBanks(),
            questions = repository.getAllQuestions(),
            chapters = repository.getAllChapters(),
        )
    }

    suspend fun startTopicQuiz(
        bankIds: Set<Long>,
        types: Set<QuestionType>,
        count: Int,
        random: Random = Random.Default,
        chapterIds: Set<Long?>? = null,
    ): QuizStartResult {
        return runCatching {
            if (bankIds.isEmpty()) return QuizStartResult(false, "请选择至少一个题库")
            if (types.isEmpty()) return QuizStartResult(false, "请选择至少一个题型")
            val candidates = TopicPractice.eligibleQuestions(
                allQuestions = repository.getAllQuestions(),
                bankIds = bankIds,
                types = types,
                chapterIds = chapterIds,
            )
            if (candidates.isEmpty()) {
                return QuizStartResult(false, "当前筛选没有可练题")
            }
            val drawn = when (val result = TopicPractice.drawQuestions(candidates, count, random)) {
                is TopicDrawResult.Failure -> return QuizStartResult(false, result.message)
                is TopicDrawResult.Success -> result.questions
            }
            val bankId = bankIds.singleOrNull()
            val bankName = bankId?.let { repository.getBank(it)?.name } ?: "专项练习"
            val quizQuestions = drawn.map { it.toQuizQuestion(1.0) }
            startQuiz(
                bankId = bankId,
                bankName = bankName,
                mode = PracticeMode.TOPIC,
                questions = quizQuestions,
                config = ExamConfig(passScore = 0.0),
                attemptStats = attemptStatsFor(quizQuestions),
            )
            QuizStartResult(true)
        }.getOrElse { error ->
            QuizStartResult(false, "无法开始专项练习：${error.message ?: "未知错误"}")
        }
    }

    suspend fun startSingleQuestionQuiz(
        questionId: Long,
        mode: PracticeMode = PracticeMode.FAVORITE,
    ): QuizStartResult {
        return runCatching {
            val question = repository.getQuestion(questionId)
                ?: return QuizStartResult(false, "题目不存在")
            val bank = repository.getBank(question.bankId)
            val quizQuestions = listOf(question.toQuizQuestion(1.0))
            startQuiz(
                bankId = question.bankId,
                bankName = bank?.name ?: "单题练习",
                mode = mode,
                questions = quizQuestions,
                config = ExamConfig(passScore = 0.0),
                bookmarkedIds = if (repository.isFavorite(questionId)) setOf(questionId) else emptySet(),
                startIndex = 0,
                attemptStats = attemptStatsFor(quizQuestions),
            )
            QuizStartResult(true)
        }.getOrElse { error -> QuizStartResult(false, "无法打开题目：${error.message}") }
    }

    suspend fun examSetupData(bankId: Long): ExamSetupData {
        val questions = repository.getQuestions(bankId)
        val available = ExamEligibility.availableCounts(questions)
        val config = ExamConfig(
            singleCount = minOf(available.getValue(QuestionType.SINGLE), 30),
            multiCount = minOf(available.getValue(QuestionType.MULTI), 10),
            judgeCount = minOf(available.getValue(QuestionType.JUDGE), 5),
            fillCount = minOf(available.getValue(QuestionType.FILL), 5),
            singlePoints = 2.0,
            multiPoints = 2.0,
            judgePoints = 2.0,
            fillPoints = 2.0,
            durationMinutes = 60,
            passScore = 60.0,
            shuffleOptions = true,
            wrongFirst = true,
            unansweredFirst = false,
            multiSelectRule = com.xjb.linti.domain.MultiSelectRule.LESS_NO_SCORE,
            showAnswerAfterAnswer = false,
        ).let { initial ->
            val total = initial.totalPoints
            initial.copy(
                passScore = if (total > 0) (total * 0.6).toInt().coerceAtLeast(1).toDouble().coerceAtMost(total) else 0.0,
            )
        }
        return ExamSetupData(available = available, config = config)
    }

    suspend fun startExam(bankId: Long, config: ExamConfig): QuizStartResult {
        return runCatching {
            val bank = repository.getBank(bankId)
                ?: return QuizStartResult(false, "题库不存在")
            if (repository.hasExamDraft(bankId)) {
                return QuizStartResult(false, "请先继续或放弃本场考试")
            }
            val questions = repository.getQuestions(bankId)
            if (questions.isEmpty()) return QuizStartResult(false, "题库中没有可抽取的题目")
            val wrongIds = repository.getWrongRows()
                .filter { it.wrongItem.bankId == bankId }
                .map { it.wrongItem.questionId }
                .toSet()
            val answeredIds = repository.getAnsweredQuestionIds(bankId)
            val built: ExamBuildResult = examBuilder.build(
                config = config,
                questions = questions,
                wrongQuestionIds = wrongIds,
                answeredQuestionIds = answeredIds,
            )
            if (built.error != null) return QuizStartResult(false, built.error)
            if (built.questions.isEmpty()) return QuizStartResult(false, "没有可开始的题目")
            val startedAt = clock.nowMillis()
            val draftId = UUID.randomUUID().toString()
            val bookmarks = repository.getFavoriteIdsForBank(bankId)
            repository.createExamDraft(
                ExamDraftSnapshot(
                    draftId = draftId,
                    bankId = bankId,
                    bankName = bank.name,
                    config = config,
                    questions = built.questions,
                    answers = emptyMap(),
                    committedIds = emptySet(),
                    flaggedIds = emptySet(),
                    bookmarkedIds = bookmarks.intersect(built.questions.map { it.questionId }.toSet()),
                    currentIndex = 0,
                    startedAt = startedAt,
                    revision = 0,
                ),
            )
            startQuiz(
                bankId = bankId,
                bankName = bank.name,
                mode = PracticeMode.EXAM,
                questions = built.questions,
                config = config,
                bookmarkedIds = bookmarks,
                startIndex = 0,
                startedAt = startedAt,
                examDraftId = draftId,
            )
            QuizStartResult(true, warnings = built.warnings)
        }.getOrElse { error -> QuizStartResult(false, "无法开始模拟考试：${error.message}") }
    }

    suspend fun resumeExam(bankId: Long): QuizStartResult {
        return try {
            val draft = repository.getExamDraft(bankId)
                ?: return QuizStartResult(false, "未找到未完成的考试")
            startQuiz(
                bankId = draft.bankId,
                bankName = draft.bankName,
                mode = PracticeMode.EXAM,
                questions = draft.questions,
                config = draft.config,
                bookmarkedIds = draft.bookmarkedIds,
                flaggedIds = draft.flaggedIds,
                startIndex = draft.currentIndex,
                answers = draft.answers,
                committedIds = draft.committedIds,
                startedAt = draft.startedAt,
                examDraftId = draft.draftId,
                draftRevision = draft.revision,
            )
            QuizStartResult(true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            QuizStartResult(false, "无法继续考试：${error.message ?: "进度读取失败"}")
        }
    }

    suspend fun discardExamDraft(draftId: String): Boolean = try {
        repository.discardExamDraft(draftId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        _message.value = "无法放弃考试：${error.message ?: "请重试"}"
        false
    }

    private suspend fun attemptStatsFor(
        questions: List<QuizQuestion>,
    ): Map<Long, AttemptStats> = repository.statsFor(questions.map { it.questionId })
        .mapValues { (_, row) -> AttemptStats(row.totalCount, row.correctCount) }

    private fun startQuiz(
        bankId: Long?,
        bankName: String,
        mode: PracticeMode,
        questions: List<QuizQuestion>,
        config: ExamConfig,
        bookmarkedIds: Set<Long> = emptySet(),
        flaggedIds: Set<Long> = emptySet(),
        startIndex: Int = 0,
        answers: Map<Long, String> = emptyMap(),
        committedIds: Set<Long> = emptySet(),
        answerViewedIds: Set<Long> = emptySet(),
        attemptStats: Map<Long, AttemptStats> = emptyMap(),
        roundStartedAt: Long = 0,
        startedAt: Long = clock.nowMillis(),
        examDraftId: String? = null,
        draftRevision: Long = 0,
    ) {
        examCountdownJob?.cancel()
        examCountdownJob = null
        examNavigation.clear()
        examDraftRevision = draftRevision
        notifiedCommitted.clear()
        _quizState.value = QuizUiState(
            active = true,
            sessionToken = ++sessionTokenCounter,
            bankId = bankId,
            bankName = bankName,
            mode = mode,
            questions = questions,
            config = config,
            answers = answers,
            bookmarkedIds = bookmarkedIds.intersect(questions.map { it.questionId }.toSet()),
            flaggedIds = flaggedIds.intersect(questions.map { it.questionId }.toSet()),
            committedIds = committedIds,
            answerViewedIds = answerViewedIds,
            attemptStats = attemptStats,
            currentIndex = startIndex.coerceIn(0, questions.lastIndex.coerceAtLeast(0)),
            startedAt = startedAt,
            roundStartedAt = roundStartedAt,
            examDraftId = examDraftId,
        )
        scheduleExamCountdown(_quizState.value.sessionToken)
    }

    private fun scheduleExamCountdown(token: Long) {
        val initial = _quizState.value
        if (initial.mode != PracticeMode.EXAM || initial.config.durationMinutes <= 0) return
        examCountdownJob?.cancel()
        examCountdownJob = viewModelScope.launch {
            while (isActive) {
                val state = _quizState.value
                if (!state.active || state.sessionToken != token) break
                if (QuizCountdown.state(state.startedAt, state.config.durationMinutes, clock.nowMillis()).expired) {
                    submitQuiz(token, autoSubmitted = true)
                    break
                }
                delay(1000L)
            }
        }
    }

    private fun examDraftSnapshot(state: QuizUiState, revision: Long): ExamDraftSnapshot? {
        val draftId = state.examDraftId ?: return null
        val bankId = state.bankId ?: return null
        if (state.mode != PracticeMode.EXAM || !state.active) return null
        return ExamDraftSnapshot(
            draftId = draftId,
            bankId = bankId,
            bankName = state.bankName,
            config = state.config,
            questions = state.questions,
            answers = state.answers,
            committedIds = state.committedIds,
            flaggedIds = state.flaggedIds,
            bookmarkedIds = state.bookmarkedIds,
            currentIndex = state.currentIndex,
            startedAt = state.startedAt,
            revision = revision,
        )
    }

    private fun saveExamProgress() {
        val state = _quizState.value
        if (state.mode != PracticeMode.EXAM || !state.active) return
        val snapshot = examDraftSnapshot(state, ++examDraftRevision) ?: return
        examDraftWrites.trySend(PendingExamDraftWrite(state.sessionToken, snapshot))
    }

    private fun reportExamDraftSaveFailure(token: Long) {
        val state = _quizState.value
        if (state.active && state.sessionToken == token &&
            _submittingExamToken.value != token && leavingExamToken != token
        ) {
            _message.value = "考试进度保存失败，请重试"
        }
    }

    private fun examIsBusy(state: QuizUiState): Boolean =
        state.mode == PracticeMode.EXAM &&
            (_submittingExamToken.value == state.sessionToken || leavingExamToken == state.sessionToken)

    private fun submitIfExamExpired(state: QuizUiState): Boolean {
        if (state.mode != PracticeMode.EXAM || !state.active ||
            !QuizCountdown.state(state.startedAt, state.config.durationMinutes, clock.nowMillis()).expired
        ) return false
        submitQuiz(state.sessionToken, autoSubmitted = true)
        return true
    }

    suspend fun leaveExam(): Boolean {
        val state = _quizState.value
        if (!state.active || state.mode != PracticeMode.EXAM || examIsBusy(state)) return false
        val snapshot = examDraftSnapshot(state, ++examDraftRevision) ?: return false
        val token = state.sessionToken
        leavingExamToken = token
        examCountdownJob?.cancel()
        examCountdownJob = null
        var left = false
        try {
            if (!repository.saveExamDraft(snapshot)) error("考试进度已不存在")
            if (_quizState.value.sessionToken != token) return false
            _quizState.update { it.copy(active = false) }
            left = true
            return true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            _message.value = "考试进度保存失败：${error.message ?: "请重试"}"
            return false
        } finally {
            leavingExamToken = null
            if (!left && _quizState.value.sessionToken == token && _quizState.value.active) {
                scheduleExamCountdown(token)
            }
        }
    }

    fun isRevealed(questionId: Long): Boolean = _quizState.value.revealed(questionId)

    private var examPaperReviewVisible = false

    fun setExamPaperReviewVisible(visible: Boolean) {
        examPaperReviewVisible = visible
    }

    fun setReciting(reciting: Boolean) {
        _quizState.update { state ->
            if (!state.canSwitchRecite || examPaperReviewVisible || state.reciting == reciting) return@update state
            val answerState = AnswerState(state.answers, state.committedIds, state.answerViewedIds)
            val viewed = if (reciting) {
                state.currentQuestion?.let { QuizSession.viewAnswer(answerState, it.questionId) } ?: answerState
            } else answerState
            state.copy(reciting = reciting, answerViewedIds = viewed.answerViewedIds)
        }
    }

    fun needsConfirm(questionId: Long): Boolean {
        val state = _quizState.value
        val question = state.questions.firstOrNull { it.questionId == questionId }
            ?: return false
        return QuizSession.needsConfirm(question.type, state.mode, state.config)
    }

    fun canCommit(questionId: Long): Boolean {
        val state = _quizState.value
        if (state.reciting && state.canSwitchRecite) return false
        return QuizSession.canCommit(
            state = AnswerState(state.answers, state.committedIds, state.answerViewedIds),
            questionId = questionId,
            mode = state.mode,
            config = state.config,
        )
    }

    fun attemptStatsFor(questionId: Long): AttemptStats =
        _quizState.value.attemptStats[questionId] ?: AttemptStats()

    fun selectAnswer(questionId: Long, rawAnswer: String) {
        val state = _quizState.value
        if (state.reciting && state.canSwitchRecite) return
        if (examIsBusy(state) || submitIfExamExpired(state)) return
        val question = state.questions.firstOrNull { it.questionId == questionId } ?: return
        val before = AnswerState(state.answers, state.committedIds, state.answerViewedIds)
        if (QuizSession.isRevealed(before, questionId, state.mode, state.config)) {
            notifyCommittedOnce(questionId)
            return
        }
        val after = QuizSession.select(
            state = before,
            questionId = questionId,
            type = question.type,
            rawAnswer = rawAnswer,
            mode = state.mode,
            config = state.config,
        )
        _quizState.update { it.copy(answers = after.answers, committedIds = after.committedIds) }
        saveExamProgress()
        if (questionId in after.committedIds && questionId !in before.committedIds) {
            onCommitted(state, question, after.answers[questionId].orEmpty())
        }
    }

    fun setFillAnswer(questionId: Long, text: String) {
        val state = _quizState.value
        if (state.reciting && state.canSwitchRecite) return
        if (examIsBusy(state) || submitIfExamExpired(state)) return
        val before = AnswerState(state.answers, state.committedIds, state.answerViewedIds)
        if (QuizSession.isRevealed(before, questionId, state.mode, state.config)) {
            notifyCommittedOnce(questionId)
            return
        }
        val after = QuizSession.setText(before, questionId, text, state.mode, state.config)
        _quizState.update { it.copy(answers = after.answers) }
        saveExamProgress()
    }

    fun commitAnswer(questionId: Long) {
        val state = _quizState.value
        if (state.reciting && state.canSwitchRecite) return
        if (examIsBusy(state) || submitIfExamExpired(state)) return
        val question = state.questions.firstOrNull { it.questionId == questionId } ?: return
        val before = AnswerState(state.answers, state.committedIds, state.answerViewedIds)
        val after = QuizSession.commit(before, questionId, state.mode, state.config) ?: return
        _quizState.update { it.copy(answers = after.answers, committedIds = after.committedIds) }
        saveExamProgress()
        onCommitted(state, question, after.answers[questionId].orEmpty())
    }

    fun retryQuestion(questionId: Long) {
        val state = _quizState.value
        if (state.mode == PracticeMode.EXAM) return
        val after = QuizSession.retry(AnswerState(state.answers, state.committedIds, state.answerViewedIds), questionId)
        _quizState.update { it.copy(answers = after.answers, committedIds = after.committedIds, answerViewedIds = after.answerViewedIds) }
        notifiedCommitted.remove(questionId)
    }

    fun replaceQuestionInSession(question: Question) {
        _quizState.update { state ->
            if (state.mode == PracticeMode.EXAM) return@update state
            val index = state.questions.indexOfFirst { it.questionId == question.id }
            if (index < 0) return@update state
            val old = state.questions[index]
            val replacement = question.toQuizQuestion(old.points).copy(labelMap = old.labelMap)
            val answerChanged =
                AnswerNormalizer.normalizeForStorage(replacement.type, replacement.correctAnswer) !=
                    AnswerNormalizer.normalizeForStorage(old.type, old.correctAnswer)
            val questions = state.questions.toMutableList().also { it[index] = replacement }
            state.copy(
                questions = questions,
                answers = if (answerChanged) state.answers - question.id else state.answers,
                committedIds = if (answerChanged) state.committedIds - question.id else state.committedIds,
                answerViewedIds = when {
                    answerChanged && state.reciting && state.currentQuestion?.questionId == question.id -> state.answerViewedIds + question.id
                    answerChanged -> state.answerViewedIds - question.id
                    else -> state.answerViewedIds
                },
            )
        }
        saveExamProgress()
    }

    fun pastPapersForBank(bankId: Long) = repository.observePastPapers(bankId)
    fun pastPaperItems(paperId: Long) = repository.observePastPaperItems(paperId)
    suspend fun createPastPaper(bankId: Long, year: Int, name: String, source: PastPaperSource, note: String?) =
        repository.createPastPaper(bankId, year, name, source, note)
    suspend fun updatePastPaper(paperId: Long, year: Int, name: String, source: PastPaperSource, note: String?) =
        repository.updatePastPaper(paperId, year, name, source, note)
    suspend fun deletePastPaper(paperId: Long) = repository.deletePastPaper(paperId)
    suspend fun addQuestionsToPastPaper(paperId: Long, ids: List<Long>) = repository.addQuestionsToPastPaper(paperId, ids)
    suspend fun movePastPaperItem(paperId: Long, questionId: Long, direction: Int) = repository.movePastPaperItem(paperId, questionId, direction)
    suspend fun removePastPaperItem(paperId: Long, questionId: Long) = repository.removePastPaperItem(paperId, questionId)
    suspend fun updatePastPaperNumber(paperId: Long, questionId: Long, number: String) = repository.updatePastPaperNumber(paperId, questionId, number)

    fun chaptersForBank(bankId: Long) = repository.observeChapters(bankId)
    fun questionsForBank(bankId: Long) = repository.observableQuestionsByBank(bankId)
    suspend fun getChapters(bankId: Long) = repository.getChapters(bankId)
    suspend fun createChapter(bankId: Long, name: String) = repository.createChapter(bankId, name)
    suspend fun renameChapter(chapterId: Long, name: String) = repository.renameChapter(chapterId, name)
    suspend fun deleteChapter(chapterId: Long) = repository.deleteChapter(chapterId)
    suspend fun moveChapter(chapterId: Long, direction: Int) = repository.moveChapter(chapterId, direction)
    suspend fun moveQuestions(bankId: Long, ids: Set<Long>, chapterId: Long?) = repository.moveQuestions(bankId, ids, chapterId)

    suspend fun questionForEdit(questionId: Long): Question? = withContext(Dispatchers.IO) {
        repository.getQuestion(questionId)
    }

    suspend fun saveQuestionEdit(question: Question): Boolean {
        if (question.stem.isBlank()) return false
        if ((question.type == QuestionType.SINGLE || question.type == QuestionType.MULTI) &&
            question.options.isEmpty()
        ) return false
        val normalized = question.copy(
            answer = AnswerNormalizer.normalizeForStorage(question.type, question.answer),
            updatedAt = clock.nowMillis(),
        )
        return withContext(Dispatchers.IO) {
            runCatching { repository.saveQuestion(normalized) }
                .onSuccess { replaceQuestionInSession(normalized) }
                .isSuccess
        }
    }

    fun leaveQuiz() {
        examCountdownJob?.cancel()
        examCountdownJob = null
        val state = _quizState.value
        val bankId = state.bankId
        if (state.mode == PracticeMode.SEQUENCE && bankId != null) {
            viewModelScope.launch {
                runCatching {
                    repository.saveSequencePosition(bankId, state.currentIndex, clock.nowMillis())
                }
            }
        }
        _quizState.update { it.copy(active = false) }
    }

    suspend fun sequenceRoundComplete(bankId: Long): Boolean {
        val progress = repository.getSequenceProgress(bankId) ?: return false
        val total = repository.getQuestions(bankId).size
        return total > 0 && progress.answeredCount >= total
    }

    suspend fun restartSequenceRound(bankId: Long) {
        runCatching {
            repository.startNewRound(bankId, clock.nowMillis())
            showMessage("已开始新一轮练习")
        }
    }

    fun countdownState(): CountdownState {
        val state = _quizState.value
        return QuizCountdown.state(state.startedAt, state.config.durationMinutes, clock.nowMillis())
    }

    private fun notifyCommittedOnce(questionId: Long) {
        if (notifiedCommitted.add(questionId)) {
            _message.value = "本题已作答"
        }
    }

    private fun onCommitted(state: QuizUiState, question: QuizQuestion, answer: String) {
        val isCorrect = Grader.gradeQuestion(
            question = question,
            answer = answer,
            multiSelectRule = state.config.multiSelectRule,
        ).outcome == QuestionOutcome.CORRECT
        _quizState.update { current ->
            val stats = current.attemptStats[question.questionId] ?: AttemptStats()
            current.copy(
                attemptStats = current.attemptStats + (
                    question.questionId to AttemptStats(
                        total = stats.total + 1,
                        correct = stats.correct + if (isCorrect && question.questionId !in state.answerViewedIds) 1 else 0,
                    )
                    ),
            )
        }
        if (state.mode == PracticeMode.EXAM) return
        val commit = PracticeCommit(
            bankId = state.bankId ?: question.bankId,
            mode = state.mode,
            questionId = question.questionId,
            questionBankId = question.bankId,
            answer = QuizSession.mapAnswerToOriginal(answer, question.labelMap),
            isCorrect = isCorrect,
            currentIndex = state.currentIndex,
            answeredAt = clock.nowMillis(),
            roundStartedAt = state.roundStartedAt,
            answerViewed = question.questionId in state.answerViewedIds,
        )
        viewModelScope.launch {
            runCatching { repository.commitPracticeAnswer(commit) }
        }
    }

    fun toggleCurrentFlag() {
        val current = _quizState.value
        if (examIsBusy(current)) return
        val questionId = current.currentQuestion?.questionId ?: return
        _quizState.update { state ->
            val updated = if (questionId in state.flaggedIds) {
                state.flaggedIds - questionId
            } else {
                state.flaggedIds + questionId
            }
            state.copy(flaggedIds = updated)
        }
        saveExamProgress()
    }

    fun toggleCurrentBookmark() {
        val current = _quizState.value
        if (examIsBusy(current)) return
        val questionId = current.currentQuestion?.questionId ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val isFavorite = repository.toggleFavorite(questionId, now)
            _quizState.update { state ->
                if (state.sessionToken != current.sessionToken || examIsBusy(state)) return@update state
                state.copy(
                    bookmarkedIds = if (isFavorite) {
                        state.bookmarkedIds + questionId
                    } else {
                        state.bookmarkedIds - questionId
                    },
                )
            }
            if (_quizState.value.sessionToken == current.sessionToken) saveExamProgress()
            _message.value = if (isFavorite) "已收藏" else "已取消收藏"
        }
    }

    suspend fun loadNote(questionId: Long): String =
        repository.getNote(questionId).orEmpty()

    suspend fun saveNote(questionId: Long, content: String): Boolean =
        runCatching {
            repository.saveNote(questionId, content, System.currentTimeMillis())
            _message.value = if (content.isBlank()) "笔记已删除" else "笔记已保存"
            true
        }.getOrElse {
            _message.value = "笔记保存失败：${it.message}"
            false
        }

    fun goToQuestion(index: Int, fromPager: Boolean = false) {
        val state = _quizState.value
        if (examIsBusy(state)) return
        if (state.questions.isEmpty()) return
        val target = index.coerceIn(0, state.questions.lastIndex)
        if (target == state.currentIndex) return
        _quizState.update { current ->
            val answerState = AnswerState(current.answers, current.committedIds, current.answerViewedIds)
            val viewed = if (current.canSwitchRecite && current.reciting) {
                QuizSession.viewAnswer(answerState, current.questions[target].questionId)
            } else answerState
            current.copy(currentIndex = target, answerViewedIds = viewed.answerViewedIds)
        }
        saveExamProgress()
        persistSequenceProgress()
        if (!fromPager) _pagerJump.update { it + 1 }
    }

    fun nextQuestion() {
        val state = _quizState.value
        if (state.currentIndex < state.questions.lastIndex) {
            goToQuestion(state.currentIndex + 1)
        }
    }

    fun previousQuestion() {
        val state = _quizState.value
        if (state.currentIndex > 0) {
            goToQuestion(state.currentIndex - 1)
        }
    }

    private fun persistSequenceProgress() {
        val state = _quizState.value
        val bankId = state.bankId ?: return
        if (state.mode != PracticeMode.SEQUENCE) return
        viewModelScope.launch {
            runCatching {
                repository.saveSequencePosition(bankId, state.currentIndex, clock.nowMillis())
            }
        }
    }

    fun submitQuiz(sessionToken: Long, autoSubmitted: Boolean = false) {
        val state = _quizState.value
        if (!state.active || state.sessionToken != sessionToken ||
            state.mode != PracticeMode.EXAM || state.questions.isEmpty() || leavingExamToken == sessionToken
        ) return
        val draftId = state.examDraftId ?: run {
            _message.value = "交卷失败：考试进度不存在"
            return
        }

        _submittingExamToken.value = sessionToken
        examSubmissions.submit(sessionToken) { markCommitted ->
            val current = _quizState.value
            if (!current.active || current.sessionToken != sessionToken) return@submit
            try {
                val now = clock.nowMillis()
                val expired = QuizCountdown.state(state.startedAt, state.config.durationMinutes, now).expired
                val submittedAutomatically = autoSubmitted || expired
                val elapsedMillis = (now - state.startedAt).coerceAtLeast(0L)
                val durationMillis = if (state.config.durationMinutes > 0) {
                    elapsedMillis.coerceAtMost(state.config.durationMinutes.toLong() * 60_000L)
                } else elapsedMillis
                val grading = Grader.grade(state.questions, state.answers, state.config)
                val records = state.questions.mapNotNull { question ->
                    val answer = state.answers[question.questionId]?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    ExamRecord(
                        questionId = question.questionId,
                        bankId = question.bankId,
                        answer = QuizSession.mapAnswerToOriginal(answer, question.labelMap),
                        isCorrect = grading.outcomes[question.questionId]?.outcome == QuestionOutcome.CORRECT,
                    )
                }
                withContext(NonCancellable) {
                    val sessionId = repository.submitExamSession(
                        SessionSnapshot(
                            bankId = state.bankId,
                            bankName = state.bankName,
                            mode = state.mode,
                            config = state.config,
                            questions = state.questions,
                            answers = state.answers,
                            flaggedIds = state.flaggedIds,
                            bookmarkedIds = state.bookmarkedIds,
                            startedAt = state.startedAt,
                            endedAt = now,
                            grading = grading,
                        ),
                        records,
                        draftId,
                    )
                    markCommitted()
                    if (_quizState.value.sessionToken == sessionToken) {
                        _lastResult.value = ResultUiState(
                            sessionId = sessionId,
                            grading = grading,
                            bankId = state.bankId,
                            bankName = state.bankName,
                            mode = state.mode,
                            durationSeconds = durationMillis / 1000,
                            config = state.config,
                            questions = state.questions,
                            answers = state.answers,
                            autoSubmitted = submittedAutomatically,
                        )
                        _quizState.update { it.copy(active = false, autoSubmitted = submittedAutomatically) }
                        examNavigation.publish(sessionToken)
                        examCountdownJob?.cancel()
                        examCountdownJob = null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (_quizState.value.sessionToken == sessionToken) {
                    _message.value = "交卷失败：${error.message ?: "未知错误"}"
                }
            }
        }.invokeOnCompletion { _submittingExamToken.compareAndSet(sessionToken, null) }
    }

    fun clearLastResult() {
        _lastResult.value = null
    }

    private val _examHistory = MutableStateFlow(ExamHistoryState())
    val examHistory: StateFlow<ExamHistoryState> = _examHistory.asStateFlow()

    private val _examHistoryDetail = MutableStateFlow(ExamHistoryDetailState())
    val examHistoryDetail: StateFlow<ExamHistoryDetailState> = _examHistoryDetail.asStateFlow()

    fun refreshExamHistory() {
        if (_examHistory.value.loading) return
        _examHistory.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            runCatching { repository.getExamHistory() }.fold(
                onSuccess = { entries -> _examHistory.value = ExamHistoryState(entries = entries) },
                onFailure = { _examHistory.update { state -> state.copy(loading = false, failed = true) } },
            )
        }
    }

    fun loadExamHistoryDetail(sessionId: Long) {
        val current = _examHistoryDetail.value
        if (current.sessionId == sessionId && current.loading) return
        _examHistoryDetail.value = ExamHistoryDetailState(
            sessionId = sessionId,
            loading = true,
            detail = current.detail?.takeIf { current.sessionId == sessionId },
        )
        viewModelScope.launch {
            val state = runCatching { repository.getExamHistoryDetail(sessionId) }.fold(
                onSuccess = { detail ->
                    ExamHistoryDetailState(
                        sessionId = sessionId,
                        detail = detail,
                    )
                },
                onFailure = { ExamHistoryDetailState(sessionId = sessionId, failed = true) },
            )
            if (_examHistoryDetail.value.sessionId == sessionId) {
                _examHistoryDetail.value = state
            }
        }
    }

    private val _examPaperReview = MutableStateFlow(ExamPaperReviewState())
    val examPaperReview: StateFlow<ExamPaperReviewState> = _examPaperReview.asStateFlow()

    // 原卷回看：加载结果只在本场次的请求仍有效时落盘，
    // 快速切场不会闪出上一场的题；快照损坏落入 failed，显示「试卷读取失败」。
    fun loadExamPaperReview(sessionId: Long) {
        val current = _examPaperReview.value
        if (current.sessionId == sessionId && current.loading) return
        _examPaperReview.value = ExamPaperReviewState(
            sessionId = sessionId,
            loading = true,
            review = current.review?.takeIf { current.sessionId == sessionId },
        )
        viewModelScope.launch {
            val state = runCatching { repository.getExamPaperReview(sessionId) }.fold(
                onSuccess = { review ->
                    ExamPaperReviewState(sessionId = sessionId, review = review)
                },
                onFailure = { ExamPaperReviewState(sessionId = sessionId, failed = true) },
            )
            if (_examPaperReview.value.sessionId == sessionId) {
                _examPaperReview.value = state
            }
        }
    }

    fun outcomeFor(questionId: Long): QuestionOutcome {
        val state = _quizState.value
        val question = state.questions.firstOrNull { it.questionId == questionId }
            ?: return QuestionOutcome.UNANSWERED
        return Grader.gradeQuestion(
            question = question,
            answer = state.answers[questionId],
            multiSelectRule = state.config.multiSelectRule,
        ).outcome
    }

    fun gradeCurrent(): GradingResult? {
        val state = _quizState.value
        if (state.questions.isEmpty()) return null
        return Grader.grade(state.questions, state.answers, state.config)
    }

    suspend fun loadFlashcards(bankId: Long?) = repository.flashcardSnapshot(bankId)

    suspend fun reviewFlashcard(questionId: Long, remembered: Boolean, bankId: Long?) =
        repository.reviewFlashcardAndSnapshot(questionId, remembered, clock, flashcardZone, bankId)

    fun flashcardQueue(snapshot: LintiRepository.FlashcardSnapshot, limit: Int?, tail: List<Long>) =
        FlashcardSchedule.queue(snapshot.questions, snapshot.reviews, limit, clock, flashcardZone, tail)

    fun flashcardDueText(timestamp: Long): String = java.time.Instant.ofEpochMilli(timestamp)
        .atZone(flashcardZone).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))

    suspend fun loadBankQuestionCollection(bankId: Long?) {
        _questionCollection.value = QuestionCollectionState(
            bankId = bankId,
            title = if (bankId == null) "全部题目" else "题库题目",
            loading = true,
        )
        val bank = bankId?.let { repository.getBank(it) }
        val questions = if (bankId == null) {
            repository.getAllQuestions()
        } else {
            repository.getQuestions(bankId)
        }
        _questionCollection.value = QuestionCollectionState(
            bankId = bankId,
            title = bank?.name ?: "全部题目",
            questions = questions,
            loading = false,
        )
    }

    suspend fun searchQuestionCollection(bankId: Long?, query: String) {
        _questionCollection.value = QuestionCollectionState(
            bankId = bankId,
            title = "搜索题目",
            loading = true,
        )
        val bank = bankId?.let { repository.getBank(it) }
        val questions = repository.searchQuestions(bankId, query)
        _questionCollection.value = QuestionCollectionState(
            bankId = bankId,
            title = if (query.isBlank()) {
                bank?.name ?: "全部题目"
            } else {
                "搜索「${query.trim()}」"
            },
            questions = questions,
            loading = false,
        )
    }

    suspend fun studySummary(): StudySummary {
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val aggregate = repository.studyAggregate(todayStart, zone)
        val pendingWrong = repository.getWrongRows().count { it.wrongItem.pendingReview }
        val totalQuestions = repository.getAllQuestions().size
        return StudySummary(
            totalAnswered = aggregate.totalAnswered,
            todayAnswered = aggregate.todayAnswered,
            accuracy = aggregate.accuracy,
            pendingWrong = pendingWrong,
            totalQuestions = totalQuestions,
            sessionCount = aggregate.sessionCount,
            streakDays = aggregate.streakDays,
        )
    }

    fun saveAiSettings(
        provider: AiProvider,
        apiKey: String?,
        baseUrl: String,
        model: String,
    ) {
        container.settingsStore.saveAiConfiguration(
            provider = provider,
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
        )
        _settings.value = container.settingsStore.snapshot()
        _message.value = if (container.settingsStore.apiKeyForRequest().isBlank()) {
            "设置已保存；尚未填写 API Key"
        } else {
            "AI 设置已保存在本机"
        }
    }

    fun clearApiKey() {
        container.settingsStore.clearApiKey()
        _settings.value = container.settingsStore.snapshot()
        _message.value = "API Key 已清除"
    }

    fun setAiExplanationReasoningEffort(effort: AiExplanationReasoningEffort) {
        container.settingsStore.setAiExplanationReasoningEffort(effort)
        _settings.value = container.settingsStore.snapshot()
    }

    fun setLocalFirst(enabled: Boolean) {
        container.settingsStore.setLocalFirst(enabled)
        _settings.value = container.settingsStore.snapshot()
    }

    fun setCallAiOnlyWhenLocalFails(enabled: Boolean) {
        container.settingsStore.setCallAiOnlyWhenLocalFails(enabled)
        _settings.value = container.settingsStore.snapshot()
    }

    suspend fun exportBackupJson(): String = repository.exportBackupJson()

    suspend fun restoreBackupJson(json: String): Boolean = runCatching {
        repository.restoreBackupJson(json)
        _message.value = "备份已恢复"
        true
    }.getOrElse { error ->
        _message.value = "备份恢复失败：${error.message ?: "文件格式不正确"}"
        false
    }

    fun loadSampleData() {
        viewModelScope.launch {
            val bankId = runCatching { repository.loadSampleDataIfMissing() }.getOrNull()
            _message.value = if (bankId != null) "示例题库已载入" else "示例题库已存在"
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = getApplication<Application>().contentResolver
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
    }

    private fun Question.toQuizQuestion(points: Double): QuizQuestion = QuizQuestion(
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
        labelMap = emptyMap(),
    )

    class Factory(
        private val application: Application,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(LintiViewModel::class.java))
            return LintiViewModel(application, container) as T
        }
    }
}
