package com.xjb.linti.data.import

import org.w3c.dom.Element
import java.io.InputStream

class DocxDocumentExtractor : DocumentExtractor {
    override suspend fun extract(input: InputStream, fileName: String): ExtractionResult {
        if (fileName.substringAfterLast('.', "").equals("doc", ignoreCase = true)) {
            return ExtractionResult(
                text = "",
                kind = DocumentKind.DOCX,
                warnings = listOf("暂不支持旧版 .doc，请另存为 .docx 后重试"),
            )
        }
        val entries = runCatching {
            ZipSupport.readEntries(input) { name -> name == "word/document.xml" }
        }.getOrElse { error ->
            return ExtractionResult(
                text = "",
                kind = DocumentKind.DOCX,
                warnings = listOf("DOCX 读取失败：${error.message ?: "文件可能已损坏"}"),
            )
        }
        val xml = entries["word/document.xml"]
            ?: return ExtractionResult(
                text = "",
                kind = DocumentKind.DOCX,
                warnings = listOf("DOCX 中未找到 word/document.xml"),
            )
        val document = runCatching { XmlSupport.parse(xml) }.getOrElse { error ->
            return ExtractionResult(
                text = "",
                kind = DocumentKind.DOCX,
                warnings = listOf("DOCX XML 解析失败：${error.message ?: "未知错误"}"),
            )
        }

        val paragraphs = mutableListOf<String>()
        val paragraphNodes = document.getElementsByTagNameNS("*", "p")
        for (index in 0 until paragraphNodes.length) {
            val paragraph = paragraphNodes.item(index) as? Element ?: continue
            val text = StringBuilder()
            XmlSupport.collectText(
                paragraph,
                onText = { text.append(it) },
                onTab = { text.append('\t') },
                onBreak = { text.append('\n') },
            )
            if (text.isNotBlank()) paragraphs += text.toString().trim()
        }

        return if (paragraphs.isEmpty()) {
            ExtractionResult(
                text = "",
                kind = DocumentKind.DOCX,
                warnings = listOf("DOCX 未提取到文本，可改用 TXT 或图片 OCR"),
            )
        } else {
            ExtractionResult(
                text = paragraphs.joinToString("\n"),
                kind = DocumentKind.DOCX,
            )
        }
    }
}
