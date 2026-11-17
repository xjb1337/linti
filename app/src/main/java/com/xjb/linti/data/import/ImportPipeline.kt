package com.xjb.linti.data.import

import com.xjb.linti.data.images.QuestionImageStore
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ImportMethod
import kotlinx.coroutines.CancellationException
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
    private val imageStore: QuestionImageStore? = null,
) {
    suspend fun run(
        input: InputStream,
        fileName: String,
        method: ImportMethod,
        kindOverride: DocumentKind? = null,
        onProgress: (String) -> Unit = {},
    ): ImportRun = withContext(Dispatchers.IO) {
        onProgress("正在读取并提取文件内容…")
        val extraction = extractorRegistry.extract(input, fileName, kindOverride)
        val aiWarnings = mutableListOf<String>()

        if (method != ImportMethod.AI_ONLY) {
            onProgress("正在解析题目与选项…")
        }
        val localParse = if (method == ImportMethod.AI_ONLY && extraction.embeddedImages.isEmpty()) null else parser.parse(extraction.text)
        val imageWarnings = mutableListOf<String>()
        val localWithImages = DocxImages.attach(localParse?.questions.orEmpty(), extraction, imageStore, imageWarnings)
        val cleanText = DocxImages.strip(extraction.text)

        val questions = when (method) {
            ImportMethod.LOCAL_ONLY -> localWithImages
            ImportMethod.AI_ONLY -> {
                if (cleanText.isBlank()) {
                    if (extraction.warnings.isEmpty()) {
                        aiWarnings += "文件未读取到可用文字，无法识别"
                    }
                    emptyList()
                } else {
                    val aiResult = requestAi(cleanText, aiWarnings, method, onProgress)
                    val used = mutableSetOf<Int>()
                    fun normNum(value: String): String =
                        value.trim().trimEnd('.', '、', ')', '）', ':', '：')
                    fun normStem(value: String): String =
                        value.trim().replace(Regex("\\s+"), " ")
                    val aiNumberCounts = aiResult.groupingBy { normNum(it.sourceNumber) }.eachCount()
                    val matched = aiResult.mapIndexed { index, question ->
                        val available = localWithImages.indices.filter { it !in used }
                        val qStem = normStem(question.stem)
                        val qNum = normNum(question.sourceNumber)
                        val sameStem = available.filter { normStem(localWithImages[it].stem) == qStem }
                        val sameNumber = available.filter {
                            qNum.isNotBlank() && aiNumberCounts[qNum] == 1 &&
                                normNum(localWithImages[it].sourceNumber) == qNum
                        }
                        val positional = if (
                            aiResult.size == localWithImages.size &&
                            index in available &&
                            localWithImages[index].images.isNotEmpty()
                        ) {
                            index
                        } else {
                            null
                        }
                        val sourceIndex = sameStem.singleOrNull() ?: sameNumber.singleOrNull() ?: positional
                        sourceIndex?.let(used::add)
                        question.copy(images = sourceIndex?.let { localWithImages[it].images }.orEmpty())
                    }
                    val unmatchedWithImages = localWithImages.indices.filter { it !in used }
                        .map { localWithImages[it] }.filter { it.images.isNotEmpty() }
                    val resolved = if (unmatchedWithImages.isEmpty()) matched else {
                        imageWarnings += "部分图片未能与 AI 结果可靠匹配，已保留本地原题供核对"
                        localWithImages.map { question ->
                            question.copy(needsReview = true, reviewReasons = (question.reviewReasons + "AI 结果与图片题无法可靠匹配，已保留本地原题").distinct())
                        }
                    }
                    if (resolved.isEmpty() && aiWarnings.isEmpty()) {
                        aiWarnings += "AI 未从文档中识别出题目，请检查文件内容或改用本地识别"
                    }
                    resolved
                }
            }
        }

        val warnings = buildList {
            addAll(extraction.warnings)
            addAll(localParse?.warnings.orEmpty())
            addAll(aiWarnings)
            addAll(imageWarnings)
        }.distinct()
        val failedCount = if (questions.isEmpty() && extraction.text.isBlank()) 1 else 0
        ImportRun(
            method = method,
            extraction = extraction.copy(text = cleanText, embeddedImages = emptyMap()),
            questions = questions,
            warnings = warnings,
            failedCount = failedCount,
        )
    }

    private suspend fun requestAi(
        text: String,
        warnings: MutableList<String>,
        method: ImportMethod,
        onProgress: (String) -> Unit,
    ): List<DraftQuestion> {
        if (text.isBlank()) return emptyList()
        val client = runCatching { aiClientProvider() }.getOrElse { error ->
            if (error is CancellationException) throw error
            warnings += "AI 配置不可用：${error.message ?: "未知错误"}"
            return emptyList()
        }
        if (client == null) {
            warnings += "尚未配置 AI 服务，请先在「我的 → AI 服务」中填写并保存 API Key"
            return emptyList()
        }
        onProgress("正在调用 AI 识别题目…")
        return try {
            client.parseQuestions(text, onProgress).map { question ->
                question.copy(
                    needsReview = true,
                    reviewReasons = (question.reviewReasons + "AI 识别结果，请人工确认").distinct(),
                )
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            warnings += "AI 识别失败：${error.message ?: "未知错误"}"
            emptyList()
        }
    }
}
