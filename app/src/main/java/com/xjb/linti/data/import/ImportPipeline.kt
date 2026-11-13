package com.xjb.linti.data.import

import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ImportMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

data class ImportRun(
    val method: ImportMethod,
    val extraction: ExtractionResult,
    val questions: List<DraftQuestion>,
    val warnings: List<String>,
    val failedCount: Int,
) {
    val recognizedCount: Int get() = questions.size
    val pendingCount: Int get() = questions.count { it.needsReview }
    val confirmedCount: Int get() = questions.count { !it.needsReview }
}

class ImportPipeline(
    private val extractorRegistry: DocumentExtractorRegistry,
    private val parser: QuestionParser,
    private val aiClientProvider: suspend () -> AiClient?,
) {
    suspend fun run(
        input: InputStream,
        fileName: String,
        method: ImportMethod,
        kindOverride: DocumentKind? = null,
    ): ImportRun = withContext(Dispatchers.IO) {
        val extraction = extractorRegistry.extract(input, fileName, kindOverride)
        val aiWarnings = mutableListOf<String>()

        val localParse = if (method == ImportMethod.AI_ONLY) {
            null
        } else {
            parser.parse(extraction.text)
        }

        val questions = when (method) {
            ImportMethod.LOCAL_ONLY -> localParse?.questions.orEmpty()
            ImportMethod.AI_ONLY -> {
                if (extraction.text.isBlank()) {
                    emptyList()
                } else {
                    val aiResult = requestAi(extraction.text, aiWarnings)
                    aiResult
                }
            }
            ImportMethod.LOCAL_THEN_AI -> {
                val local = localParse?.questions.orEmpty()
                val needsAi = local.isEmpty() || local.any { it.needsReview }
                if (!needsAi) {
                    local
                } else {
                    val pendingText = if (local.isEmpty()) {
                        extraction.text
                    } else {
                        local.filter { it.needsReview }
                            .joinToString("\n\n") { question ->
                                question.rawSnippet ?: buildQuestionSnippet(question)
                            }
                    }
                    val aiQuestions = requestAi(pendingText, aiWarnings)
                    mergeAiQuestions(local, aiQuestions)
                }
            }
        }

        val warnings = buildList {
            addAll(extraction.warnings)
            addAll(localParse?.warnings.orEmpty())
            addAll(aiWarnings)
        }.distinct()
        val failedCount = if (questions.isEmpty() && extraction.text.isBlank()) 1 else 0
        ImportRun(
            method = method,
            extraction = extraction,
            questions = questions,
            warnings = warnings,
            failedCount = failedCount,
        )
    }

    private suspend fun requestAi(
        text: String,
        warnings: MutableList<String>,
    ): List<DraftQuestion> {
        if (text.isBlank()) return emptyList()
        val client = runCatching { aiClientProvider() }.getOrElse { error ->
            warnings += "AI 配置不可用：${error.message ?: "未知错误"}"
            return emptyList()
        }
        if (client == null) {
            warnings += "未配置 AI 服务，已保留本地结果；可在设置中填写 HTTPS Base URL 与 API Key"
            return emptyList()
        }
        return runCatching {
            client.parseQuestions(text).map { question ->
                question.copy(
                    needsReview = true,
                    reviewReasons = (question.reviewReasons + "AI 识别结果，请人工确认").distinct(),
                )
            }
        }.getOrElse { error ->
            warnings += "AI 识别失败：${error.message ?: "未知错误"}"
            emptyList()
        }
    }

    private fun mergeAiQuestions(
        local: List<DraftQuestion>,
        ai: List<DraftQuestion>,
    ): List<DraftQuestion> {
        if (ai.isEmpty()) return local
        val localByNumber = local.associateBy { it.sourceNumber }
        val consumed = mutableSetOf<String>()
        val merged = local.map { question ->
            val replacement = ai.firstOrNull {
                it.sourceNumber.isNotBlank() && it.sourceNumber == question.sourceNumber
            }
            if (replacement != null) {
                consumed += replacement.sourceNumber
                replacement.copy(
                    needsReview = true,
                    reviewReasons = (replacement.reviewReasons + "AI 补全或校对结果，请人工确认").distinct(),
                )
            } else {
                question
            }
        }.toMutableList()
        ai.filter { it.sourceNumber !in consumed && it.sourceNumber !in localByNumber }
            .forEach { merged += it }
        return merged
    }

    private fun buildQuestionSnippet(question: DraftQuestion): String = buildString {
        append(question.sourceNumber).append(". ").append(question.stem).append('\n')
        question.options.forEach { append(it.label).append(". ").append(it.text).append('\n') }
        if (question.answer.isNotBlank()) append("答案：").append(question.answer).append('\n')
        if (question.explanation.isNotBlank()) append("解析：").append(question.explanation)
    }
}
