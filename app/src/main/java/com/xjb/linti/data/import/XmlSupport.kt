package com.xjb.linti.data.import

import org.w3c.dom.Document
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

internal object XmlSupport {
    fun parse(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(bytes))
    }

    fun collectText(
        node: Node,
        onText: (String) -> Unit,
        onTab: () -> Unit,
        onBreak: () -> Unit,
    ) {
        when (node.nodeType) {
            Node.ELEMENT_NODE -> {
                val name = node.localName ?: node.nodeName.substringAfter(':')
                when (name) {
                    "t" -> onText(node.textContent.orEmpty())
                    "tab" -> onTab()
                    "br", "cr" -> onBreak()
                    else -> {
                        var child = node.firstChild
                        while (child != null) {
                            collectText(child, onText, onTab, onBreak)
                            child = child.nextSibling
                        }
                    }
                }
            }
        }
    }
}
