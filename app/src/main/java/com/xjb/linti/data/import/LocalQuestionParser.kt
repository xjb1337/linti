package com.xjb.linti.data.import

import com.xjb.linti.domain.AnswerNormalizer
import com.xjb.linti.domain.DraftQuestion
import com.xjb.linti.domain.ParseResult
import com.xjb.linti.domain.QuestionOption
import com.xjb.linti.domain.QuestionType
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min

class LocalQuestionParser : QuestionParser {
    private data class IndexedLine(
        val originalIndex: Int,
        val text: String,
    )

    private data class QuestionStart(
        val originalIndex: Int,
        val sourceNumber: String,
        val contentAfterNumber: String,
        val originalLine: String,
    )

    private data class EndAnswers(
        val byNumber: Map<Int, String>,
        val ordered: List<Pair<Int, String>>,
        val ambiguousNumbers: Set<Int>,
        val removedLineIndices: Set<Int>,
    )

    private enum class SegmentKind {
        CONTENT,
        ANSWER,
        EXPLANATION,
    }

    private data class Segment(
        val kind: SegmentKind,
        val text: String,
    )

    private data class Marker(
        val kind: SegmentKind,
        val start: Int,
        val end: Int,
    )

    private class MutableOption(
        val label: String,
        val text: StringBuilder = StringBuilder(),
    )

    private class BlockState {
        var explicitType: QuestionType? = null
        val stem = StringBuilder()
        val options = mutableListOf<MutableOption>()
        val answer = StringBuilder()
        val explanation = StringBuilder()
        var answerFound = false
        var explanationFound = false
    }

    override fun parse(text: String): ParseResult {
        val normalizedText = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\uFEFF", "")

        if (normalizedText.isBlank()) {
            return ParseResult(
                questions = emptyList(),
                warnings = listOf("输入内容为空，未识别到题目"),
            )
        }

        val rawLines = normalizedText.split('\n')
        val endAnswers = extractEndAnswers(rawLines)
        val lines = rawLines.mapIndexedNotNull { index, line ->
            if (index in endAnswers.removedLineIndices) null else IndexedLine(index, line)
        }
        val starts = lines.mapNotNull(::questionStart)

        if (starts.isEmpty()) {
            return ParseResult(
                questions = emptyList(),
                warnings = listOf("未识别到题目，请检查题号格式或改用手动录入"),
            )
        }

        val warnings = mutableListOf<String>()
        val fallbackAnswers = buildFallbackAnswerMap(starts, endAnswers, warnings)
        val parsed = mutableListOf<DraftQuestion>()

        starts.forEachIndexed { startIndex, start ->
            val lineOffset = lines.indexOfFirst { it.originalIndex == start.originalIndex }
            val endLineOffset = if (startIndex + 1 < starts.size) {
                lines.indexOfFirst { it.originalIndex == starts[startIndex + 1].originalIndex }
            } else {
                lines.size
            }
            val blockLines = lines.subList(lineOffset, endLineOffset)
            val fallback = start.sourceNumber.toIntOrNull()?.let(fallbackAnswers::get)
            val rawSnippet = blockLines.joinToString("\n") { it.text }
            parsed += parseBlock(
                start = start,
                blockLines = blockLines,
                fallbackAnswer = fallback,
                sourceFileName = "",
                rawSnippet = rawSnippet,
            )
        }

        val withNumbering = applyNumberingChecks(parsed)
        val matchedNumbers = withNumbering.mapNotNull { it.sourceNumber.toIntOrNull() }.toSet()
        val unmatched = endAnswers.byNumber.keys - matchedNumbers
        if (unmatched.isNotEmpty()) {
            warnings += "文末答案中的题号 ${unmatched.sorted().joinToString("、")} 未找到对应题目"
        }
        if (endAnswers.ambiguousNumbers.isNotEmpty()) {
            warnings += "文末答案中的题号 ${endAnswers.ambiguousNumbers.sorted().joinToString("、")} 重复，无法唯一匹配"
        }
        val filledFromEnd = withNumbering.count { q ->
            q.answer.isNotBlank() &&
                q.sourceNumber.toIntOrNull()?.let(fallbackAnswers::containsKey) == true
        }
        if (filledFromEnd > 0) {
            warnings += "已从文末答案列表回填 $filledFromEnd 道题"
        }

        return ParseResult(
            questions = withNumbering,
            warnings = warnings.distinct(),
        )
    }

    private fun parseBlock(
        start: QuestionStart,
        blockLines: List<IndexedLine>,
        fallbackAnswer: String?,
        sourceFileName: String,
        rawSnippet: String,
    ): DraftQuestion {
        val state = BlockState()
        if (!fallbackAnswer.isNullOrBlank()) {
            state.answer.append(fallbackAnswer)
        }

        blockLines.forEachIndexed { index, line ->
            val lineText = if (index == 0) start.contentAfterNumber else line.text
            if (lineText.isBlank()) return@forEachIndexed
            processLine(lineText, state)
        }

        val stem = collapseWhitespace(state.stem.toString())
        val rawOptions = state.options.map { option ->
            QuestionOption(
                label = option.label,
                text = collapseWhitespace(option.text.toString()),
            )
        }
        val nonEmptyOptions = rawOptions.filter { it.text.isNotBlank() }
        val optionLabels = nonEmptyOptions.map { it.label }
        val duplicateLabels = optionLabels.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val emptyOptionLabels = rawOptions.filter { it.text.isBlank() }.map { it.label }

        val answerRaw = AnswerNormalizer.normalizeFillForStorage(state.answer.toString())
        val answerLetters = AnswerNormalizer.normalizeLetters(answerRaw)
        val judgeAnswer = AnswerNormalizer.normalizeJudge(answerRaw)
        val type = inferType(
            explicitType = state.explicitType,
            optionCount = nonEmptyOptions.size,
            answerRaw = answerRaw,
            judgeAnswer = judgeAnswer,
            hasBlanks = hasFillBlank(stem),
        )

        val reasons = mutableListOf<String>()
        if (stem.isBlank()) {
            reasons += "题干为空，请补充题目内容"
        } else if (stem.length < 4) {
            reasons += "题干过短，请核对是否识别完整"
        }
        if (answerRaw.isBlank()) {
            reasons += "未找到答案，请手动确认"
        }
        if ((type == QuestionType.SINGLE || type == QuestionType.MULTI) &&
            nonEmptyOptions.size < 2
        ) {
            reasons += "选项不足（至少需要 2 个选项）"
        }
        if (duplicateLabels.isNotEmpty()) {
            reasons += "选项字母重复：${duplicateLabels.sorted().joinToString("、")}"
        }
        if (emptyOptionLabels.isNotEmpty()) {
            reasons += "选项 ${emptyOptionLabels.joinToString("、")} 内容为空"
        }

        val normalizedAnswer = when (type) {
            QuestionType.SINGLE, QuestionType.MULTI -> {
                if (answerRaw.isNotBlank() && answerLetters.isBlank()) {
                    reasons += "答案格式无法识别为选项字母"
                }
                if (state.explicitType == QuestionType.SINGLE && answerLetters.length > 1) {
                    reasons += "题型标注为单选，但答案包含多个选项字母"
                }
                val invalidLetters = answerLetters.filter { letter ->
                    optionLabels.isNotEmpty() && letter.toString() !in optionLabels
                }
                if (invalidLetters.isNotEmpty()) {
                    reasons += "答案字母 ${invalidLetters.toList().joinToString("")} 不在选项中"
                }
                answerLetters
            }
            QuestionType.JUDGE -> {
                if (answerRaw.isNotBlank() && judgeAnswer == null) {
                    reasons += "判断题答案无法归一为正确或错误"
                }
                judgeAnswer.orEmpty()
            }
            QuestionType.FILL -> answerRaw
        }

        if (state.explicitType == null &&
            answerRaw.isBlank() &&
            nonEmptyOptions.size < 2 &&
            !hasFillBlank(stem)
        ) {
            reasons += "题型不明确，已暂按单选题处理，请核对"
        }

        if (suspectHeaderOrFooter(rawSnippet)) {
            reasons += "疑似包含页眉、页脚或试卷标题，请核对题目边界"
        }

        val distinctReasons = reasons.distinct()
        return DraftQuestion(
            sourceNumber = start.sourceNumber,
            type = type,
            stem = stem,
            options = nonEmptyOptions,
            answer = normalizedAnswer,
            explanation = collapseExplanation(state.explanation.toString()),
            tags = null,
            knowledgePoint = null,
            confidence = confidenceFor(distinctReasons, answerRaw.isBlank()),
            needsReview = distinctReasons.isNotEmpty(),
            reviewReasons = distinctReasons,
            sourceFileName = sourceFileName,
            rawSnippet = rawSnippet.trim().take(8000),
        )
    }

    private fun processLine(line: String, state: BlockState) {
        splitSegments(line).forEach { segment ->
            when (segment.kind) {
                SegmentKind.ANSWER -> {
                    state.answerFound = true
                    appendBuilder(state.answer, segment.text, separator = " ")
                }
                SegmentKind.EXPLANATION -> {
                    state.explanationFound = true
                    appendBuilder(state.explanation, segment.text, separator = "\n")
                }
                SegmentKind.CONTENT -> processContentSegment(segment.text, state)
            }
        }
    }

    private fun processContentSegment(rawText: String, state: BlockState) {
        var text = rawText
        if (state.stem.isBlank() && state.options.isEmpty()) {
            val stripped = stripTypeHint(text)
            if (stripped != null) {
                state.explicitType = state.explicitType ?: stripped.first
                text = stripped.second
            }
        }
        if (text.isBlank()) return

        if (state.answerFound) {
            if (state.answer.isBlank() && !state.explanationFound) {
                appendBuilder(state.answer, text, separator = " ")
            } else {
                state.explanationFound = true
                appendBuilder(state.explanation, text, separator = "\n")
            }
            return
        }
        if (state.explanationFound) {
            appendBuilder(state.explanation, text, separator = "\n")
            return
        }

        val matches = OPTION_MARKER.findAll(text).toList()
        if (matches.isEmpty()) {
            if (state.options.isEmpty()) {
                appendBuilder(state.stem, text, separator = " ")
            } else {
                appendBuilder(state.options.last().text, text, separator = " ")
            }
            return
        }

        val before = text.substring(0, matches.first().range.first)
        appendContentBeforeOption(before, state)

        matches.forEachIndexed { index, match ->
            val label = normalizeOptionLabel(match.groupValues[1])
            val contentStart = match.range.last + 1
            val contentEnd = if (index + 1 < matches.size) {
                matches[index + 1].range.first
            } else {
                text.length
            }
            val option = MutableOption(label)
            appendBuilder(option.text, text.substring(contentStart, contentEnd), separator = " ")
            state.options += option
        }
    }

    private fun appendContentBeforeOption(text: String, state: BlockState) {
        if (text.isBlank()) return
        if (state.options.isEmpty()) {
            appendBuilder(state.stem, text, separator = " ")
        } else {
            appendBuilder(state.options.last().text, text, separator = " ")
        }
    }

    private fun stripTypeHint(text: String): Pair<QuestionType, String>? {
        val match = TYPE_HINT.find(text) ?: return null
        val keyword = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return null
        val type = when {
            keyword.startsWith("多选") -> QuestionType.MULTI
            keyword.startsWith("判断") -> QuestionType.JUDGE
            keyword.startsWith("填空") -> QuestionType.FILL
            else -> QuestionType.SINGLE
        }
        return type to text.substring(match.range.last + 1).trimStart()
    }

    private fun splitSegments(line: String): List<Segment> {
        val markers = mutableListOf<Marker>()
        ANSWER_MARKER.findAll(line).forEach { match ->
            markers += Marker(SegmentKind.ANSWER, match.range.first, match.range.last + 1)
        }
        EXPLANATION_MARKER.findAll(line).forEach { match ->
            markers += Marker(SegmentKind.EXPLANATION, match.range.first, match.range.last + 1)
        }
        if (markers.isEmpty()) {
            return listOf(Segment(SegmentKind.CONTENT, line))
        }
        val sorted = markers.sortedWith(compareBy<Marker> { it.start }.thenByDescending { it.end })
        val segments = mutableListOf<Segment>()
        var cursor = 0
        sorted.forEachIndexed { index, marker ->
            if (marker.start < cursor) return@forEachIndexed
            if (marker.start > cursor) {
                segments += Segment(SegmentKind.CONTENT, line.substring(cursor, marker.start))
            }
            val end = if (index + 1 < sorted.size) sorted[index + 1].start else line.length
            segments += Segment(marker.kind, line.substring(marker.end, end))
            cursor = end
        }
        if (cursor < line.length) {
            segments += Segment(SegmentKind.CONTENT, line.substring(cursor))
        }
        return segments
    }

    private fun inferType(
        explicitType: QuestionType?,
        optionCount: Int,
        answerRaw: String,
        judgeAnswer: String?,
        hasBlanks: Boolean,
    ): QuestionType {
        if (explicitType != null) return explicitType
        val letters = AnswerNormalizer.normalizeLetters(answerRaw)
        val normalizedRaw = Normalizer.normalize(answerRaw, Normalizer.Form.NFKC)
            .trim()
            .uppercase(Locale.ROOT)
        val ambiguousBooleanLetter = normalizedRaw in setOf("T", "F") && optionCount >= 2
        if (judgeAnswer != null && !ambiguousBooleanLetter) return QuestionType.JUDGE
        if (letters.length >= 2) return QuestionType.MULTI
        if (optionCount >= 2 && letters.length == 1) return QuestionType.SINGLE
        if (optionCount >= 2) return QuestionType.SINGLE
        if (hasBlanks) return QuestionType.FILL
        if (judgeAnswer != null) return QuestionType.JUDGE
        return QuestionType.SINGLE
    }

    private fun extractEndAnswers(lines: List<String>): EndAnswers {
        val removed = mutableSetOf<Int>()
        val ordered = mutableListOf<Pair<Int, String>>()
        var index = 0
        while (index < lines.size) {
            val markerMatch = END_ANSWER_MARKER.matchEntire(lines[index])
            if (markerMatch == null) {
                index++
                continue
            }
            val remainder = markerMatch.groupValues[1]
            val pairs = parseAnswerPairs(remainder)
            if (pairs.size >= 2) {
                removed += index
                ordered += pairs
                index++
                continue
            }

            val lookahead = mutableListOf<Pair<Int, String>>()
            val lookaheadIndices = mutableListOf<Int>()
            var cursor = index + 1
            while (cursor < lines.size && lookahead.size < 500) {
                val nextPairs = parseAnswerPairs(lines[cursor])
                if (nextPairs.isEmpty()) break
                lookahead += nextPairs
                lookaheadIndices += cursor
                cursor++
            }
            if (lookahead.size >= 2) {
                removed += index
                removed += lookaheadIndices
                ordered += lookahead
                index = cursor
            } else {
                index++
            }
        }

        val grouped = ordered.groupBy({ it.first }, { AnswerNormalizer.normalizeFillForStorage(it.second) })
        val byNumber = mutableMapOf<Int, String>()
        val ambiguous = mutableSetOf<Int>()
        grouped.forEach { (number, values) ->
            val distinct = values.distinct()
            if (distinct.size == 1) {
                byNumber[number] = distinct.first()
            } else {
                ambiguous += number
            }
        }
        return EndAnswers(
            byNumber = byNumber,
            ordered = ordered.distinctBy { it.first to it.second },
            ambiguousNumbers = ambiguous,
            removedLineIndices = removed,
        )
    }

    private fun parseAnswerPairs(text: String): List<Pair<Int, String>> =
        END_ANSWER_PAIR.findAll(text).mapNotNull { match ->
            val number = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val answer = match.groupValues[2].trim()
            if (answer.isBlank()) null else number to answer
        }.toList()

    private fun buildFallbackAnswerMap(
        starts: List<QuestionStart>,
        endAnswers: EndAnswers,
        warnings: MutableList<String>,
    ): Map<Int, String> {
        if (endAnswers.ordered.isEmpty()) return emptyMap()
        val numbers = starts.mapNotNull { it.sourceNumber.toIntOrNull() }
        val byNumberComplete = numbers.isNotEmpty() && numbers.all { it in endAnswers.byNumber }
        if (byNumberComplete) {
            return numbers.associateWith { endAnswers.byNumber.getValue(it) }
        }

        if (numbers.size == endAnswers.ordered.size &&
            endAnswers.ambiguousNumbers.isEmpty() &&
            numbers.distinct().size == numbers.size
        ) {
            warnings += "文末答案未能按题号唯一匹配，已按题目顺序回填，请逐题核对"
            val result = mutableMapOf<Int, String>()
            numbers.forEachIndexed { index, number ->
                result[number] = endAnswers.ordered[index].second
            }
            return result
        }

        return numbers.mapNotNull { number ->
            endAnswers.byNumber[number]?.let { number to it }
        }.toMap()
    }

    private fun applyNumberingChecks(questions: List<DraftQuestion>): List<DraftQuestion> {
        if (questions.size < 2) return questions
        val result = questions.toMutableList()
        var previousNumber: Int? = null
        var previousIndex: Int? = null
        result.forEachIndexed { index, question ->
            val current = question.sourceNumber.toIntOrNull()
            if (current == null) return@forEachIndexed
            val previous = previousNumber
            if (previous != null) {
                when {
                    current > previous + 1 -> {
                        val reason = "题号从 $previous 跳到 $current，请核对是否缺题或题号有误"
                        previousIndex?.let { result[it] = result[it].appendReviewReason(reason) }
                        result[index] = result[index].appendReviewReason(reason)
                    }
                    current == previous -> {
                        result[index] = result[index].appendReviewReason(
                            "题号 $current 重复出现，请核对题目边界",
                        )
                    }
                    current < previous -> {
                        result[index] = result[index].appendReviewReason(
                            "题号 $current 在 $previous 之后出现，顺序异常",
                        )
                    }
                }
            }
            previousNumber = current
            previousIndex = index
        }
        return result
    }

    private fun DraftQuestion.appendReviewReason(reason: String): DraftQuestion {
        if (reason in reviewReasons) return this
        return copy(
            needsReview = true,
            reviewReasons = reviewReasons + reason,
            confidence = min(confidence, 0.72),
        )
    }

    private fun questionStart(line: IndexedLine): QuestionStart? {
        val match = QUESTION_START.matchEntire(line.text) ?: return null
        val numberValue = match.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return null
        return QuestionStart(
            originalIndex = line.originalIndex,
            sourceNumber = numberValue,
            contentAfterNumber = match.groupValues[5].trimStart(),
            originalLine = line.text,
        )
    }

    private fun normalizeOptionLabel(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC).uppercase(Locale.ROOT).take(1)

    private fun hasFillBlank(stem: String): Boolean = FILL_BLANK.containsMatchIn(stem)

    private fun suspectHeaderOrFooter(text: String): Boolean =
        HEADER_FOOTER.containsMatchIn(text)

    private fun appendBuilder(builder: StringBuilder, raw: String, separator: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        if (builder.isNotEmpty()) builder.append(separator)
        builder.append(text)
    }

    private fun collapseWhitespace(text: String): String =
        text.replace('\u00A0', ' ')
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\s*\\n\\s*"), " ")
            .trim()

    private fun collapseExplanation(text: String): String =
        text.replace('\u00A0', ' ')
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    private fun confidenceFor(reasons: List<String>, answerMissing: Boolean): Double = when {
        reasons.isEmpty() -> 1.0
        answerMissing -> 0.45
        reasons.any { it.contains("选项") || it.contains("答案字母") } -> 0.55
        else -> 0.68
    }

    private companion object {
        val QUESTION_START = Regex(
            """^\s*(?:第\s*(\d+)\s*题\s*[：:.、]?|（\s*(\d+)\s*）\s*|\(\s*(\d+)\s*\)\s*|(\d+)\s*[.、)）]\s*)(.*)$""",
        )

        val OPTION_MARKER = Regex(
            """(?:^|\s)([A-Ha-hＡ-Ｈａ-ｈ])\s*[.、)）:：]\s*""",
        )

        val TYPE_HINT = Regex(
            """^\s*(?:[【\[（(]\s*(单选题?|多选题?|判断题?|填空题?)\s*[】\]）)]\s*[·:：、]?\s*|(?:题型\s*[:：]\s*)?(单选题?|多选题?|判断题?|填空题?)(?=\s|$|[·:：、]))""",
        )

        val ANSWER_MARKER = Regex(
            """(?:【\s*答案\s*】|(?:参考|正确|标准)?答案\s*[:：]|(?:参考|正确|标准)?答案\s+(?=(?:[A-Ha-hＡ-Ｈａ-ｈ]{1,8}|正确|错误|对|错|√|×|[TtFf]|true|false)(?:\s|$|[。；;，,])))""",
            RegexOption.IGNORE_CASE,
        )

        val EXPLANATION_MARKER = Regex(
            """(?:【\s*(?:答案)?解析\s*】|(?:答案\s*)?解析\s*[:：]|【\s*考点\s*】|考点\s*[:：])""",
            RegexOption.IGNORE_CASE,
        )

        val END_ANSWER_MARKER = Regex(
            """^\s*(?:参考\s*答案|标准\s*答案|答案汇总|答案)\s*[:：]?\s*(.*)$""",
            RegexOption.IGNORE_CASE,
        )

        val END_ANSWER_PAIR = Regex(
            """(\d+)\s*[.、)）:：]\s*(true|false|正确|错误|对|错|√|×|[A-Ha-h]{1,8}|[TtFf])(?=$|\s|[;；,，、])""",
            RegexOption.IGNORE_CASE,
        )

        val FILL_BLANK = Regex("""_{2,}|＿{2,}|（\s*）|\(\s*\)""")

        val HEADER_FOOTER = Regex(
            """(内部练习题|仅供内部|第\s*\d+\s*页|共\s*\d+\s*页|密封线|姓名\s*[:：]|学号\s*[:：]|班级\s*[:：])""",
        )
    }
}
