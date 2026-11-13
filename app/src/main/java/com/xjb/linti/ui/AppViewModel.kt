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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

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

    private val _settings = MutableStateFlow(container.settingsStore.snapshot())
    val settings: StateFlow<SettingsSnapshot> = _settings.asStateFlow()

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _quizState = MutableStateFlow(QuizUiState())
    val quizState: StateFlow<QuizUiState> = _quizState.asStateFlow()

    private val _lastResult = MutableStateFlow<ResultUiState?>(null)
    val lastResult: StateFlow<ResultUiState?> = _lastResult.asStateFlow()

    private val _questionCollection = MutableStateFlow<QuestionCollectionState?>(null)
    val questionCollection: StateFlow<QuestionCollectionState?> = _questionCollection.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

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
        val available = QuestionType.entries.associateWith { type ->
            questions.count { it.type == type }
        }
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
            initial.copy(passScore = if (total > 0) (total * 0.6).toInt().coerceAtLeast(1).toDouble() else 0.0)
        }
        return ExamSetupData(available = available, config = config)
    }

    suspend fun startExam(bankId: Long, config: ExamConfig): QuizStartResult {
        return runCatching {
            val bank = repository.getBank(bankId)
                ?: return QuizStartResult(false, "题库不存在")
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
            startQuiz(
                bankId = bankId,
                bankName = bank.name,
                mode = PracticeMode.EXAM,
                questions = built.questions,
                config = config,
                bookmarkedIds = repository.getFavoriteIdsForBank(bankId),
                startIndex = 0,
            )
            QuizStartResult(true, warnings = built.warnings)
        }.getOrElse { error -> QuizStartResult(false, "无法开始模拟考试：${error.message}") }
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
        startIndex: Int = 0,
        answers: Map<Long, String> = emptyMap(),
        committedIds: Set<Long> = emptySet(),
        attemptStats: Map<Long, AttemptStats> = emptyMap(),
        roundStartedAt: Long = 0,
    ) {
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
            committedIds = committedIds,
            attemptStats = attemptStats,
            currentIndex = startIndex.coerceIn(0, questions.lastIndex.coerceAtLeast(0)),
            startedAt = clock.nowMillis(),
            roundStartedAt = roundStartedAt,
        )
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
        if (questionId in after.committedIds && questionId !in before.committedIds) {
            onCommitted(state, question, after.answers[questionId].orEmpty())
        }
    }

    fun setFillAnswer(questionId: Long, text: String) {
        val state = _quizState.value
        val before = AnswerState(state.answers, state.committedIds)
        if (QuizSession.isRevealed(before, questionId, state.mode, state.config)) {
            notifyCommittedOnce(questionId)
            return
        }
        val after = QuizSession.setText(before, questionId, text, state.mode, state.config)
        _quizState.update { it.copy(answers = after.answers) }
    }

    fun commitAnswer(questionId: Long) {
        val state = _quizState.value
        val question = state.questions.firstOrNull { it.questionId == questionId } ?: return
        val before = AnswerState(state.answers, state.committedIds)
        val after = QuizSession.commit(before, questionId, state.mode, state.config) ?: return
        _quizState.update { it.copy(answers = after.answers, committedIds = after.committedIds) }
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

    suspend fun autoSubmitIfExpired(): Boolean {
        val state = _quizState.value
        if (!state.active || state.mode != PracticeMode.EXAM || !countdownState().expired) return false
        return submitQuiz(autoSubmitted = true)
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
        val questionId = _quizState.value.currentQuestion?.questionId ?: return
        _quizState.update { state ->
            val updated = if (questionId in state.flaggedIds) {
                state.flaggedIds - questionId
            } else {
                state.flaggedIds + questionId
            }
            state.copy(flaggedIds = updated)
        }
    }

    fun toggleCurrentBookmark() {
        val questionId = _quizState.value.currentQuestion?.questionId ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val isFavorite = repository.toggleFavorite(questionId, now)
            _quizState.update { state ->
                state.copy(
                    bookmarkedIds = if (isFavorite) {
                        state.bookmarkedIds + questionId
                    } else {
                        state.bookmarkedIds - questionId
                    },
                )
            }
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
        if (state.questions.isEmpty()) return
        val target = index.coerceIn(0, state.questions.lastIndex)
        if (target == state.currentIndex) return
        _quizState.update { it.copy(currentIndex = target) }
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

    suspend fun submitQuiz(autoSubmitted: Boolean = false): Boolean {
        val state = _quizState.value
        if (!state.active || state.questions.isEmpty()) return false
        if (state.mode != PracticeMode.EXAM) {
            leaveQuiz()
            return false
        }
        return runCatching {
            val now = clock.nowMillis()
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
            )
            _lastResult.value = ResultUiState(
                sessionId = sessionId,
                grading = grading,
                bankId = state.bankId,
                bankName = state.bankName,
                mode = state.mode,
                durationSeconds = ((now - state.startedAt) / 1000).coerceAtLeast(0),
                config = state.config,
                questions = state.questions,
                answers = state.answers,
                autoSubmitted = autoSubmitted,
            )
            _quizState.update { it.copy(active = false, autoSubmitted = autoSubmitted) }
            true
        }.getOrElse { error ->
            _message.value = "交卷失败：${error.message ?: "未知错误"}"
            false
        }
    }

    fun clearLastResult() {
        _lastResult.value = null
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
