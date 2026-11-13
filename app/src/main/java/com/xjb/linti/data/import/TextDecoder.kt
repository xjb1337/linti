package com.xjb.linti.data.import

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class DecodedText(
    val text: String,
    val charsetName: String,
    val usedFallbackEncoding: Boolean,
)

object TextDecoder {
    private val utf8 = Charsets.UTF_8
    private val gb18030: Charset = runCatching { Charset.forName("GB18030") }
        .getOrElse { Charset.defaultCharset() }

    fun decode(bytes: ByteArray): DecodedText {
        val withoutBom = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) {
            bytes.copyOfRange(3, bytes.size)
        } else {
            bytes
        }

        decodeStrict(withoutBom, utf8)?.let { text ->
            return DecodedText(text, utf8.name(), usedFallbackEncoding = false)
        }
        val fallback = decodeStrict(withoutBom, gb18030)
            ?: String(withoutBom, gb18030)
        return DecodedText(
            text = fallback,
            charsetName = gb18030.name(),
            usedFallbackEncoding = true,
        )
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? {
        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
