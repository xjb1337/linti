package com.xjb.linti.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.explanation.AiExplanationException
import com.xjb.linti.data.import.ConfirmImportResult
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.data.import.PdfOcrMergeMode
import com.xjb.linti.data.ocr.CropRect
import com.xjb.linti.data.ocr.DeepSeekOcrClient
import com.xjb.linti.data.ocr.OcrKeySource
import com.xjb.linti.data.prefs.AiExplanationReasoningEffort
import com.xjb.linti.data.prefs.AiProvider
import com.xjb.linti.data.prefs.PracticeSessionStore
import com.xjb.linti.data.prefs.SettingsSnapshot
import com.xjb.linti.data.repo.ExamDraftSummary
import com.xjb.linti.data.repo.LintiRepository
import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.AppearanceMode
import com.xjb.linti.domain.AttemptStats
import com.xjb.linti.domain.BankSummary
import com.xjb.linti.domain.ColorTheme
import com.xjb.linti.domain.CountdownState
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ExamConfig
import com.xjb.linti.domain.FavoriteQuestionItem
import com.xjb.linti.domain.FlashcardSchedule
import com.xjb.linti.domain.FontScale
import com.xjb.linti.domain.GradingResult
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.NoteQuestionItem
import com.xjb.linti.domain.PastPaperSource
import com.xjb.linti.domain.PracticeMode
import com.xjb.linti.domain.PracticeSessionDescriptor
import com.xjb.linti.domain.Question
import com.xjb.linti.domain.QuestionImage
import com.xjb.linti.domain.QuestionOutcome
import com.xjb.linti.domain.QuestionType
import com.xjb.linti.domain.QuizClock
import com.xjb.linti.domain.SystemQuizClock
import com.xjb.linti.domain.WrongQuestionItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * 应用顶层 ViewModel 门面：对外保持统一的 StateFlow 与操作接口，
 * 内部按业务域委托给 [ImportOcrCoordinator]、[QuizExamCoordinator] 与 [UpdateCoordinator]。
 */
class LintiViewModel(
    application: Application,
    private val container: AppContainer,
    private val clock: QuizClock = SystemQuizClock,
    ocrClientFactory: () -> DeepSeekOcrClient? = container::createOcrClient,
    ocrKeySourceOverride: OcrKeySource? = null,
    private val flashcardZone: ZoneId = ZoneId.systemDefault(),
    private val aiExplanationRepository: com.xjb.linti.data.explanation.AiExplanationRepository = container.aiExplanationRepository,
    val aiQuestionRepository: com.xjb.linti.data.aigeneration.AiQuestionRepository = container.aiQuestionRepository,
    practiceSessionStore: PracticeSessionStore = container.practiceSessionStore,
) : AndroidViewModel(application) {
    private val repository = container.repository

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _settings = MutableStateFlow(container.settingsStore.snapshot())
    val settings: StateFlow<SettingsSnapshot> = _settings.asStateFlow()

    private val _aiExplanationRequests = MutableStateFlow<Map<Long, AiExplanationRequestState>>(emptyMap())
    val aiExplanationRequests: StateFlow<Map<Long, AiExplanationRequestState>> = _aiExplanationRequests.asStateFlow()

    private val _questionCollection = MutableStateFlow<QuestionCollectionState?>(null)
    val questionCollection: StateFlow<QuestionCollectionState?> = _questionCollection.asStateFlow()

    private val updateCoordinator = UpdateCoordinator(
        scope = viewModelScope,
        updateClient = container.updateClient,
        updateInstaller = container.updateInstaller,
        onMessage = { _message.value = it },
    )

    private val importOcr = ImportOcrCoordinator(
        application = application,
        container = container,
        scope = viewModelScope,
        ocrClientFactory = ocrClientFactory,
        ocrKeySourceOverride = ocrKeySourceOverride,
        queryDisplayName = ::queryDisplayName,
        releaseQuestionImages = ::releaseQuestionImages,
        onSettingsChanged = { _settings.value = it },
        onMessage = { _message.value = it },
    )

    private val quizExam = QuizExamCoordinator(
        repository = repository,
        practiceSessionStore = practiceSessionStore,
        clock = clock,
        flashcardZone = flashcardZone,
        scope = viewModelScope,
        onMessage = { _message.value = it },
    )

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

    val pendingImportReviews: StateFlow<List<ImportJob>> = repository.pendingImportReviews.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList(),
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

    val importState: StateFlow<ImportUiState> = importOcr.importState
    val importProgress: StateFlow<ImportProgressUiState> = importOcr.importProgress
    val importNavigation: Flow<ImportNavigationTarget> = importOcr.importNavigation
    val splitImportState: StateFlow<SplitImportUiState> = importOcr.splitImportState
    val imageOcrState: StateFlow<ImageOcrUiState> = importOcr.imageOcrState
    val imageOcrPreview: StateFlow<Bitmap?> = importOcr.imageOcrPreview
    val pdfOcrState: StateFlow<PdfOcrUiState> = importOcr.pdfOcrState

    val quizState: StateFlow<QuizUiState> = quizExam.quizState
    val lastPracticeSession: StateFlow<PracticeSessionDescriptor?> = quizExam.lastPracticeSession
    val lastResult: StateFlow<ResultUiState?> = quizExam.lastResult
    val submittingExamToken: StateFlow<Long?> = quizExam.submittingExamToken
    val pendingExamNavigation: StateFlow<Long?> = quizExam.pendingExamNavigation
    val pagerJump: StateFlow<Long> = quizExam.pagerJump
    val examHistory: StateFlow<ExamHistoryState> = quizExam.examHistory
    val examHistoryDetail: StateFlow<ExamHistoryDetailState> = quizExam.examHistoryDetail
    val examPaperReview: StateFlow<ExamPaperReviewState> = quizExam.examPaperReview

    val updateState: StateFlow<UpdateUiState> = updateCoordinator.state

    val fontScale: StateFlow<FontScale> = container.quizPreferences.fontScale
    val appearanceMode: StateFlow<AppearanceMode> = container.quizPreferences.appearanceMode
    val colorTheme: StateFlow<ColorTheme> = container.quizPreferences.colorTheme
    val autoNextOnCorrect: StateFlow<Boolean> = container.quizPreferences.autoNextOnCorrect
    val autoCheckUpdates: StateFlow<Boolean> = container.quizPreferences.autoCheckUpdates
    val masteryThreshold: StateFlow<Int?> = container.quizPreferences.masteryThreshold
    val dailyNewCards: StateFlow<Int?> = container.quizPreferences.dailyNewCards

    init {
        if (container.quizPreferences.autoCheckUpdates.value) {
            checkForUpdates(silent = true)
        }
    }

    // ===== 偏好设置与版本更新 =====

    fun setFontScale(value: FontScale) = container.quizPreferences.setFontScale(value)
    fun setAppearanceMode(value: AppearanceMode) = container.quizPreferences.setAppearanceMode(value)
    fun setColorTheme(value: ColorTheme) = container.quizPreferences.setColorTheme(value)
    fun setAutoNextOnCorrect(value: Boolean) = container.quizPreferences.setAutoNextOnCorrect(value)
    fun setMasteryThreshold(value: Int?) = container.quizPreferences.setMasteryThreshold(value)
    fun setDailyNewCards(value: Int?) = container.quizPreferences.setDailyNewCards(value)
    fun setAutoCheckUpdates(value: Boolean) = container.quizPreferences.setAutoCheckUpdates(value)

    fun checkForUpdates(silent: Boolean = false) = updateCoordinator.checkForUpdates(silent)
    fun downloadAndInstallUpdate() = updateCoordinator.downloadAndInstallUpdate()
    fun cancelUpdateDownload() = updateCoordinator.cancelUpdateDownload()
    fun dismissUpdateDialog() = updateCoordinator.dismissUpdateDialog()
    fun openInstallPermissionSettings() = updateCoordinator.openInstallPermissionSettings()
    fun onAppResumed() = updateCoordinator.onAppResumed()

    // ===== 导题、分文件校对与 DeepSeek OCR =====

    fun backgroundImport() = importOcr.backgroundImport()
    fun cancelActiveImport() = importOcr.cancelActiveImport()
    fun clearImportProgressError() = importOcr.clearImportProgressError()
    fun openPendingPdf() = importOcr.openPendingPdf()
    fun dismissPendingPdf() = importOcr.dismissPendingPdf()
    fun startImport(uri: Uri, kind: DocumentKind, method: ImportMethod) = importOcr.startImport(uri, kind, method)
    suspend fun openImportJobForReview(jobId: Long): Boolean = importOcr.openImportJobForReview(jobId)
    fun deleteImportJob(jobId: Long) = importOcr.deleteImportJob(jobId)
    suspend fun importFromUri(uri: Uri, kind: DocumentKind?, method: ImportMethod): Boolean =
        importOcr.importFromUri(uri, kind, method)
    suspend fun startManualEntry(): Boolean = importOcr.startManualEntry()
    fun updateImportDraft(index: Int, draft: DraftQuestion) = importOcr.updateImportDraft(index, draft)
    fun confirmAllValidImportDrafts(): Int = importOcr.confirmAllValidImportDrafts()
    fun resolveSplitConflict(index: Int, useExternalAnswer: Boolean) = importOcr.resolveSplitConflict(index, useExternalAnswer)
    fun updateImportTargetBank(bankId: Long?) = importOcr.updateImportTargetBank(bankId)
    fun updateImportNewBankName(name: String) = importOcr.updateImportNewBankName(name)
    fun selectSplitQuestionFile(uri: Uri?) = importOcr.selectSplitQuestionFile(uri)
    fun selectSplitAnswerFile(uri: Uri?) = importOcr.selectSplitAnswerFile(uri)
    fun onSplitScreenLeft() = importOcr.onSplitScreenLeft()
    suspend fun matchSplitFiles(): Boolean = importOcr.matchSplitFiles()
    suspend fun confirmImport(): ConfirmImportResult = importOcr.confirmImport()
    fun clearImport() = importOcr.clearImport()

    fun ocrKeyState(): OcrKeySource = importOcr.ocrKeyState()
    fun saveOcrApiKey(key: String) = importOcr.saveOcrApiKey(key)
    fun clearOcrApiKey() = importOcr.clearOcrApiKey()
    suspend fun prepareImageOcr(uri: Uri, kind: DocumentKind, method: ImportMethod = ImportMethod.LOCAL_ONLY): Boolean =
        importOcr.prepareImageOcr(uri, kind, method)
    fun onImageOcrScreenLeft() = importOcr.onImageOcrScreenLeft()
    suspend fun submitImageOcr(
        rotationDegrees: Int,
        previewCropRect: CropRect,
        previewWidth: Float,
        previewHeight: Float,
        billingConfirmed: Boolean = false,
    ): Boolean = importOcr.submitImageOcr(rotationDegrees, previewCropRect, previewWidth, previewHeight, billingConfirmed)
    fun clearMaterialFile() = importOcr.clearMaterialFile()
    suspend fun readMaterial(uri: Uri): MaterialFileResult = importOcr.readMaterial(uri)
    suspend fun recognizeMaterialPdf(): MaterialFileResult = importOcr.recognizeMaterialPdf()
    suspend fun importPdfFromUri(uri: Uri, method: ImportMethod): PdfImportDecision = importOcr.importPdfFromUri(uri, method)
    fun onPdfOcrScreenLeft() = importOcr.onPdfOcrScreenLeft()
    suspend fun submitPdfOcr(mode: PdfOcrMergeMode, billingConfirmed: Boolean = false): Boolean =
        importOcr.submitPdfOcr(mode, billingConfirmed)
    fun cancelOcr() = importOcr.cancelOcr()
    fun onReviewScreenLeft() = importOcr.onReviewScreenLeft()

    // ===== 练题、模拟考试与历史记录 =====

    fun consumeExamNavigation(token: Long): Boolean = quizExam.consumeExamNavigation(token)
    suspend fun startSequenceQuiz(bankId: Long): QuizStartResult = quizExam.startSequenceQuiz(bankId)
    suspend fun startPastPaperQuiz(paperId: Long): QuizStartResult = quizExam.startPastPaperQuiz(paperId)
    suspend fun startWrongQuiz(bankId: Long? = null, questionId: Long? = null, questionIds: Set<Long>? = null): QuizStartResult =
        quizExam.startWrongQuiz(bankId, questionId, questionIds)
    suspend fun startFavoriteQuiz(bankId: Long? = null, questionId: Long? = null): QuizStartResult =
        quizExam.startFavoriteQuiz(bankId, questionId)
    suspend fun topicSetupData(): TopicSetupData = quizExam.topicSetupData()
    suspend fun startTopicQuiz(
        bankIds: Set<Long>,
        types: Set<QuestionType>,
        count: Int,
        random: Random = Random.Default,
        chapterIds: Set<Long?>? = null,
    ): QuizStartResult = quizExam.startTopicQuiz(bankIds, types, count, random, chapterIds)
    suspend fun startSingleQuestionQuiz(questionId: Long, mode: PracticeMode = PracticeMode.FAVORITE): QuizStartResult =
        quizExam.startSingleQuestionQuiz(questionId, mode)
    suspend fun examSetupData(bankId: Long): ExamSetupData = quizExam.examSetupData(bankId)
    suspend fun startExam(bankId: Long, config: ExamConfig): QuizStartResult = quizExam.startExam(bankId, config)
    suspend fun resumeExam(bankId: Long): QuizStartResult = quizExam.resumeExam(bankId)
    suspend fun discardExamDraft(draftId: String): Boolean = quizExam.discardExamDraft(draftId)
    suspend fun leaveExam(): Boolean = quizExam.leaveExam()
    fun isRevealed(questionId: Long): Boolean = quizExam.isRevealed(questionId)
    fun setExamPaperReviewVisible(visible: Boolean) = quizExam.setExamPaperReviewVisible(visible)
    fun setReciting(reciting: Boolean) = quizExam.setReciting(reciting)
    fun needsConfirm(questionId: Long): Boolean = quizExam.needsConfirm(questionId)
    fun canCommit(questionId: Long): Boolean = quizExam.canCommit(questionId)
    fun attemptStatsFor(questionId: Long): AttemptStats = quizExam.attemptStatsFor(questionId)
    fun selectAnswer(questionId: Long, rawAnswer: String) = quizExam.selectAnswer(questionId, rawAnswer)
    fun setFillAnswer(questionId: Long, text: String) = quizExam.setFillAnswer(questionId, text)
    fun commitAnswer(questionId: Long) = quizExam.commitAnswer(questionId)
    fun revealEssay(questionId: Long) = quizExam.revealEssay(questionId)
    fun assessEssay(questionId: Long, correct: Boolean) = quizExam.assessEssay(questionId, correct)
    fun retryQuestion(questionId: Long) = quizExam.retryQuestion(questionId)
    fun replaceQuestionInSession(question: Question) = quizExam.replaceQuestionInSession(question)
    fun leaveQuiz() = quizExam.leaveQuiz()
    fun dismissLastPractice() = quizExam.dismissLastPractice()
    suspend fun resumeLastPractice(): QuizStartResult = quizExam.resumeLastPractice()
    suspend fun sequenceRoundComplete(bankId: Long): Boolean = quizExam.sequenceRoundComplete(bankId)
    suspend fun restartSequenceRound(bankId: Long) = quizExam.restartSequenceRound(bankId)
    fun countdownState(): CountdownState = quizExam.countdownState()
    fun toggleCurrentFlag() = quizExam.toggleCurrentFlag()
    fun toggleCurrentBookmark() = quizExam.toggleCurrentBookmark()
    fun goToQuestion(index: Int, fromPager: Boolean = false) = quizExam.goToQuestion(index, fromPager)
    fun nextQuestion() = quizExam.nextQuestion()
    fun previousQuestion() = quizExam.previousQuestion()
    fun submitQuiz(sessionToken: Long, autoSubmitted: Boolean = false) = quizExam.submitQuiz(sessionToken, autoSubmitted)
    fun clearLastResult() = quizExam.clearLastResult()
    fun refreshExamHistory() = quizExam.refreshExamHistory()
    fun loadExamHistoryDetail(sessionId: Long) = quizExam.loadExamHistoryDetail(sessionId)
    fun loadExamPaperReview(sessionId: Long) = quizExam.loadExamPaperReview(sessionId)
    fun outcomeFor(questionId: Long): QuestionOutcome = quizExam.outcomeFor(questionId)
    fun gradeCurrent(): GradingResult? = quizExam.gradeCurrent()

    // ===== 题库、章节、真题、错题、笔记与 AI 解析 =====

    fun observeAiExplanation(questionId: Long) = aiExplanationRepository.observe(questionId)

    fun requestAiExplanation(questionId: Long, onOpenSettings: () -> Unit) {
        if (_aiExplanationRequests.value[questionId]?.loading == true) return
        _aiExplanationRequests.value = _aiExplanationRequests.value + (questionId to AiExplanationRequestState(loading = true))
        viewModelScope.launch {
            val result = try {
                aiExplanationRepository.generate(questionId)
                AiExplanationRequestState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiExplanationException) {
                AiExplanationRequestState(error = e.reason)
            } catch (_: Exception) {
                AiExplanationRequestState(error = com.xjb.linti.data.explanation.AiExplanationError.NETWORK)
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

    fun removeWrongItem(questionId: Long) {
        viewModelScope.launch { repository.markWrongCorrected(questionId, clock.nowMillis()) }
    }

    fun pastPapersForBank(bankId: Long) = repository.observePastPapers(bankId)
    fun pastPaperItems(paperId: Long) = repository.observePastPaperItems(paperId)
    suspend fun createPastPaper(bankId: Long, year: Int, name: String, source: PastPaperSource, note: String?) =
        repository.createPastPaper(bankId, year, name, source, note)
    suspend fun updatePastPaper(paperId: Long, year: Int, name: String, source: PastPaperSource, note: String?) =
        repository.updatePastPaper(paperId, year, name, source, note)
    suspend fun deletePastPaper(paperId: Long) = repository.deletePastPaper(paperId)

    suspend fun deleteBank(bankId: Long) {
        quizExam.clearIfBank(bankId)
        repository.deleteBank(bankId)
        if (_questionCollection.value?.bankId == bankId) _questionCollection.value = null
        _message.value = "题库已删除"
    }

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
        val questions = if (bankId == null) repository.getAllQuestions() else repository.getQuestions(bankId)
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
            title = if (query.isBlank()) bank?.name ?: "全部题目" else "搜索「${query.trim()}」",
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

    // ===== AI 服务、图片、备份与全局提示 =====

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

    fun resetAiEndpointToDefault() {
        container.settingsStore.saveAiConfiguration(
            provider = AiProvider.DEEPSEEK,
            apiKey = null,
            baseUrl = "",
            model = "",
        )
        _settings.value = container.settingsStore.snapshot()
        _message.value = "已恢复默认 DeepSeek 接口与模型"
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

    suspend fun importQuestionImage(uri: Uri): QuestionImage = withContext(Dispatchers.IO) {
        val input = requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)) { "无法打开图片" }
        container.imageStore.import(input)
    }

    fun releaseQuestionImages(images: List<QuestionImage>) {
        images.forEach(container.imageStore::release)
        viewModelScope.launch(Dispatchers.IO) { runCatching { repository.cleanImages() } }
    }

    suspend fun exportBackup(uri: Uri) = withContext(Dispatchers.IO) {
        val output = requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri)) { "无法写入备份" }
        output.use { repository.exportBackup(it) }
    }

    suspend fun restoreBackup(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val input = requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri)) { "无法读取备份" }
            input.use { repository.restoreBackup(it) }
            _message.value = "备份已恢复"
            true
        }.getOrElse { error ->
            _message.value = "备份恢复失败：${error.message ?: "文件格式不正确"}"
            false
        }
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
