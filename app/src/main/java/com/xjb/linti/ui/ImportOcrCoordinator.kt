package com.xjb.linti.ui

import android.app.Application
import android.net.Uri
import com.xjb.linti.data.AppContainer
import com.xjb.linti.data.db.ImportReviewSnapshot
import com.xjb.linti.data.import.AiConfigurationException
import com.xjb.linti.data.import.ConfirmImportResult
import com.xjb.linti.data.import.ConfirmTarget
import com.xjb.linti.data.import.DocumentKind
import com.xjb.linti.data.import.DraftValidator
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
import com.xjb.linti.data.ocr.CropRect
import com.xjb.linti.data.ocr.DeepSeekOcrClient
import com.xjb.linti.data.ocr.ImageOcrBitmaps
import com.xjb.linti.data.ocr.OcrException
import com.xjb.linti.data.ocr.OcrFailureKind
import com.xjb.linti.data.ocr.OcrKeyResolver
import com.xjb.linti.data.ocr.OcrKeySource
import com.xjb.linti.data.ocr.OcrLimits
import com.xjb.linti.data.ocr.OcrUploadNaming
import com.xjb.linti.data.prefs.SettingsSnapshot
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ImportJob
import com.xjb.linti.domain.ImportMethod
import com.xjb.linti.domain.ImportStatus
import com.xjb.linti.domain.QuestionImage
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** 导题、分文件校对、未入库草稿持久化与 DeepSeek 在线 OCR 协调器。 */
internal class ImportOcrCoordinator(
    private val application: Application,
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val ocrClientFactory: () -> DeepSeekOcrClient?,
    private val ocrKeySourceOverride: OcrKeySource?,
    private val queryDisplayName: (Uri) -> String?,
    private val releaseQuestionImages: (List<QuestionImage>) -> Unit,
    private val onSettingsChanged: (SettingsSnapshot) -> Unit,
    private val onMessage: (String) -> Unit,
) {
    private val repository = container.repository

    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    private val _importProgress = MutableStateFlow(ImportProgressUiState())
    val importProgress: StateFlow<ImportProgressUiState> = _importProgress.asStateFlow()

    private val _importNavigation = Channel<ImportNavigationTarget>(Channel.BUFFERED)
    val importNavigation = _importNavigation.receiveAsFlow()

    private val _splitImportState = MutableStateFlow(SplitImportUiState())
    val splitImportState: StateFlow<SplitImportUiState> = _splitImportState.asStateFlow()

    private val _imageOcrState = MutableStateFlow(ImageOcrUiState())
    val imageOcrState: StateFlow<ImageOcrUiState> = _imageOcrState.asStateFlow()

    private val _imageOcrPreview = MutableStateFlow<android.graphics.Bitmap?>(null)
    val imageOcrPreview: StateFlow<android.graphics.Bitmap?> = _imageOcrPreview.asStateFlow()

    private val _pdfOcrState = MutableStateFlow(PdfOcrUiState())
    val pdfOcrState: StateFlow<PdfOcrUiState> = _pdfOcrState.asStateFlow()

    private var activeImportJob: Job? = null
    private val draftPersistMutex = Mutex()
    private val splitMatchGeneration = AtomicLong()
    private val ocrGeneration = AtomicLong()
    private var ocrJob: Job? = null
    private var pdfInspection: PdfPageInspection? = null

    private var materialPdf: File? = null
    private var materialPdfName: String = ""
    private var materialInspection: PdfPageInspection? = null

    fun backgroundImport() {
        _importProgress.update { state ->
            if (state.isImporting) state.copy(isBackgrounded = true) else state
        }
    }

    fun cancelActiveImport() {
        activeImportJob?.cancel()
    }

    fun clearImportProgressError() {
        _importProgress.update { it.copy(error = null) }
    }

    fun startImport(uri: Uri, kind: DocumentKind, method: ImportMethod) {
        if (activeImportJob?.isCompleted == false || _importProgress.value.awaitingPdfDecision) return
        activeImportJob = scope.launch {
            try {
                if (kind == DocumentKind.PDF) {
                    importPdfFromUri(uri, method, navigateOnSuccess = true)
                } else {
                    importFromUri(uri, kind, method, navigateOnSuccess = true)
                }
            } catch (cancelled: CancellationException) {
                _importProgress.value = ImportProgressUiState()
                throw cancelled
            } finally {
                if (activeImportJob === coroutineContext[Job]) {
                    activeImportJob = null
                }
            }
        }
    }

    fun openPendingPdf() {
        if (!_importProgress.value.awaitingPdfDecision) return
        _importProgress.value = ImportProgressUiState()
        _importNavigation.trySend(ImportNavigationTarget.PDF_OCR)
    }

    fun dismissPendingPdf() {
        if (!_importProgress.value.awaitingPdfDecision) return
        onPdfOcrScreenLeft()
        _importProgress.value = ImportProgressUiState()
    }

    suspend fun openImportJobForReview(jobId: Long): Boolean {
        val snapshot = draftPersistMutex.withLock {
            repository.getImportReviewDraft(jobId)
        }
        if (snapshot == null) {
            onMessage("该待校对记录缺少草稿数据，请重新导入")
            return false
        }
        val validTargetBankId = snapshot.targetBankId?.takeIf { id -> repository.getBank(id) != null }
        val ocrSource = snapshot.ocrSourceName?.let { name ->
            runCatching { OcrImportSource.valueOf(name) }.getOrNull()
        }
        _importState.value = ImportUiState(
            jobId = jobId,
            fileName = snapshot.fileName,
            method = snapshot.method,
            kind = snapshot.kind,
            drafts = snapshot.drafts,
            warnings = snapshot.warnings,
            failedCount = snapshot.failedCount,
            targetBankId = validTargetBankId,
            newBankName = snapshot.newBankName,
            isSplitFile = snapshot.isSplitFile,
            answerFileName = snapshot.answerFileName,
            splitProblems = snapshot.splitProblems,
            ocrSource = ocrSource,
            requiresValidAnswers = snapshot.requiresValidAnswers,
            sourcePages = snapshot.sourcePages,
            originalPreviewPath = snapshot.originalPreviewPath,
            pdfPreviewPath = snapshot.pdfPreviewPath,
        )
        return true
    }

    fun deleteImportJob(jobId: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                val snapshot = draftPersistMutex.withLock { repository.deletePendingImportReview(jobId) }
                snapshot?.originalPreviewPath?.let { File(it).delete() }
                snapshot?.pdfPreviewPath?.let { File(it).delete() }
                repository.cleanImages()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onMessage("删除草稿失败，请重试")
            }
        }
    }

    private fun ImportUiState.toReviewSnapshot(): ImportReviewSnapshot = ImportReviewSnapshot(
        fileName = fileName,
        method = method,
        kind = kind,
        drafts = drafts,
        warnings = warnings,
        failedCount = failedCount,
        targetBankId = targetBankId,
        newBankName = newBankName,
        isSplitFile = isSplitFile,
        answerFileName = answerFileName,
        splitProblems = splitProblems,
        ocrSourceName = ocrSource?.name,
        requiresValidAnswers = requiresValidAnswers,
        sourcePages = sourcePages,
        originalPreviewPath = originalPreviewPath,
        pdfPreviewPath = pdfPreviewPath,
    )

    private fun schedulePersistReviewDraft() {
        val state = _importState.value
        val jobId = state.jobId ?: return
        if (state.confirmedBankId != null || state.drafts.isEmpty()) return
        val snapshot = state.toReviewSnapshot()
        // 在调用顺序中进入互斥队列，避免较早的编辑在较新的编辑之后保存。
        container.appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                draftPersistMutex.withLock {
                    repository.saveImportReviewDraft(jobId, snapshot)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onMessage("校对草稿保存失败，请检查可用空间后重试")
            }
        }
    }

    suspend fun importFromUri(
        uri: Uri,
        kind: DocumentKind?,
        method: ImportMethod,
        navigateOnSuccess: Boolean = false,
    ): Boolean {
        val preserveBackground = _importProgress.value.isImporting && _importProgress.value.isBackgrounded
        if (!preserveBackground) {
            _importProgress.value = ImportProgressUiState(
                isImporting = true,
                isBackgrounded = false,
                progressText = "正在读取文件…",
                method = method,
                kind = kind,
            )
        } else {
            _importProgress.update {
                it.copy(
                    isImporting = true,
                    progressText = "正在读取文件…",
                    method = method,
                    kind = kind,
                    error = null,
                )
            }
        }
        var stagedImages: List<QuestionImage> = emptyList()
        var emptyReason: String? = null
        var resolvedFileName = "导入文件"
        var recognizedCount = 0
        var builtState: ImportUiState? = null
        return try {
            withContext(Dispatchers.IO) {
                val resolver = application.contentResolver
                val fileName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "导入文件"
                resolvedFileName = fileName
                val mimeType = resolver.getType(uri)
                _importProgress.update { it.copy(fileName = fileName) }
                val run = resolver.openInputStream(uri)?.use { input ->
                    container.importPipeline.run(input, fileName, method, kind) { progress ->
                        _importProgress.update { state -> state.copy(progressText = progress) }
                    }.also { result ->
                        stagedImages = result.questions.flatMap { it.images }
                    }
                } ?: error("无法打开所选文件")
                val banks = repository.getBanks()
                val now = System.currentTimeMillis()
                val job = ImportJob(
                    fileName = fileName,
                    mimeType = mimeType,
                    kind = run.extraction.kind.storageValue,
                    method = method,
                    status = if (run.questions.isEmpty()) ImportStatus.FAILED else ImportStatus.REVIEW,
                    recognizedCount = run.recognizedCount,
                    confirmedCount = run.confirmedCount,
                    pendingCount = run.pendingCount,
                    failedCount = run.failedCount,
                    createdAt = now,
                    warnings = run.warnings,
                )
                emptyReason = if (run.questions.isEmpty()) {
                    run.warnings.firstOrNull() ?: "未能从文件中识别出题目"
                } else {
                    null
                }
                recognizedCount = run.questions.size
                if (emptyReason == null) {
                    val reviewState = ImportUiState(
                        fileName = fileName,
                        method = method,
                        kind = run.extraction.kind,
                        drafts = run.questions,
                        warnings = run.warnings,
                        failedCount = run.failedCount,
                        targetBankId = banks.firstOrNull()?.id,
                        newBankName = if (banks.isEmpty()) "导入题库" else "",
                    )
                    val jobId = repository.insertImportReview(job, reviewState.toReviewSnapshot())
                    builtState = reviewState.copy(jobId = jobId)
                } else {
                    repository.insertImportJob(job)
                }
            }
            if (emptyReason != null) {
                _importProgress.value = ImportProgressUiState(error = emptyReason)
                onMessage(emptyReason)
                false
            } else {
                val wasBackgrounded = _importProgress.value.isBackgrounded
                _importProgress.value = ImportProgressUiState()
                if (!wasBackgrounded && builtState != null) {
                    clearImport()
                    _importState.value = requireNotNull(builtState)
                    if (navigateOnSuccess) _importNavigation.trySend(ImportNavigationTarget.REVIEW)
                } else if (wasBackgrounded) {
                    onMessage("「$resolvedFileName」导入完成（共 $recognizedCount 题），可在「最近导入」中继续校对")
                }
                true
            }
        } catch (error: CancellationException) {
            _importProgress.value = ImportProgressUiState()
            throw error
        } catch (error: Throwable) {
            val message = "导入失败：${error.message ?: "未知错误"}"
            _importProgress.value = ImportProgressUiState(error = message)
            onMessage(message)
            false
        } finally {
            // Room 事务提交后由草稿引用保护图片，取消和失败时可清理未引用图片。
            if (stagedImages.isNotEmpty()) releaseQuestionImages(stagedImages)
        }
    }

    suspend fun startManualEntry(): Boolean {
        val banks = repository.getBanks()
        clearImport()
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
        schedulePersistReviewDraft()
    }

    fun confirmAllValidImportDrafts(): Int {
        var confirmed = 0
        _importState.update { state ->
            val updatedProblems = state.splitProblems.toMutableMap()
            val updatedDrafts = state.drafts.mapIndexed { index, draft ->
                if (!draft.needsReview) return@mapIndexed draft
                val candidate = draft.copy(
                    needsReview = false,
                    reviewReasons = emptyList(),
                    confidence = 1.0,
                )
                if (state.isSplitFile || state.requiresValidAnswers) {
                    val result = SplitDraftResolver.afterEdit(candidate, draft, updatedProblems[index])
                    if (result.resolved) updatedProblems.remove(index)
                    if (!result.draft.needsReview) confirmed++
                    result.draft
                } else {
                    if (DraftValidator.validate(candidate).reasons.isEmpty()) {
                        confirmed++
                        candidate
                    } else {
                        draft
                    }
                }
            }
            state.copy(drafts = updatedDrafts, splitProblems = updatedProblems)
        }
        schedulePersistReviewDraft()
        return confirmed
    }

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
        schedulePersistReviewDraft()
    }

    fun updateImportTargetBank(bankId: Long?) {
        _importState.update { it.copy(targetBankId = bankId) }
        schedulePersistReviewDraft()
    }

    fun updateImportNewBankName(name: String) {
        _importState.update { it.copy(newBankName = name) }
        schedulePersistReviewDraft()
    }

    fun selectSplitQuestionFile(uri: Uri?) = selectSplitSlot(isQuestion = true, uri = uri)

    fun selectSplitAnswerFile(uri: Uri?) = selectSplitSlot(isQuestion = false, uri = uri)

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
        scope.launch(Dispatchers.IO) {
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
        var stagedImages: List<QuestionImage> = emptyList()
        try {
            val resolver = application.contentResolver
            val questionStream = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(questionUri) }.getOrNull()
            }
            if (splitMatchGeneration.get() != generation) {
                questionStream?.close()
                return false
            }
            if (questionStream == null) {
                _splitImportState.update {
                    it.copy(question = it.question.copy(error = "无法打开试题文件，请更换文件后重试"))
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
                    it.copy(answer = it.answer.copy(error = "无法打开答案文件，请更换文件后重试"))
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
                        ).also { result ->
                            stagedImages = (result as? SplitImportOutcome.Success)?.drafts.orEmpty().flatMap { it.images }
                        }
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
                    if (splitMatchGeneration.get() != generation) return false
                    val banks = repository.getBanks()
                    if (splitMatchGeneration.get() != generation) return false
                    val job = ImportJob(
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
                    )
                    val drafts = outcome.drafts.mapIndexed { index, draft ->
                        SplitDraftResolver.applyMatchProblems(draft, outcome.problems[index])
                    }
                    val review = ImportUiState(
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
                    val jobId = repository.insertImportReview(job, review.toReviewSnapshot())
                    if (splitMatchGeneration.get() != generation) return false
                    clearImport()
                    _importState.value = review.copy(jobId = jobId)
                    return true
                }
            }
        } finally {
            if (stagedImages.isNotEmpty()) releaseQuestionImages(stagedImages)
            if (splitMatchGeneration.get() == generation) {
                _splitImportState.update { it.copy(isMatching = false) }
            }
        }
    }

    suspend fun confirmImport(): ConfirmImportResult {
        val state = _importState.value
        if (state.isConfirming || state.confirmedBankId != null) {
            return ConfirmImportResult.Failure("该导入正在入库或已入库")
        }
        val blocking = state.blockingDraftCount
        if (blocking > 0) {
            val message = "还有 $blocking 题待处理（待核对或缺少答案），全部解决后才能入库"
            _importState.update { it.copy(error = message) }
            return ConfirmImportResult.Failure(message)
        }
        _importState.update { it.copy(isConfirming = true, error = null) }
        val result = try {
            container.confirmImportUseCase.execute(
                drafts = state.drafts,
                target = ConfirmTarget(
                    bankId = state.targetBankId,
                    newBankName = state.newBankName,
                ),
                sourceFileName = state.fileName,
                importJobId = state.jobId,
                onlyValidAnswers = state.isSplitFile || state.requiresValidAnswers,
            )
        } catch (cancelled: CancellationException) {
            _importState.update { it.copy(isConfirming = false) }
            throw cancelled
        } catch (_: Exception) {
            ConfirmImportResult.Failure("入库失败，请检查可用空间后重试")
        }
        when (result) {
            is ConfirmImportResult.Success -> {
                deleteOcrPreviewFiles()
                _importState.update {
                    it.copy(
                        isConfirming = false,
                        confirmedBankId = result.value.bankId,
                    )
                }
                onMessage("已入库 ${result.value.questionIds.size} 道题")
            }
            is ConfirmImportResult.Failure -> {
                _importState.update {
                    it.copy(isConfirming = false, error = result.message)
                }
                onMessage(result.message)
            }
        }
        return result
    }

    fun clearImport() {
        val state = _importState.value
        if (state.jobId == null) {
            val images = state.drafts.flatMap { it.images }
            if (images.isNotEmpty()) releaseQuestionImages(images)
            deleteOcrPreviewFiles()
        }
        _importState.value = ImportUiState()
    }

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
        onSettingsChanged(container.settingsStore.snapshot())
        onMessage("DeepSeek OCR Key 已保存")
    }

    fun clearOcrApiKey() {
        container.settingsStore.clearOcrApiKey()
        onSettingsChanged(container.settingsStore.snapshot())
        onMessage("DeepSeek OCR Key 已清除")
    }

    suspend fun prepareImageOcr(
        uri: Uri,
        kind: DocumentKind,
        method: ImportMethod = ImportMethod.LOCAL_ONLY,
    ): Boolean {
        if (_imageOcrState.value.isRunning) return false
        cancelOcrRequest()
        val generation = ocrGeneration.get()
        var temp: File? = null
        return try {
            val preview = withContext(Dispatchers.IO) {
                val copied = copyUriToOcrTemp(uri)
                temp = copied
                ImageOcrBitmaps.decodeExifCorrectedPreview(copied.absolutePath)
            }
            if (ocrGeneration.get() != generation) {
                preview.recycle()
                return false
            }
            val copied = requireNotNull(temp)
            _imageOcrState.value.sourcePath?.let { File(it).delete() }
            _imageOcrPreview.value = preview
            _imageOcrState.value = ImageOcrUiState(
                sourcePath = copied.absolutePath,
                fileName = queryDisplayName(uri) ?: copied.name,
                kind = kind,
                importMethod = method,
            )
            temp = null
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (ocrGeneration.get() == generation) onMessage("无法读取所选图片：${error.message ?: "文件不可读"}")
            false
        } finally {
            temp?.delete()
        }
    }

    fun onImageOcrScreenLeft() {
        val source = _imageOcrState.value.sourcePath
        invalidateOcrRequest()
        _imageOcrState.value = ImageOcrUiState()
        _imageOcrPreview.value = null
        source?.let { path -> runCatching { File(path).delete() } }
    }

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
            _imageOcrState.update { it.copy(error = OCR_KEY_MISSING_MESSAGE) }
            return false
        }
        val generation = ocrGeneration.get()
        val sourcePath = state.sourcePath
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
        val deferred = scope.async {
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
                    importMethod = state.importMethod,
                    onStatus = { status ->
                        if (ocrGeneration.get() == generation) {
                            _imageOcrState.update { it.copy(statusText = status) }
                        }
                    },
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
                clearImport()
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
        } catch (_: CancellationException) {
            false
        } finally {
            if (ocrJob === deferred) ocrJob = null
        }
    }

    fun clearMaterialFile() {
        materialPdf?.delete()
        materialPdf = null
        materialInspection = null
    }

    suspend fun readMaterial(uri: Uri): MaterialFileResult = withContext(Dispatchers.IO) {
        clearMaterialFile()
        try {
            val name = queryDisplayName(uri) ?: uri.lastPathSegment.orEmpty()
            val kind = DocumentKind.fromFileName(name)
            if (kind !in setOf(DocumentKind.PDF, DocumentKind.DOCX, DocumentKind.TXT, DocumentKind.XLSX, DocumentKind.CSV) ||
                name.endsWith(".doc", true) || name.endsWith(".xls", true)
            ) {
                return@withContext MaterialFileResult.Failed("请选择 PDF、DOCX、TXT、XLSX 或 CSV 文件")
            }
            if (kind == DocumentKind.PDF) {
                val file = copyUriToPdfTemp(uri)
                val inspection = PdfPageInspector().inspectFile(file)
                when (val plan = PdfOcrPlanner.plan(inspection)) {
                    is PdfOcrPlan.AllTextUsable -> file.delete()
                    is PdfOcrPlan.NeedsOcr, is PdfOcrPlan.LoadFailed -> {
                        materialPdf = file
                        materialPdfName = name
                        materialInspection = inspection.takeIf { plan is PdfOcrPlan.NeedsOcr }
                        return@withContext MaterialFileResult.NeedsOcr(inspection.scanPages.size, inspection.pageCount)
                    }
                    is PdfOcrPlan.Encrypted -> {
                        file.delete()
                        return@withContext MaterialFileResult.Failed(plan.message)
                    }
                    is PdfOcrPlan.Empty -> {
                        file.delete()
                        return@withContext MaterialFileResult.Failed(plan.message)
                    }
                    is PdfOcrPlan.TooManyPages -> {
                        file.delete()
                        return@withContext MaterialFileResult.Failed("PDF 超过在线识别页数上限，请拆分文件")
                    }
                }
            }
            val input = application.contentResolver.openInputStream(uri)
                ?: return@withContext MaterialFileResult.Failed("无法读取文件")
            val result = input.use { container.extractorRegistry.extract(it, name, kind) }
            if (result.text.isBlank()) MaterialFileResult.Failed("文件未读取到文字") else MaterialFileResult.Text(result.text, name)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            MaterialFileResult.Failed("无法读取文件，请检查文件格式")
        }
    }

    suspend fun recognizeMaterialPdf(): MaterialFileResult {
        val file = materialPdf ?: return MaterialFileResult.Failed("请重新选择文件")
        val client = ocrClientFactory() ?: return MaterialFileResult.Failed("尚未配置 DeepSeek OCR Key", true)
        return try {
            val inspection = materialInspection
            val result = client.recognizeFile(file, OcrUploadNaming.pdfUploadFileName(materialPdfName), inspection?.pageCount)
            val text = if (inspection == null) {
                result.pages.joinToString("\n") { it.text }
            } else {
                PdfOcrMerger.merge(inspection, result.pages, PdfOcrMergeMode.SCANS_ONLY).joinedText
            }
            if (text.isBlank()) {
                MaterialFileResult.Failed("文件未识别到文字")
            } else {
                val name = materialPdfName
                clearMaterialFile()
                MaterialFileResult.Text(text, name)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: OcrException) {
            MaterialFileResult.Failed(e.message ?: "OCR 识别失败，请重试")
        } catch (_: Exception) {
            MaterialFileResult.Failed("OCR 识别失败，请检查网络后重试")
        }
    }

    suspend fun importPdfFromUri(
        uri: Uri,
        method: ImportMethod,
        navigateOnSuccess: Boolean = false,
    ): PdfImportDecision {
        invalidateOcrRequest()
        val fileName = queryDisplayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "PDF 文件"
        _importProgress.value = ImportProgressUiState(
            isImporting = true,
            isBackgrounded = false,
            progressText = "正在检查 PDF 页面与文字层…",
            fileName = fileName,
            method = method,
            kind = DocumentKind.PDF,
        )
        fun failPdf(message: String): PdfImportDecision.Failed {
            _importProgress.value = ImportProgressUiState(error = message)
            onMessage(message)
            return PdfImportDecision.Failed(message)
        }
        var temp: File? = null
        return try {
            val inspection = withContext(Dispatchers.IO) {
                val copied = copyUriToPdfTemp(uri)
                temp = copied
                PdfPageInspector().inspectFile(copied)
            }
            when (val plan = PdfOcrPlanner.plan(inspection)) {
                is PdfOcrPlan.AllTextUsable -> {
                    if (importFromUri(uri, DocumentKind.PDF, method, navigateOnSuccess)) {
                        PdfImportDecision.ProceedLocal
                    } else {
                        PdfImportDecision.Failed(_importProgress.value.error ?: "PDF 解析失败，未能识别出题目")
                    }
                }
                is PdfOcrPlan.NeedsOcr, is PdfOcrPlan.LoadFailed -> {
                    val wasBackgrounded = _importProgress.value.isBackgrounded
                    _pdfOcrState.value.sourcePath?.let { File(it).delete() }
                    pdfInspection = if (plan is PdfOcrPlan.NeedsOcr) inspection else null
                    _pdfOcrState.value = PdfOcrUiState(
                        sourcePath = requireNotNull(temp).absolutePath,
                        fileName = fileName,
                        pageCount = inspection.pageCount,
                        usablePageCount = inspection.usablePageCount,
                        scanPages = (plan as? PdfOcrPlan.NeedsOcr)?.scanPages.orEmpty(),
                        loadErrorMessage = (plan as? PdfOcrPlan.LoadFailed)?.message,
                        importMethod = method,
                    )
                    temp = null
                    if (navigateOnSuccess && wasBackgrounded) {
                        _importProgress.value = ImportProgressUiState(fileName = fileName, awaitingPdfDecision = true)
                        onMessage("PDF 待确认，返回首页可继续处理")
                    } else {
                        _importProgress.value = ImportProgressUiState()
                        if (navigateOnSuccess) _importNavigation.trySend(ImportNavigationTarget.PDF_OCR)
                    }
                    PdfImportDecision.NeedsDecision
                }
                is PdfOcrPlan.TooManyPages -> {
                    if (importFromUri(uri, DocumentKind.PDF, method, navigateOnSuccess)) {
                        PdfImportDecision.ProceedLocal
                    } else {
                        failPdf(
                            "PDF 超过 ${OcrLimits.MAX_PAGES} 页在线识别上限" +
                                "（当前 ${plan.pageCount} 页），且未能提取到可用题目；请拆分后重试",
                        )
                    }
                }
                is PdfOcrPlan.Encrypted -> failPdf(plan.message)
                is PdfOcrPlan.Empty -> failPdf(plan.message)
            }
        } catch (cancelled: CancellationException) {
            _importProgress.value = ImportProgressUiState()
            throw cancelled
        } catch (_: OcrFileTooLargeException) {
            failPdf("PDF 超过 ${OcrLimits.MAX_FILE_BYTES / (1024 * 1024)} MB 上限，请压缩或拆分后再试")
        } catch (error: Exception) {
            failPdf("无法读取所选 PDF：${error.message ?: "文件不可读"}")
        } finally {
            temp?.delete()
        }
    }

    fun onPdfOcrScreenLeft() {
        val source = _pdfOcrState.value.sourcePath
        invalidateOcrRequest()
        _pdfOcrState.value = PdfOcrUiState()
        pdfInspection = null
        source?.let { path -> runCatching { File(path).delete() } }
    }

    suspend fun submitPdfOcr(mode: PdfOcrMergeMode, billingConfirmed: Boolean = false): Boolean {
        val state = _pdfOcrState.value
        if (state.isRunning || state.sourcePath == null) return false
        if (state.pendingBillingConfirm && !billingConfirmed) return false
        val client = ocrClientFactory()
        if (client == null) {
            _pdfOcrState.update { it.copy(error = OCR_KEY_MISSING_MESSAGE) }
            return false
        }
        val generation = ocrGeneration.get()
        val sourcePath = state.sourcePath
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
        val deferred = scope.async {
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
                    importMethod = state.importMethod,
                    onStatus = { status ->
                        if (ocrGeneration.get() == generation) {
                            _pdfOcrState.update { it.copy(statusText = status) }
                        }
                    },
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
                clearImport()
                _importState.value = importState
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
        } catch (_: CancellationException) {
            false
        } finally {
            if (ocrJob === deferred) ocrJob = null
        }
    }

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

    fun onReviewScreenLeft() {
        val state = _importState.value
        if (state.confirmedBankId != null) {
            deleteOcrPreviewFiles()
            _importState.value = ImportUiState()
        } else if (state.jobId != null && state.drafts.isNotEmpty()) {
            schedulePersistReviewDraft()
            _importState.value = ImportUiState()
        } else if (state.ocrSource != null || state.drafts.any { it.images.isNotEmpty() }) {
            clearImport()
        }
    }

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
        importMethod: ImportMethod = ImportMethod.LOCAL_ONLY,
        onStatus: (String) -> Unit = {},
    ): ImportUiState? {
        val joined = pageTexts.joinToString("\n")
        val parse = container.parser.parse(joined)
        var usedAi = false
        val aiExtraWarnings = mutableListOf<String>()
        val resolvedDrafts: List<DraftQuestion>
        val sourcePages: Map<Int, Int>

        val shouldTryAi = importMethod == ImportMethod.AI_ONLY ||
            (parse.questions.isEmpty() && joined.isNotBlank())

        if (shouldTryAi) {
            val aiClient = try {
                container.createAiClient()
            } catch (e: AiConfigurationException) {
                if (importMethod == ImportMethod.AI_ONLY) {
                    throw OcrException(OcrFailureKind.BAD_REQUEST, e.message ?: "AI 配置无效")
                }
                null
            }
            if (aiClient != null) {
                onStatus("OCR 文字提取完成，正在调用 AI 整理题目…")
                val aiDrafts = try {
                    aiClient.parseQuestions(joined, onStatus)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    throw OcrException(
                        OcrFailureKind.SERVER,
                        "OCR 已识别文字，但 AI 整理题目失败：${e.message ?: "未知错误"}",
                    )
                }
                if (aiDrafts.isEmpty()) return null
                usedAi = true
                if (importMethod != ImportMethod.AI_ONLY) {
                    aiExtraWarnings += "本地规则未能切分 OCR 文字，已自动调用 AI 整理题目"
                }
                aiExtraWarnings += "以下题目由 OCR 转写后经 AI 整理，入库前请逐条核对"
                resolvedDrafts = aiDrafts.map { draft ->
                    val validation = DraftValidator.validate(draft)
                    draft.copy(
                        needsReview = true,
                        reviewReasons = (validation.reasons + draft.reviewReasons).distinct(),
                    )
                }
                sourcePages = if (sourcePagesAllFirst) {
                    aiDrafts.indices.associateWith { 1 }
                } else {
                    aiDrafts.mapIndexedNotNull { index, draft ->
                        OcrDraftPostProcessor.locatePage(pageTexts, draft)?.let { page -> index to page }
                    }.toMap()
                }
            } else {
                if (parse.questions.isEmpty()) {
                    if (importMethod == ImportMethod.AI_ONLY && joined.isNotBlank()) {
                        throw OcrException(
                            OcrFailureKind.NO_KEY,
                            "OCR 已转写文字，但尚未配置 AI Key 且本地未能切分题目；请先在「我的 → AI 服务」配置 Key",
                        )
                    }
                    return null
                }
                if (importMethod == ImportMethod.AI_ONLY) {
                    aiExtraWarnings += "未配置 AI 服务，已改用本地规则解析识别文字"
                }
                val post = OcrDraftPostProcessor.process(parse, pageTexts, extraWarnings)
                resolvedDrafts = post.drafts
                aiExtraWarnings += post.warnings
                sourcePages = if (sourcePagesAllFirst) {
                    post.drafts.indices.associateWith { 1 }
                } else {
                    post.sourcePages
                }
            }
        } else {
            if (parse.questions.isEmpty()) return null
            val post = OcrDraftPostProcessor.process(parse, pageTexts, extraWarnings)
            resolvedDrafts = post.drafts
            aiExtraWarnings += post.warnings
            sourcePages = if (sourcePagesAllFirst) {
                post.drafts.indices.associateWith { 1 }
            } else {
                post.sourcePages
            }
        }

        val effectiveMethod = if (usedAi) ImportMethod.AI_ONLY else ImportMethod.LOCAL_ONLY
        val allWarnings = (
            if (usedAi) (extraWarnings + aiExtraWarnings) else (parse.warnings + aiExtraWarnings)
            ).distinct()
        val banks = repository.getBanks()
        val job = ImportJob(
            fileName = fileName,
            mimeType = null,
            kind = kind.storageValue,
            method = effectiveMethod,
            status = ImportStatus.REVIEW,
            recognizedCount = resolvedDrafts.size,
            confirmedCount = resolvedDrafts.count { !it.needsReview },
            pendingCount = resolvedDrafts.count { it.needsReview },
            failedCount = 0,
            createdAt = System.currentTimeMillis(),
            warnings = allWarnings,
        )
        val built = ImportUiState(
            fileName = fileName,
            method = effectiveMethod,
            kind = kind,
            drafts = resolvedDrafts,
            warnings = allWarnings,
            failedCount = 0,
            targetBankId = banks.firstOrNull()?.id,
            newBankName = if (banks.isEmpty()) "导入题库" else "",
            ocrSource = source,
            requiresValidAnswers = true,
            sourcePages = sourcePages,
            originalPreviewPath = previewPath,
            pdfPreviewPath = pdfPath,
        )
        val jobId = repository.insertImportReview(job, built.toReviewSnapshot())
        return built.copy(jobId = jobId)
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
        File(application.cacheDir, "ocr_import").apply { mkdirs() }

    private class OcrFileTooLargeException : Exception("文件超过大小上限")

    private fun copyUriToPdfTemp(uri: Uri): File =
        copyUriToTempFile(uri, prefix = "ocr_pdf_", extension = "pdf", emptyError = "所选 PDF 为空")

    private fun copyUriToOcrTemp(uri: Uri): File {
        val displayName = queryDisplayName(uri)
        val extension = displayName?.substringAfterLast('.', "")?.lowercase()?.takeIf {
            it.matches(Regex("[a-z0-9]{1,8}"))
        } ?: "jpg"
        return copyUriToTempFile(uri, prefix = "ocr_img_", extension = extension, emptyError = "所选图片为空")
    }

    private fun copyUriToTempFile(
        uri: Uri,
        prefix: String,
        extension: String,
        emptyError: String,
    ): File {
        val resolver = application.contentResolver
        val file = File.createTempFile(prefix, ".$extension", ocrTempDir())
        try {
            val stream = resolver.openInputStream(uri) ?: error("无法打开所选文件")
            stream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        total += read
                        if (total > OcrLimits.MAX_FILE_BYTES) throw OcrFileTooLargeException()
                        output.write(buffer, 0, read)
                    }
                }
            }
            check(file.length() > 0L) { emptyError }
            return file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}
