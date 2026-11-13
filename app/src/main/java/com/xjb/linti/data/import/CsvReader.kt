package com.xjb.linti.data.import

object CsvReader {
    fun parse(text: String): List<List<String>> {
        if (text.isBlank()) return emptyList()
        val delimiter = detectDelimiter(text)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        fun finishField() {
            row += field.toString().trim()
            field.clear()
        }

        fun finishRow() {
            finishField()
            if (row.any { it.isNotBlank() }) {
                rows += row.toList()
            }
            row = mutableListOf()
        }

        while (index < text.length) {
            val char = text[index]
            when {
                char == '"' -> {
                    if (inQuotes && index + 1 < text.length && text[index + 1] == '"') {
                        field.append('"')
                        index++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                char == delimiter && !inQuotes -> finishField()
                (char == '\n' || char == '\r') && !inQuotes -> {
                    if (char == '\r' && index + 1 < text.length && text[index + 1] == '\n') {
                        index++
                    }
                    finishRow()
                }
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            finishRow()
        }
        return rows
    }

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
        val candidates = listOf(',', '\t', ';')
        return candidates.maxByOrNull { firstLine.count { char -> char == it } } ?: ','
    }
}
