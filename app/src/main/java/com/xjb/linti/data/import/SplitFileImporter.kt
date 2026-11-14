package com.xjb.linti.data.import

import com.xjb.linti.data.images.QuestionImageStore
import com.xjb.linti.domain.DraftQuestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

data class SplitSlotOutcome(
    val error: String? = null,
    val warnings: List<String> = emptyList(),
)

sealed interface SplitImportOutcome {
    data class Success(
        val drafts: List<DraftQuestion>,
        val problems: Map<Int, SplitDraftProblem>,
        val warnings: List<String>,
        val questionKind: DocumentKind,
        val answerKind: DocumentKind,
    ) : SplitImportOutcome

    data class SlotErrors(
        val question: SplitSlotOutcome,
        val answer: SplitSlotOutcome,
    ) : SplitImportOutcome
}

// 分文件导入编排：试题文件走题目提取与解析（禁用顺序兜底、允许全角题号），
// 答案文件走独立的结构化读取与解析；两侧错误按槽位返回，另一侧已选文件保留。
class SplitFileImporter(
    private val extractorRegistry: DocumentExtractorRegistry,
    private val parser: LocalQuestionParser,
    private val answerFileReader: AnswerFileReader,
    private val imageStore: QuestionImageStore? = null,
) {
    suspend fun import(
        questionInput: InputStream,
        questionFileName: String,
        answerInput: InputStream,
        answerFileName: String,
    ): SplitImportOutcome = withContext(Dispatchers.IO) {
        val questionOutcome = readQuestions(questionInput, questionFileName)
        val answerRead = answerFileReader.read(answerInput, answerFileName)
        val answerOutcome = when (answerRead) {
            is AnswerFileReadResult.Failed -> SplitSlotOutcome(error = answerRead.error)
            is AnswerFileReadResult.Ok -> SplitSlotOutcome(warnings = answerRead.warnings)
        }
        if (questionOutcome.error != null || answerOutcome.error != null) {
            questionOutcome.questions.orEmpty().flatMap { it.images }.forEach { imageStore?.release(it) }
            return@withContext SplitImportOutcome.SlotErrors(
                question = questionOutcome.toSlot(),
                answer = answerOutcome,
            )
        }
        val answerParse = AnswerFileParser.parse((answerRead as AnswerFileReadResult.Ok).source)
        if (answerParse.error != null) {
            questionOutcome.questions.orEmpty().flatMap { it.images }.forEach { imageStore?.release(it) }
            return@withContext SplitImportOutcome.SlotErrors(
                question = questionOutcome.toSlot(),
                answer = SplitSlotOutcome(
                    error = answerParse.error,
                    warnings = answerOutcome.warnings + answerParse.diagnostics,
                ),
            )
        }
        val questions = questionOutcome.questions.orEmpty()
        val match = SplitFileMatcher.match(questions, answerParse.entries)
        SplitImportOutcome.Success(
            drafts = match.drafts,
            problems = match.problems,
            warnings = questionOutcome.warnings +
                answerOutcome.warnings +
                answerParse.diagnostics +
                match.warnings,
            questionKind = questionOutcome.kind,
            answerKind = answerRead.kind,
        )
    }

    private data class QuestionOutcome(
        val questions: List<DraftQuestion>?,
        val warnings: List<String>,
        val error: String?,
        val kind: DocumentKind,
    ) {
        fun toSlot() = SplitSlotOutcome(error = error, warnings = warnings)
    }

    private suspend fun readQuestions(
        input: InputStream,
        fileName: String,
    ): QuestionOutcome {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension !in setOf("txt", "docx", "doc", "pdf")) {
            return QuestionOutcome(
                questions = null,
                warnings = emptyList(),
                error = "试题文件仅支持 TXT、DOCX、PDF，当前为「$fileName」",
                kind = DocumentKind.TXT,
            )
        }
        val extraction = extractorRegistry.extract(input, fileName)
        if (extraction.text.isBlank()) {
            val error = when (extraction.kind) {
                DocumentKind.PDF -> "未能从 PDF 提取文字（可能是扫描件），请改用可复制文字的 PDF，或改用 TXT/DOCX"
                else -> extraction.warnings.firstOrNull() ?: "试题文件内容为空，请检查文件"
            }
            return QuestionOutcome(
                questions = null,
                warnings = extraction.warnings,
                error = error,
                kind = extraction.kind,
            )
        }
        val parsed = parser.parse(
            extraction.text,
            SplitParseOptions(
                sequentialAnswerFallback = false,
                fullWidthNumbers = true,
                consumeSingleEndAnswerPair = true,
                skipDuplicateNumberEndAnswers = true,
                allowNumberGaps = true,
            ),
        )
        if (parsed.questions.isEmpty()) {
            return QuestionOutcome(
                questions = null,
                warnings = extraction.warnings + parsed.warnings,
                error = "未从试题文件识别到题目，请检查题号格式（如 1. 题干、A/B 选项）",
                kind = extraction.kind,
            )
        }
        val imageWarnings = mutableListOf<String>()
        val withImages = DocxImages.attach(parsed.questions, extraction, imageStore, imageWarnings)
        return QuestionOutcome(
            questions = withImages,
            warnings = extraction.warnings + parsed.warnings + imageWarnings,
            error = null,
            kind = extraction.kind,
        )
    }
}
