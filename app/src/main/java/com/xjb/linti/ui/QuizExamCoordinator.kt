package com.xjb.linti.ui

import com.xjb.linti.data.db.SessionBackfill
import com.xjb.linti.data.db.ExamDraftSnapshot
import com.xjb.linti.data.prefs.PracticeSessionStore
import com.xjb.linti.data.repo.ExamRecord
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.data.repo.PracticeCommit
import com.xjb.linti.data.repo.SessionSnapshot
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.AnswerState
import com.xjb.linti.domain.AttemptStats
import com.xjb.linti.domain.CountdownState
import com.xjb.linti.domain.EssayAnswer
import com.xjb.linti.domain.ExamBuildResult
import com.xjb.linti.domain.ExamBuilder
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.ExamEligibility
import com.xjb.linti.domain.ExamNavigationEvent
import com.xjb.linti.domain.ExamSubmissionCoordinator
import com.xjb.linti.domain.Grader
import com.xjb.linti.domain.GradingResult
import com.xjb.linti.domain.MultiSelectRule
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.PracticeSessionDescriptor
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizClock
import com.xjb.linti.domain.QuizCountdown
import com.xjb.linti.domain.QuizQuestion
import com.xjb.linti.domain.QuizSession
import com.xjb.linti.domain.TopicDrawResult
import com.xjb.linti.domain.TopicPractice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/** 练题、模拟考试、倒计时、交卷与历史记录协调器。 */
internal class QuizExamCoordinator(
    private val repository: LintiRepository,
    private val practiceSessionStore: PracticeSessionStore,
    private val clock: QuizClock,
    private val flashcardZone: ZoneId,
    private val scope: CoroutineScope,
    private val onMessage: (String) -> Unit,
) {
    private val examBuilder = ExamBuilder()
    private val examSubmissions = ExamSubmissionCoordinator(scope)
    private val examNavigation = ExamNavigationEvent()
    private var examCountdownJob: Job? = null
    private var examDraftRevision = 0L
    private var leavingExamToken: Long? = null
    private val examDraftWrites = Channel<PendingExamDraftWrite>(Channel.CONFLATED)
    private var sessionTokenCounter = 0L
    private val notifiedCommitted = mutableSetOf<Long>()
    private var examPaperReviewVisible = false

    private val _quizState = MutableStateFlow(QuizUiState())
    val quizState: StateFlow<QuizUiState> = _quizState.asStateFlow()

    private val _lastPracticeSession = MutableStateFlow(practiceSessionStore.load())
    val lastPracticeSession: StateFlow<PracticeSessionDescriptor?> = _lastPracticeSession.asStateFlow()

    private val _lastResult = MutableStateFlow<ResultUiState?>(null)
    val lastResult: StateFlow<ResultUiState?> = _lastResult.asStateFlow()

    private val _submittingExamToken = MutableStateFlow<Long?>(null)
    val submittingExamToken: StateFlow<Long?> = _submittingExamToken.asStateFlow()

    val pendingExamNavigation: StateFlow<Long?> = examNavigation.pending

    private val _pagerJump = MutableStateFlow(0L)
    val pagerJump: StateFlow<Long> = _pagerJump.asStateFlow()

    private val _examHistory = MutableStateFlow(ExamHistoryState())
    val examHistory: StateFlow<ExamHistoryState> = _examHistory.asStateFlow()

    private val _examHistoryDetail = MutableStateFlow(ExamHistoryDetailState())
    val examHistoryDetail: StateFlow<ExamHistoryDetailState> = _examHistoryDetail.asStateFlow()

    private val _examPaperReview = MutableStateFlow(ExamPaperReviewState())
    val examPaperReview: StateFlow<ExamPaperReviewState> = _examPaperReview.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
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

    fun consumeExamNavigation(token: Long): Boolean =
        examNavigation.consume(token, _quizState.value.sessionToken)

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

    suspend fun startPastPaperQuiz(paperId: Long, resumeFrom: PracticeSessionDescriptor? = null): QuizStartResult = runCatching {
        val paper = repository.getPastPaper(paperId)
        if (paper == null) {
            if (resumeFrom != null && _lastPracticeSession.value == resumeFrom) dismissLastPractice()
            return QuizStartResult(false, "真题卷不存在")
        }
        val questions = repository.getPastPaperQuestions(paperId)
        if (questions.isEmpty()) return QuizStartResult(false, "卷中还没有题目")
        val quizQuestions = questions.map { it.toQuizQuestion(1.0) }
        startQuiz(
            bankId = paper.bankId,
            bankName = paper.name,
            mode = PracticeMode.PAPER,
            questions = quizQuestions,
            config = ExamConfig(passScore = 0.0),
            bookmarkedIds = repository.getFavoriteIdsForBank(paper.bankId),
            startIndex = resumeFrom?.resumeIndexIn(quizQuestions) ?: 0,
            attemptStats = attemptStatsFor(quizQuestions),
            paperId = paperId,
        )
        QuizStartResult(true)
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        QuizStartResult(false, "无法开始真题练习：${error.message ?: "未知错误"}")
    }

    suspend fun startWrongQuiz(
        bankId: Long? = null,
        questionId: Long? = null,
        questionIds: Set<Long>? = null,
    ): QuizStartResult {
        return runCatching {
            val rows = repository.getPendingWrongRows(bankId, questionId, questionIds)
            if (rows.isEmpty()) return QuizStartResult(false, "当前筛选范围没有错题")
            val questions = repository.getQuestionsByIds(rows.map { it.wrongItem.questionId })
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
            if (candidates.isEmpty()) return QuizStartResult(false, "当前筛选没有可练题")
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
                isStandaloneQuestion = true,
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
            essayCount = minOf(available.getValue(QuestionType.ESSAY), 5),
            singlePoints = 2.0,
            multiPoints = 2.0,
            judgePoints = 2.0,
            fillPoints = 2.0,
            durationMinutes = 60,
            passScore = 60.0,
            shuffleOptions = true,
            wrongFirst = true,
            unansweredFirst = false,
            multiSelectRule = MultiSelectRule.LESS_NO_SCORE,
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
        onMessage("无法放弃考试：${error.message ?: "请重试"}")
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
        paperId: Long? = null,
        isStandaloneQuestion: Boolean = false,
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
            paperId = paperId,
            isStandaloneQuestion = isStandaloneQuestion,
        )
        scheduleExamCountdown(_quizState.value.sessionToken)
        rememberPracticeSession()
    }

    private fun scheduleExamCountdown(token: Long) {
        val initial = _quizState.value
        if (initial.mode != PracticeMode.EXAM || initial.config.durationMinutes <= 0) return
        examCountdownJob?.cancel()
        examCountdownJob = scope.launch {
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
            onMessage("考试进度保存失败，请重试")
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
            onMessage("考试进度保存失败：${error.message ?: "请重试"}")
            return false
        } finally {
            leavingExamToken = null
            if (!left && _quizState.value.sessionToken == token && _quizState.value.active) {
                scheduleExamCountdown(token)
            }
        }
    }

    fun isRevealed(questionId: Long): Boolean = _quizState.value.revealed(questionId)

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
        val question = state.questions.firstOrNull { it.questionId == questionId } ?: return false
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
        val question = state.questions.firstOrNull { it.questionId == questionId } ?: return
        val before = AnswerState(state.answers, state.committedIds, state.answerViewedIds)
        if (QuizSession.isRevealed(before, questionId, state.mode, state.config) ||
            (question.type == QuestionType.ESSAY && (questionId in state.essayRevealedIds ||
                EssayAnswer.isAssessed(state.answers[questionId].orEmpty())))
        ) {
            notifyCommittedOnce(questionId)
            return
        }
        val storedText = if (question.type == QuestionType.ESSAY) EssayAnswer.pending(text) else text
        val after = QuizSession.setText(before, questionId, storedText, state.mode, state.config)
        _quizState.update { it.copy(answers = after.answers) }
        saveExamProgress()
    }

    fun commitAnswer(questionId: Long) {
        val state = _quizState.value
        if (state.reciting && state.canSwitchRecite) return
        if (examIsBusy(state) || submitIfExamExpired(state)) return
        val question = state.questions.firstOrNull { it.questionId == questionId } ?: return
        if (question.type == QuestionType.ESSAY) return
        val before = AnswerState(state.answers, state.committedIds, state.answerViewedIds)
        val after = QuizSession.commit(before, questionId, state.mode, state.config) ?: return
        _quizState.update { it.copy(answers = after.answers, committedIds = after.committedIds) }
        saveExamProgress()
        onCommitted(state, question, after.answers[questionId].orEmpty())
    }

    fun revealEssay(questionId: Long) {
        val state = _quizState.value
        if (state.reciting || examIsBusy(state) || submitIfExamExpired(state)) return
        if (state.questions.none { it.questionId == questionId && it.type == QuestionType.ESSAY }) return
        _quizState.update { it.copy(essayRevealedIds = it.essayRevealedIds + questionId) }
    }

    fun assessEssay(questionId: Long, correct: Boolean) {
        val state = _quizState.value
        if (state.reciting || questionId !in state.essayRevealedIds || questionId in state.committedIds ||
            examIsBusy(state) || submitIfExamExpired(state)
        ) return
        val question = state.questions.firstOrNull { it.questionId == questionId && it.type == QuestionType.ESSAY } ?: return
        val answer = EssayAnswer.encode(EssayAnswer.text(state.answers[questionId].orEmpty()), correct)
        _quizState.update { it.copy(answers = it.answers + (questionId to answer), committedIds = it.committedIds + questionId) }
        saveExamProgress()
        onCommitted(state, question, answer)
    }

    fun retryQuestion(questionId: Long) {
        val state = _quizState.value
        if (state.mode == PracticeMode.EXAM) return
        val after = QuizSession.retry(AnswerState(state.answers, state.committedIds, state.answerViewedIds), questionId)
        _quizState.update {
            it.copy(
                answers = after.answers,
                committedIds = after.committedIds,
                answerViewedIds = after.answerViewedIds,
                essayRevealedIds = it.essayRevealedIds - questionId,
            )
        }
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
                    AnswerNormalizer.normalizeForStorage(old.type, old.correctAnswer) ||
                    replacement.type != old.type || replacement.acceptedAnswers != old.acceptedAnswers
            val questions = state.questions.toMutableList().also { it[index] = replacement }
            state.copy(
                questions = questions,
                answers = if (answerChanged) state.answers - question.id else state.answers,
                committedIds = if (answerChanged) state.committedIds - question.id else state.committedIds,
                essayRevealedIds = if (answerChanged) state.essayRevealedIds - question.id else state.essayRevealedIds,
                answerViewedIds = when {
                    answerChanged && state.reciting && state.currentQuestion?.questionId == question.id -> state.answerViewedIds + question.id
                    answerChanged -> state.answerViewedIds - question.id
                    else -> state.answerViewedIds
                },
            )
        }
        saveExamProgress()
    }

    fun leaveQuiz() {
        examCountdownJob?.cancel()
        examCountdownJob = null
        val state = _quizState.value
        val bankId = state.bankId
        if (state.active && state.tracksSequencePosition && bankId != null) {
            scope.launch {
                runCatching {
                    repository.saveSequencePosition(bankId, state.currentIndex, clock.nowMillis())
                }
            }
        }
        rememberPracticeSession()
        _quizState.update { it.copy(active = false) }
    }

    private fun rememberPracticeSession() {
        val state = _quizState.value
        if (!state.active || state.isStandaloneQuestion || state.mode == PracticeMode.EXAM || state.mode == PracticeMode.FLASHCARD) return
        val descriptor = PracticeSessionDescriptor(
            mode = state.mode,
            bankId = state.bankId,
            bankName = state.bankName,
            paperId = state.paperId,
            questionIds = if (state.mode == PracticeMode.SEQUENCE) emptyList() else state.questions.map { it.questionId },
            currentIndex = state.currentIndex,
            updatedAt = clock.nowMillis(),
        )
        practiceSessionStore.save(descriptor)
        _lastPracticeSession.value = descriptor
    }

    fun dismissLastPractice() {
        practiceSessionStore.clear()
        _lastPracticeSession.value = null
    }

    fun clearLastPracticeIfBank(bankId: Long) {
        if (_lastPracticeSession.value?.bankId == bankId) dismissLastPractice()
    }

    fun clearIfBank(bankId: Long) {
        if (_quizState.value.bankId == bankId) {
            examCountdownJob?.cancel()
            examCountdownJob = null
            _quizState.value = QuizUiState()
        }
        clearLastPracticeIfBank(bankId)
    }

    suspend fun resumeLastPractice(): QuizStartResult {
        val descriptor = _lastPracticeSession.value
            ?: return QuizStartResult(false, "没有可继续的练习")
        return when (descriptor.mode) {
            PracticeMode.SEQUENCE -> {
                val bankId = descriptor.bankId
                if (bankId == null) QuizStartResult(false, "练习对应的题库不存在")
                else startSequenceQuiz(bankId)
            }
            PracticeMode.PAPER -> {
                val paperId = descriptor.paperId
                if (paperId == null) QuizStartResult(false, "真题卷不存在")
                else startPastPaperQuiz(paperId, resumeFrom = descriptor)
            }
            PracticeMode.WRONG, PracticeMode.FAVORITE, PracticeMode.TOPIC, PracticeMode.RECITE ->
                resumeQuestionSetPractice(descriptor)
            else -> QuizStartResult(false, "该练习不支持继续")
        }
    }

    private suspend fun resumeQuestionSetPractice(
        descriptor: PracticeSessionDescriptor,
    ): QuizStartResult = runCatching {
        val rows = repository.getQuestionsByIds(descriptor.questionIds)
        val byId = rows.associateBy { it.id }
        val questions = descriptor.questionIds.mapNotNull { byId[it] }
        if (questions.isEmpty()) {
            dismissLastPractice()
            return QuizStartResult(false, "练习中的题目已被删除")
        }
        val quizQuestions = questions.map { it.toQuizQuestion(1.0) }
        val favoriteIds = repository.getAllFavoriteIds()
        startQuiz(
            bankId = descriptor.bankId,
            bankName = descriptor.bankName,
            mode = descriptor.mode,
            questions = quizQuestions,
            config = ExamConfig(passScore = 0.0),
            bookmarkedIds = favoriteIds,
            startIndex = descriptor.resumeIndexIn(quizQuestions),
            attemptStats = attemptStatsFor(quizQuestions),
        )
        QuizStartResult(true)
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        QuizStartResult(false, "无法继续练习：${error.message ?: "未知错误"}")
    }

    private fun PracticeSessionDescriptor.resumeIndexIn(questions: List<QuizQuestion>): Int {
        val indexById = questions.mapIndexed { index, question -> question.questionId to index }.toMap()
        val savedIndex = currentIndex.coerceIn(0, questionIds.size)
        // 题目被删除时先找下一道仍存在的题，再回退到前一道。
        return questionIds.asSequence().drop(savedIndex).firstNotNullOfOrNull(indexById::get)
            ?: questionIds.subList(0, savedIndex).asReversed().firstNotNullOfOrNull(indexById::get)
            ?: currentIndex.coerceIn(0, questions.lastIndex)
    }

    suspend fun sequenceRoundComplete(bankId: Long): Boolean {
        val progress = repository.getSequenceProgress(bankId) ?: return false
        val total = repository.getQuestions(bankId).size
        return total > 0 && progress.answeredCount >= total
    }

    suspend fun restartSequenceRound(bankId: Long) {
        runCatching {
            repository.startNewRound(bankId, clock.nowMillis())
            onMessage("已开始新一轮练习")
        }
    }

    fun countdownState(): CountdownState {
        val state = _quizState.value
        return QuizCountdown.state(state.startedAt, state.config.durationMinutes, clock.nowMillis())
    }

    private fun notifyCommittedOnce(questionId: Long) {
        if (notifiedCommitted.add(questionId)) {
            onMessage("本题已作答")
        }
    }

    private fun onCommitted(state: QuizUiState, question: QuizQuestion, answer: String) {
        rememberPracticeSession()
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
            currentIndex = state.currentIndex.takeIf { state.tracksSequencePosition },
            answeredAt = clock.nowMillis(),
            roundStartedAt = state.roundStartedAt,
            answerViewed = question.questionId in state.answerViewedIds,
            reviewZone = flashcardZone,
        )
        scope.launch {
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
        scope.launch {
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
            onMessage(if (isFavorite) "已收藏" else "已取消收藏")
        }
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
        rememberPracticeSession()
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
        if (!state.tracksSequencePosition) return
        scope.launch {
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
            onMessage("交卷失败：考试进度不存在")
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
                    val answer = state.answers[question.questionId]?.takeIf {
                        it.isNotBlank() && (question.type != QuestionType.ESSAY || EssayAnswer.isAssessed(it))
                    } ?: return@mapNotNull null
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
                        flashcardZone,
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
                    onMessage("交卷失败：${error.message ?: "未知错误"}")
                }
            }
        }.invokeOnCompletion { _submittingExamToken.compareAndSet(sessionToken, null) }
    }

    fun clearLastResult() {
        _lastResult.value = null
    }

    fun refreshExamHistory() {
        if (_examHistory.value.loading) return
        _examHistory.update { it.copy(loading = true, failed = false) }
        scope.launch {
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
        scope.launch {
            val state = runCatching { repository.getExamHistoryDetail(sessionId) }.fold(
                onSuccess = { detail -> ExamHistoryDetailState(sessionId = sessionId, detail = detail) },
                onFailure = { ExamHistoryDetailState(sessionId = sessionId, failed = true) },
            )
            if (_examHistoryDetail.value.sessionId == sessionId) {
                _examHistoryDetail.value = state
            }
        }
    }

    fun loadExamPaperReview(sessionId: Long) {
        val current = _examPaperReview.value
        if (current.sessionId == sessionId && current.loading) return
        _examPaperReview.value = ExamPaperReviewState(
            sessionId = sessionId,
            loading = true,
            review = current.review?.takeIf { current.sessionId == sessionId },
        )
        scope.launch {
            val state = runCatching { repository.getExamPaperReview(sessionId) }.fold(
                onSuccess = { review -> ExamPaperReviewState(sessionId = sessionId, review = review) },
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
        acceptedAnswers = acceptedAnswers,
        images = images,
        labelMap = emptyMap(),
    )
}
