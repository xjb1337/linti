package com.xjb.linti.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.import.ConfirmImportResult
import com.xjb.linti.data.import.ConfirmTarget
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.data.import.ImportRun
import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.data.prefs.SettingsSnapshot
import com.xjb.linti.data.db.SessionBackfill
import com.xjb.linti.data.db.ExamDraftSnapshot
import com.xjb.linti.data.repo.ExamDraftSummary
import com.xjb.linti.data.repo.ExamRecord
import com.xjb.linti.data.repo.PracticeCommit
import com.xjb.linti.data.repo.SessionSnapshot
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.AnswerState
import com.xjb.linti.domain.AttemptStats
import com.xjb.linti.domain.BankSummary
import com.xjb.linti.domain.CountdownState
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ExamBuilder
import com.xjb.linti.domain.ExamBuildResult
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.ExamEligibility
import com.xjb.linti.domain.ExamAccuracyPoint
import com.xjb.linti.domain.ExamHistoryDetail
import com.xjb.linti.domain.ExamHistoryEntry
import com.xjb.linti.domain.recentExamAccuracyPoints
import com.xjb.linti.domain.ExamNavigationEvent
import com.xjb.linti.domain.ExamSubmissionCoordinator
import com.xjb.linti.domain.FavoriteQuestionItem
import com.xjb.linti.domain.FontScale
import com.xjb.linti.domain.Grader
import com.xjb.linti.domain.GradingResult
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.NoteQuestionItem
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizClock
import com.xjb.linti.domain.QuizCountdown
import com.xjb.linti.domain.ReadingMode
import com.xjb.linti.domain.QuizQuestion
import com.xjb.linti.domain.QuizSession
import com.xjb.linti.domain.SystemQuizClock
import com.xjb.linti.domain.WrongQuestionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
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
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

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
) {
    val recognizedCount: Int get() = drafts.size
    val pendingCount: Int get() = drafts.count { it.needsReview }
    val confirmedCount: Int get() = drafts.count { !it.needsReview }
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

    fun revealed(questionId: Long): Boolean =
        QuizUiStateRules.isRevealed(committedIds, questionId, mode, config)
}

private object QuizUiStateRules {
    fun isRevealed(
        committedIds: Set<Long>,
        questionId: Long,
        mode: PracticeMode,
        config: ExamConfig,
    ): Boolean = QuizSession.isRevealed(
        state = AnswerState(committedIds = committedIds),
        questionId = questionId,
        mode = mode,
        config = config,
    )
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

class LintiViewModel(
    application: Application,
    private val container: AppContainer,
    private val clock: QuizClock = SystemQuizClock,
) : AndroidViewModel(application) {
    private val repository = container.repository
    private val examBuilder = ExamBuilder()
    private val examSubmissions = ExamSubmissionCoordinator(viewModelScope)
    private val examNavigation = ExamNavigationEvent()
    private var examCountdownJob: Job? = null
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
    }

    private val _pagerJump = MutableStateFlow(0L)
    val pagerJump: StateFlow<Long> = _pagerJump.asStateFlow()

    val fontScale: StateFlow<FontScale> = container.quizPreferences.fontScale
    val readingMode: StateFlow<ReadingMode> = container.quizPreferences.readingMode
    val autoNextOnCorrect: StateFlow<Boolean> = container.quizPreferences.autoNextOnCorrect

    fun setFontScale(value: FontScale) = container.quizPreferences.setFontScale(value)

    fun setReadingMode(value: ReadingMode) = container.quizPreferences.setReadingMode(value)

    fun setAutoNextOnCorrect(value: Boolean) = container.quizPreferences.setAutoNextOnCorrect(value)

    suspend fun importFromUri(
        uri: Uri,
        kind: DocumentKind?,
        method: ImportMethod,
    ): Boolean {
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
            state.copy(drafts = state.drafts.toMutableList().also { it[index] = draft })
        }
    }

    fun updateImportTargetBank(bankId: Long?) {
        _importState.update { it.copy(targetBankId = bankId) }
    }

    fun updateImportNewBankName(name: String) {
        _importState.update { it.copy(newBankName = name) }
    }

    suspend fun confirmImport(): ConfirmImportResult {
        val state = _importState.value
        _importState.update { it.copy(isConfirming = true, error = null) }
        val result = container.confirmImportUseCase.execute(
            drafts = state.drafts,
            target = ConfirmTarget(
                bankId = state.targetBankId,
                newBankName = state.newBankName,
            ),
            sourceFileName = state.fileName,
            importJobId = state.jobId,
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
        _importState.value = ImportUiState()
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

    suspend fun startWrongQuiz(
        bankId: Long? = null,
        questionId: Long? = null,
    ): QuizStartResult {
        return runCatching {
            val rows = repository.getWrongRows().filter { item ->
                (bankId == null || item.wrongItem.bankId == bankId) &&
                    (questionId == null || item.wrongItem.questionId == questionId)
            }
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

    fun isRevealed(questionId: Long): Boolean {
        val state = _quizState.value
        return QuizSession.isRevealed(
            state = AnswerState(state.answers, state.committedIds),
            questionId = questionId,
            mode = state.mode,
            config = state.config,
        )
    }

    fun needsConfirm(questionId: Long): Boolean {
        val state = _quizState.value
        val question = state.questions.firstOrNull { it.questionId == questionId }
            ?: return false
        return QuizSession.needsConfirm(question.type, state.mode, state.config)
    }

    fun canCommit(questionId: Long): Boolean {
        val state = _quizState.value
        return QuizSession.canCommit(
            state = AnswerState(state.answers, state.committedIds),
            questionId = questionId,
            mode = state.mode,
            config = state.config,
        )
    }

    fun attemptStatsFor(questionId: Long): AttemptStats =
        _quizState.value.attemptStats[questionId] ?: AttemptStats()

    fun selectAnswer(questionId: Long, rawAnswer: String) {
        val state = _quizState.value
        if (examIsBusy(state) || submitIfExamExpired(state)) return
        val question = state.questions.firstOrNull { it.questionId == questionId } ?: return
        val before = AnswerState(state.answers, state.committedIds)
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
        if (examIsBusy(state) || submitIfExamExpired(state)) return
        val before = AnswerState(state.answers, state.committedIds)
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
        if (examIsBusy(state) || submitIfExamExpired(state)) return
        val question = state.questions.firstOrNull { it.questionId == questionId } ?: return
        val before = AnswerState(state.answers, state.committedIds)
        val after = QuizSession.commit(before, questionId, state.mode, state.config) ?: return
        _quizState.update { it.copy(answers = after.answers, committedIds = after.committedIds) }
        saveExamProgress()
        onCommitted(state, question, after.answers[questionId].orEmpty())
    }

    fun retryQuestion(questionId: Long) {
        val state = _quizState.value
        if (state.mode == PracticeMode.EXAM) return
        val after = QuizSession.retry(AnswerState(state.answers, state.committedIds), questionId)
        _quizState.update { it.copy(answers = after.answers, committedIds = after.committedIds) }
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
            )
        }
        saveExamProgress()
    }

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
            _message.value = "本题已作答，可在设置里重练本题"
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
                        correct = stats.correct + if (isCorrect) 1 else 0,
                    )
                    ),
            )
        }
        if (state.mode == PracticeMode.EXAM) return
        viewModelScope.launch {
            runCatching {
                repository.commitPracticeAnswer(
                    PracticeCommit(
                        bankId = state.bankId ?: question.bankId,
                        mode = state.mode,
                        questionId = question.questionId,
                        questionBankId = question.bankId,
                        answer = QuizSession.mapAnswerToOriginal(answer, question.labelMap),
                        isCorrect = isCorrect,
                        currentIndex = state.currentIndex,
                        answeredAt = clock.nowMillis(),
                        roundStartedAt = state.roundStartedAt,
                    ),
                )
            }
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
        _quizState.update { it.copy(currentIndex = target) }
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
