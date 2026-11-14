package com.xjb.linti.data.import

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.net.URI

class DocxDocumentExtractor : DocumentExtractor {
    override suspend fun extract(input: InputStream, fileName: String): ExtractionResult {
        if (fileName.substringAfterLast('.', "").equals("doc", ignoreCase = true)) {
            return ExtractionResult("", DocumentKind.DOCX, listOf("暂不支持旧版 .doc，请另存为 .docx 后重试"))
        }
        val entries = runCatching {
            ZipSupport.readEntries(input) { name ->
                name == "word/document.xml" || name == "word/_rels/document.xml.rels" || name.startsWith("word/media/")
            }
        }.getOrElse { return ExtractionResult("", DocumentKind.DOCX, listOf("DOCX 读取失败：${it.message ?: "文件可能已损坏"}")) }
        val xml = entries["word/document.xml"]
            ?: return ExtractionResult("", DocumentKind.DOCX, listOf("DOCX 中未找到 word/document.xml"))
        val document = runCatching { XmlSupport.parse(xml) }.getOrElse {
            return ExtractionResult("", DocumentKind.DOCX, listOf("DOCX XML 解析失败：${it.message ?: "未知错误"}"))
        }
        val relationships = mutableMapOf<String, String>()
        entries["word/_rels/document.xml.rels"]?.let { relBytes ->
            val rels = XmlSupport.parse(relBytes).getElementsByTagNameNS("*", "Relationship")
            for (i in 0 until rels.length) {
                val rel = rels.item(i) as? Element ?: continue
                if (!rel.getAttribute("Type").endsWith("/image") || rel.getAttribute("TargetMode").equals("External", true)) continue
                val target = rel.getAttribute("Target")
                val resolved = runCatching { URI("word/document.xml").resolve(target).normalize() }.getOrNull() ?: continue
                val name = resolved.path?.removePrefix("/") ?: continue
                if (resolved.isAbsolute || !name.startsWith("word/media/") || name.contains("..") || name.contains('\\')) continue
                if (name in entries) relationships[rel.getAttribute("Id")] = name
            }
        }
        val pictures = linkedMapOf<String, ByteArray>()
        fun collect(node: Node, text: StringBuilder) {
            if (node.nodeType != Node.ELEMENT_NODE) return
            val element = node as Element
            when (element.localName ?: element.nodeName.substringAfter(':')) {
                "t" -> text.append(element.textContent.orEmpty())
                "tab" -> text.append('\t')
                "br", "cr" -> text.append('\n')
                "blip" -> {
                    val id = element.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed")
                    val bytes = relationships[id]?.let(entries::get)
                    if (bytes != null) {
                        val key = "image${pictures.size}"
                        pictures[key] = bytes
                        text.append(DocxImages.marker(key))
                    }
                }
                else -> {
                    var child = node.firstChild
                    while (child != null) { collect(child, text); child = child.nextSibling }
                }
            }
        }
        val paragraphs = mutableListOf<String>()
        val nodes = document.getElementsByTagNameNS("*", "p")
        for (index in 0 until nodes.length) {
            val paragraph = nodes.item(index) as? Element ?: continue
            val text = StringBuilder().also { collect(paragraph, it) }.toString().trim()
            if (text.isNotBlank()) paragraphs += text
        }
        return if (paragraphs.isEmpty()) ExtractionResult("", DocumentKind.DOCX, listOf("DOCX 未提取到文本，可改用 TXT 或图片 OCR"))
        else ExtractionResult(paragraphs.joinToString("\n"), DocumentKind.DOCX, embeddedImages = pictures)
    }
}
