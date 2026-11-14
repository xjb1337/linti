package com.xjb.linti.data.import

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream

// XLSX 首个工作表的行列读取：题目提取与分文件答案导入共用。
// 空单元格按列位置占位返回，保证缺失单元格不会让后续列左移。
object XlsxWorksheetReader {
    // 工作表选择：FILE_NAME 按文件名取最小（既有行为）；WORKBOOK_TAB 按
    // workbook.xml 的页签顺序取第一个 sheet（分文件答案读取用）。
    enum class SheetOrder { FILE_NAME, WORKBOOK_TAB }

    private const val WORKBOOK_ENTRY = "xl/workbook.xml"
    private const val WORKBOOK_RELS_ENTRY = "xl/_rels/workbook.xml.rels"
    private const val REL_NAMESPACE =
        "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    data class SheetRows(
        val rows: List<List<String>>,
        val error: String? = null,
    )

    fun read(input: InputStream, order: SheetOrder = SheetOrder.FILE_NAME): SheetRows {
        val entries = runCatching {
            ZipSupport.readEntries(input) { name ->
                name == "xl/sharedStrings.xml" ||
                    name == WORKBOOK_ENTRY ||
                    name == WORKBOOK_RELS_ENTRY ||
                    name.matches(Regex("""xl/worksheets/sheet\d+\.xml"""))
            }
        }.getOrElse { error ->
            return SheetRows(rows = emptyList(), error = "XLSX 读取失败：${error.message ?: "文件可能已损坏"}")
        }
        val sheetEntry = when (order) {
            SheetOrder.FILE_NAME -> entries.keys
                .filter { it.startsWith("xl/worksheets/") }
                .minByOrNull { it }
                ?: return SheetRows(rows = emptyList(), error = "XLSX 中未找到工作表")
            SheetOrder.WORKBOOK_TAB -> firstSheetByWorkbookOrder(entries)
                ?: return SheetRows(
                    rows = emptyList(),
                    error = "无法从 XLSX 工作簿确定首个工作表（页签关系缺失或损坏），请重新导出该文件后重试",
                )
        }
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()
        val rows = runCatching {
            parseWorksheet(entries.getValue(sheetEntry), sharedStrings)
        }.getOrElse { error ->
            return SheetRows(rows = emptyList(), error = "XLSX 工作表解析失败：${error.message ?: "未知错误"}")
        }
        return SheetRows(rows = rows)
    }

    // 解析 workbook.xml 的首个 <sheet r:id> 与 workbook.xml.rels 的 Id→Target，
    // 只认第一个页签：关系缺失、目标文件不存在或解析失败一律返回 null 报错，
    // 不跳到后续页签、不按文件名兜底，避免损坏工作簿静默配错整卷。
    private fun firstSheetByWorkbookOrder(entries: Map<String, ByteArray>): String? {
        val workbookBytes = entries[WORKBOOK_ENTRY] ?: return null
        val relsBytes = entries[WORKBOOK_RELS_ENTRY] ?: return null
        return runCatching {
            val rels = XmlSupport.parse(relsBytes)
            val targets = mutableMapOf<String, String>()
            val relNodes = rels.getElementsByTagNameNS("*", "Relationship")
            for (index in 0 until relNodes.length) {
                val node = relNodes.item(index) as? org.w3c.dom.Element ?: continue
                val id = node.getAttribute("Id")
                val target = node.getAttribute("Target")
                if (id.isNotBlank() && target.isNotBlank()) targets[id] = target
            }
            val workbook = XmlSupport.parse(workbookBytes)
            val sheetNodes = workbook.getElementsByTagNameNS("*", "sheet")
            val firstSheet = sheetNodes.item(0) as? org.w3c.dom.Element
                ?: return@runCatching null
            val relId = firstSheet.getAttribute("r:id").ifBlank {
                firstSheet.getAttributeNS(REL_NAMESPACE, "id")
            }
            val target = targets[relId] ?: return@runCatching null
            val entryName = when {
                target.startsWith("/") -> target.removePrefix("/")
                target.startsWith("xl/") -> target
                else -> "xl/$target"
            }
            entryName.takeIf { entries.containsKey(it) }
        }.getOrNull()
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
