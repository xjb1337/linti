package com.xjb.linti.data.import

import com.xjb.linti.domain.QuestionType
import java.text.Normalizer
import java.util.Locale

data class TabularConversion(
    val text: String,
    val warnings: List<String> = emptyList(),
)

object TabularQuestionMapper {
    private data class ColumnMapping(
        val sourceNumber: Int?,
        val stem: Int,
        val options: Map<String, Int>,
        val genericOption: Int?,
        val answer: Int?,
        val explanation: Int?,
        val type: Int?,
    )

    private data class HeaderDetection(
        val hasHeader: Boolean,
        val mapping: ColumnMapping,
        val notes: List<String>,
    )

    fun convert(rows: List<List<String>>, sourceLabel: String): TabularConversion {
        if (rows.isEmpty()) {
            return TabularConversion("", listOf("$sourceLabel 没有可读取的数据行"))
        }
        val detection = detectHeader(rows.first())
        val dataRows = if (detection.hasHeader) rows.drop(1) else rows
        if (dataRows.isEmpty()) {
            return TabularConversion("", listOf("$sourceLabel 只有表头，没有题目行"))
        }

        val warnings = detection.notes.toMutableList()
        val output = StringBuilder()
        var rowNumber = 0
        dataRows.forEachIndexed { index, row ->
            if (row.all { it.isBlank() }) return@forEachIndexed
            rowNumber++
            val source = detection.mapping.sourceNumber
                ?.let(row::getOrNull)
                ?.takeIf { it.isNotBlank() }
                ?: rowNumber.toString()
            val stem = detection.mapping.stem
                .let(row::getOrNull)
                .orEmpty()
                .replace('\n', ' ')
                .trim()
            if (stem.isBlank()) {
                warnings += "$sourceLabel 第 ${index + if (detection.hasHeader) 2 else 1} 行缺少题干，已跳过"
                return@forEachIndexed
            }
            val rawType = detection.mapping.type
                ?.let(row::getOrNull)
                .orEmpty()
                .trim()
            val normalizedType = normalizeTypeLabel(rawType)
            if (rawType.isNotBlank() && normalizedType == null) {
                warnings += "$sourceLabel 第 ${index + if (detection.hasHeader) 2 else 1} 行题型「$rawType」未识别"
            }
            output.append(source).append(". ")
            if (normalizedType != null) {
                output.append('[').append(normalizedType).append("] ")
            }
            output.append(stem).append('\n')

            val options = mutableMapOf<String, String>()
            detection.mapping.options.forEach { (label, column) ->
                val value = row.getOrNull(column).orEmpty().trim()
                if (value.isNotBlank()) options[label] = value
            }
            detection.mapping.genericOption?.let { column ->
                val value = row.getOrNull(column).orEmpty()
                options.putAll(splitGenericOptions(value))
            }
            options.toSortedMap().forEach { (label, value) ->
                output.append(label).append(". ").append(value.replace('\n', ' ')).append('\n')
            }

            val answer = detection.mapping.answer
                ?.let(row::getOrNull)
                .orEmpty()
                .trim()
            if (answer.isNotBlank()) {
                output.append("答案：").append(answer).append('\n')
            }
            val explanation = detection.mapping.explanation
                ?.let(row::getOrNull)
                .orEmpty()
                .trim()
            if (explanation.isNotBlank()) {
                output.append("解析：").append(explanation.replace('\n', ' ')).append('\n')
            }
            output.append('\n')
        }
        if (output.isBlank()) {
            warnings += "$sourceLabel 未转换出任何题目"
        }
        return TabularConversion(
            text = output.toString().trim(),
            warnings = warnings.distinct(),
        )
    }

    private fun detectHeader(row: List<String>): HeaderDetection {
        val normalized = row.map(::normalizeHeader)
        var sourceNumber: Int? = null
        var stem: Int? = null
        var answer: Int? = null
        var explanation: Int? = null
        var type: Int? = null
        var genericOption: Int? = null
        val options = mutableMapOf<String, Int>()

        normalized.forEachIndexed { index, header ->
            when {
                header in setOf("题号", "编号", "序号", "no", "number", "n") -> sourceNumber = index
                header in setOf("题干", "题目", "问题", "试题", "stem", "question") -> stem = index
                header in setOf("答案", "正确答案", "answer", "key") -> answer = index
                header in setOf("解析", "答案解析", "explanation", "analysis") -> explanation = index
                header in setOf("题型", "类型", "type") -> type = index
                else -> {
                    val label = optionLabel(header)
                    if (label != null) {
                        options[label] = index
                    } else if (header in setOf("选项", "options", "option")) {
                        genericOption = index
                    }
                }
            }
        }

        val hasHeader = stem != null || genericOption != null
        if (hasHeader) {
            val fallbackStem = stem ?: normalized.indexOfFirst {
                it.contains("题干") || it.contains("题目")
            }.takeIf { it >= 0 }
            if (fallbackStem == null) {
                return HeaderDetection(
                    hasHeader = false,
                    mapping = inferHeaderlessMapping(row.size),
                    notes = listOf("表格首行未找到题干列，按无表头常见列顺序尝试"),
                )
            }
            val mapping = ColumnMapping(
                sourceNumber = sourceNumber,
                stem = fallbackStem,
                options = options,
                genericOption = genericOption,
                answer = answer,
                explanation = explanation,
                type = type,
            )
            val optionNote = options.entries.sortedBy { it.key }
                .joinToString("、") { "选项${it.key}→第${it.value + 1}列" }
            val note = buildString {
                append("表格表头映射：题干→第").append(fallbackStem + 1).append("列")
                if (optionNote.isNotBlank()) append("，").append(optionNote)
                answer?.let { append("，答案→第").append(it + 1).append("列") }
                explanation?.let { append("，解析→第").append(it + 1).append("列") }
                type?.let { append("，题型→第").append(it + 1).append("列") }
            }
            return HeaderDetection(true, mapping, listOf(note))
        }

        return HeaderDetection(
            hasHeader = false,
            mapping = inferHeaderlessMapping(row.size),
            notes = listOf("未检测到表头，按常见列顺序：题号、题干、选项A-D、答案、解析、题型"),
        )
    }

    private fun inferHeaderlessMapping(columnCount: Int): ColumnMapping {
        if (columnCount <= 1) {
            return ColumnMapping(null, 0, emptyMap(), null, null, null, null)
        }
        val sourceNumber = if (columnCount >= 6) 0 else null
        val stem = if (sourceNumber != null) 1 else 0
        val tail = when {
            columnCount >= 9 -> 3
            columnCount >= 8 -> 2
            columnCount >= 7 -> 2
            columnCount >= 6 -> 1
            else -> 0
        }
        val type = if (tail >= 3) columnCount - 1 else null
        val explanation = if (tail >= 2) columnCount - if (type != null) 2 else 1 else null
        val answer = if (tail >= 1) columnCount - if (type != null) 3 else if (explanation != null) 2 else 1 else null
        val optionStart = stem + 1
        val optionEndExclusive = answer ?: columnCount
        val options = mutableMapOf<String, Int>()
        var labelIndex = 0
        for (column in optionStart until optionEndExclusive) {
            if (labelIndex >= 8) break
            options[('A' + labelIndex).toString()] = column
            labelIndex++
        }
        return ColumnMapping(
            sourceNumber = sourceNumber,
            stem = stem,
            options = options,
            genericOption = null,
            answer = answer,
            explanation = explanation,
            type = type,
        )
    }

    private fun splitGenericOptions(value: String): Map<String, String> {
        if (value.isBlank()) return emptyMap()
        val labeled = Regex(
            """(?:^|[\n;；])\s*([A-Ha-h])\s*[.、)）:：]\s*""",
        ).findAll(value).toList()
        if (labeled.isNotEmpty()) {
            val result = mutableMapOf<String, String>()
            labeled.forEachIndexed { index, match ->
                val start = match.range.last + 1
                val end = if (index + 1 < labeled.size) labeled[index + 1].range.first else value.length
                val label = match.groupValues[1].uppercase(Locale.ROOT)
                val text = value.substring(start, end).trim().trim(';', '；', '\n')
                if (text.isNotBlank()) result[label] = text
            }
            if (result.isNotEmpty()) return result
        }
        return value.split('\n', ';', '；')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(8)
            .mapIndexed { index, text -> ('A' + index).toString() to text }
            .toMap()
    }

    private fun optionLabel(header: String): String? {
        val direct = Regex("""^(?:选项|option|opt)?([a-h])$""").matchEntire(header)
        if (direct != null) {
            return direct.groupValues[1].uppercase(Locale.ROOT)
        }
        val numeric = Regex("""^(?:选项|option|opt)([1-8])$""").matchEntire(header)
        if (numeric != null) {
            val index = numeric.groupValues[1].toInt() - 1
            return ('A' + index).toString()
        }
        return null
    }

    private fun normalizeHeader(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(Regex("[\\s_\\-:：.()（）]+"), "")

    private fun normalizeTypeLabel(raw: String): String? {
        val value = Normalizer.normalize(raw, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        return when {
            "多选" in value -> "多选题"
            "单选" in value -> "单选题"
            "判断" in value -> "判断题"
            "填空" in value -> "填空题"
            "问答" in value || "简答" in value || "essay" in value -> "问答题"
            else -> null
        }
    }
}
