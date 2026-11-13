package com.xjb.linti.data.import

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream

class XlsxDocumentExtractor : DocumentExtractor {
    override suspend fun extract(input: InputStream, fileName: String): ExtractionResult {
        if (fileName.substringAfterLast('.', "").equals("xls", ignoreCase = true)) {
            return ExtractionResult(
                text = "",
                kind = DocumentKind.XLSX,
                warnings = listOf("暂不支持旧版 .xls，请另存为 .xlsx 或 CSV 后重试"),
            )
        }
        val entries = runCatching {
            ZipSupport.readEntries(input) { name ->
                name == "xl/sharedStrings.xml" || name.matches(Regex("""xl/worksheets/sheet\d+\.xml"""))
            }
        }.getOrElse { error ->
            return ExtractionResult(
                text = "",
                kind = DocumentKind.XLSX,
                warnings = listOf("XLSX 读取失败：${error.message ?: "文件可能已损坏"}"),
            )
        }
        val sheetEntry = entries.keys
            .filter { it.startsWith("xl/worksheets/") }
            .minByOrNull { it }
            ?: return ExtractionResult(
                text = "",
                kind = DocumentKind.XLSX,
                warnings = listOf("XLSX 中未找到工作表"),
            )
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val rows = runCatching {
            parseWorksheet(entries.getValue(sheetEntry), sharedStrings)
        }.getOrElse { error ->
            return ExtractionResult(
                text = "",
                kind = DocumentKind.XLSX,
                warnings = listOf("XLSX 工作表解析失败：${error.message ?: "未知错误"}"),
            )
        }
        if (rows.isEmpty()) {
            return ExtractionResult(
                text = "",
                kind = DocumentKind.XLSX,
                warnings = listOf("XLSX 第一张工作表没有可读取的行"),
            )
        }
        val conversion = TabularQuestionMapper.convert(rows, "XLSX")
        return ExtractionResult(
            text = conversion.text,
            kind = DocumentKind.XLSX,
            warnings = conversion.warnings,
        )
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val document = XmlSupport.parse(bytes)
        val nodes = document.getElementsByTagNameNS("*", "si")
        val strings = mutableListOf<String>()
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            val text = StringBuilder()
            XmlSupport.collectText(
                node,
                onText = { text.append(it) },
                onTab = { text.append('\t') },
                onBreak = { text.append('\n') },
            )
            strings += text.toString()
        }
        return strings
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val document = XmlSupport.parse(bytes)
        val rowNodes = document.getElementsByTagNameNS("*", "row")
        val rows = mutableListOf<List<String>>()
        for (rowIndex in 0 until minOf(rowNodes.length, 5000)) {
            val rowNode = rowNodes.item(rowIndex) as? Element ?: continue
            val cells = rowNode.getElementsByTagNameNS("*", "c")
            val values = mutableMapOf<Int, String>()
            for (cellIndex in 0 until cells.length) {
                val cell = cells.item(cellIndex) as? Element ?: continue
                val reference = cell.getAttribute("r")
                val column = columnIndex(reference)
                val type = cell.getAttribute("t")
                val value = when (type) {
                    "s" -> childValue(cell, "v")?.toIntOrNull()
                        ?.let(sharedStrings::getOrNull)
                        .orEmpty()
                    "inlineStr" -> collectAllText(cell)
                    else -> childValue(cell, "v") ?: collectAllText(cell)
                }
                if (value.isNotBlank()) values[column] = value
            }
            if (values.isEmpty()) continue
            val maxColumn = values.keys.maxOrNull() ?: continue
            rows += (0..maxColumn).map { values[it].orEmpty() }
        }
        return rows
    }

    private fun childValue(cell: Element, localName: String): String? {
        val nodes = cell.getElementsByTagNameNS("*", localName)
        if (nodes.length == 0) return null
        return nodes.item(0).textContent
    }

    private fun collectAllText(node: Node): String {
        val text = StringBuilder()
        val textNodes = (node as? Element)?.getElementsByTagNameNS("*", "t")
        if (textNodes != null) {
            for (index in 0 until textNodes.length) {
                text.append(textNodes.item(index).textContent.orEmpty())
            }
        } else {
            text.append(node.textContent.orEmpty())
        }
        return text.toString()
    }

    private fun columnIndex(reference: String): Int {
        if (reference.isBlank()) return 0
        var result = 0
        var sawLetter = false
        for (char in reference) {
            if (char in 'A'..'Z') {
                sawLetter = true
                result = result * 26 + (char - 'A' + 1)
            } else if (char in 'a'..'z') {
                sawLetter = true
                result = result * 26 + (char - 'a' + 1)
            } else {
                if (sawLetter) break
            }
        }
        return (result - 1).coerceAtLeast(0)
    }
}
