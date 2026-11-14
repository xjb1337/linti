package com.xjb.linti.data.import

import java.text.Normalizer

data class AnswerEntry(
    val number: Int,
    val numberText: String,
    val rawAnswer: String,
)

data class AnswerFileParseResult(
    val entries: List<AnswerEntry> = emptyList(),
    val diagnostics: List<String> = emptyList(),
    val error: String? = null,
)

// 题号键归一：只接受明确写出的正整数；兼容全角数字与前导零（０１、001 都归一到 1）。
// Excel 数值单元格可能带 ".0" 尾巴，一并归一。
fun normalizeQuestionNumber(raw: String): Int? {
    val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).trim()
    val digits = if (Regex("""^\d+\.0+$""").matches(normalized)) {
        normalized.substringBefore('.')
    } else {
        normalized
    }
    val number = digits.toIntOrNull() ?: return null
    return number.takeIf { it > 0 }
}

// 答案文件解析：TXT 一行一组（01.A / 2、AC / 3：正确，可空白分隔，可有「参考答案」标题）；
// CSV/XLSX 要求显式「题号」「答案」表头列（接受原题号、正确答案等明确同义词），
// 列可调换、可有多余列，缺失单元格不左移。不猜测：无法解析的行保留诊断。
object AnswerFileParser {
    private val NUMBER_HEADERS = setOf("题号", "原题号", "题目编号")
    private val ANSWER_HEADERS = setOf("答案", "正确答案", "参考答案", "标准答案")

    private val HEADER_LINE = Regex(
        """^\s*(?:参考\s*答案|标准\s*答案|答案汇总|答案)\s*[:：]?\s*(.*)$""",
    )

    private val ANSWER_LINE = Regex(
        """^\s*(?:第\s*([0-9０-９]+)\s*题\s*|[（(]\s*([0-9０-９]+)\s*[）)]\s*|([0-9０-９]+)\s*)(?:[.、．)）:：，,]|\s|$)\s*(.*)$""",
    )

    private val ANSWER_PREFIX = Regex("""^\s*(?:参考|正确|标准)?\s*答案\s*[:：]\s*""")

    private val PAIR_TOKEN = Regex("""^([0-9０-９]+)\s*[.、．)）:：]\s*(.+)$""")

    fun parse(source: AnswerFileSource): AnswerFileParseResult = when (source) {
        is AnswerFileSource.Lines -> parseLines(source.text)
        is AnswerFileSource.Grid -> parseGrid(source.rows, source.sourceLabel)
    }

    private fun parseLines(text: String): AnswerFileParseResult {
        val entries = mutableListOf<AnswerEntry>()
        val diagnostics = mutableListOf<String>()
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            val rowNumber = index + 1
            val header = HEADER_LINE.matchEntire(line)
            if (header != null) {
                val remainder = header.groupValues[1].trim()
                if (remainder.isNotEmpty()) {
                    parsePairTokens(remainder, rowNumber, entries, diagnostics)
                }
                return@forEachIndexed
            }
            val match = ANSWER_LINE.matchEntire(line)
            if (match == null) {
                diagnostics += "答案文件第 $rowNumber 行无法识别题号：「${line.take(24)}」"
                return@forEachIndexed
            }
            val numberText = match.groupValues.drop(1).take(3).firstOrNull { it.isNotBlank() } ?: ""
            val number = normalizeQuestionNumber(numberText)
            if (number == null) {
                diagnostics += "答案文件第 $rowNumber 行题号无效：「${numberText.take(16)}」"
                return@forEachIndexed
            }
            val answer = ANSWER_PREFIX.replace(match.groupValues[4], "").trim()
            if (answer.isEmpty()) {
                // 有效题号的空答案行计入重复：该题号不得自动匹配
                diagnostics += "答案文件第 $rowNumber 行答案为空"
                entries += AnswerEntry(number = number, numberText = numberText, rawAnswer = "")
                return@forEachIndexed
            }
            entries += AnswerEntry(number = number, numberText = numberText, rawAnswer = answer)
        }
        if (entries.isEmpty()) {
            return AnswerFileParseResult(
                error = "未从答案文件识别到任何题号答案对，请按「题号 答案」格式逐行填写（如 01.A）",
                diagnostics = diagnostics,
            )
        }
        return AnswerFileParseResult(entries = entries, diagnostics = diagnostics)
    }

    private fun parsePairTokens(
        text: String,
        rowNumber: Int,
        entries: MutableList<AnswerEntry>,
        diagnostics: MutableList<String>,
    ) {
        text.split(Regex("\\s+")).forEach { token ->
            if (token.isBlank()) return@forEach
            val match = PAIR_TOKEN.matchEntire(token)
            val number = match?.let { normalizeQuestionNumber(it.groupValues[1]) }
            val answer = match?.groupValues?.get(2)?.trim().orEmpty()
            if (number == null || answer.isEmpty()) {
                diagnostics += "答案文件第 $rowNumber 行「${token.take(24)}」无法识别为题号答案对"
                return@forEach
            }
            entries += AnswerEntry(number = number, numberText = match.groupValues[1], rawAnswer = answer)
        }
    }

    private fun parseGrid(rows: List<List<String>>, sourceLabel: String): AnswerFileParseResult {
        val header = when (val lookup = findHeader(rows)) {
            is HeaderLookup.Found -> lookup.header
            is HeaderLookup.Ambiguous -> return AnswerFileParseResult(
                error = "答案文件表头存在多个同义「${lookup.columns.joinToString("」「")}」列，" +
                    "无法确定用哪一列，请只保留一列后重试（$sourceLabel）",
            )
            null -> return AnswerFileParseResult(
                error = "答案文件缺少「题号」「答案」表头，请让首行包含题号与答案两列（$sourceLabel）",
            )
        }
        val entries = mutableListOf<AnswerEntry>()
        val diagnostics = mutableListOf<String>()
        for (index in header.rowIndex + 1 until rows.size) {
            val row = rows[index]
            if (row.all { it.isBlank() }) continue
            val rowNumber = index + 1
            val numberText = row.getOrNull(header.numberColumn).orEmpty().trim()
            val number = normalizeQuestionNumber(numberText)
            if (number == null) {
                diagnostics += "答案文件第 $rowNumber 行题号无效：「${numberText.take(16)}」"
                continue
            }
            val answer = row.getOrNull(header.answerColumn).orEmpty().trim()
            if (answer.isEmpty()) {
                // 有效题号的空答案行计入重复：该题号不得自动匹配
                diagnostics += "答案文件第 $rowNumber 行答案为空"
                entries += AnswerEntry(number = number, numberText = numberText, rawAnswer = "")
                continue
            }
            entries += AnswerEntry(number = number, numberText = numberText, rawAnswer = answer)
        }
        if (entries.isEmpty()) {
            return AnswerFileParseResult(
                error = "$sourceLabel 表头下没有可解析的题号答案数据，请检查列内容",
                diagnostics = diagnostics,
            )
        }
        return AnswerFileParseResult(entries = entries, diagnostics = diagnostics)
    }

    private data class GridHeader(
        val rowIndex: Int,
        val numberColumn: Int,
        val answerColumn: Int,
    )

    private sealed interface HeaderLookup {
        data class Found(val header: GridHeader) : HeaderLookup
        data class Ambiguous(val columns: List<String>) : HeaderLookup
    }

    private fun findHeader(rows: List<List<String>>): HeaderLookup? {
        rows.take(20).forEachIndexed { index, row ->
            val cells = row.map { normalizeHeaderCell(it) }
            val numberColumns = cells.withIndex().filter { it.value in NUMBER_HEADERS }.map { it.index }
            val answerColumns = cells.withIndex().filter { it.value in ANSWER_HEADERS }.map { it.index }
            // 同义表头出现多列：明确报错说明哪类列重复，不擅取其一
            val duplicated = buildList {
                if (numberColumns.size > 1) add("题号")
                if (answerColumns.size > 1) add("答案")
            }
            if (duplicated.isNotEmpty()) return HeaderLookup.Ambiguous(duplicated)
            val numberColumn = numberColumns.firstOrNull() ?: return@forEachIndexed
            val answerColumn = answerColumns.firstOrNull() ?: return@forEachIndexed
            if (answerColumn == numberColumn) return@forEachIndexed
            return HeaderLookup.Found(
                GridHeader(
                    rowIndex = index,
                    numberColumn = numberColumn,
                    answerColumn = answerColumn,
                ),
            )
        }
        return null
    }

    private fun normalizeHeaderCell(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFKC)
            .trim()
            .trimEnd(':', '：', ' ', '\u00A0')
            .trim()
}
