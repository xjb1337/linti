package com.xjb.linti.data.import

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal object ZipSupport {
    private const val MAX_ENTRY_BYTES = 30 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 80 * 1024 * 1024

    fun readEntries(
        input: InputStream,
        maxEntries: Int = 250,
        selector: (String) -> Boolean,
    ): Map<String, ByteArray> {
        val result = linkedMapOf<String, ByteArray>()
        var total = 0L
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++count > maxEntries) break
                if (!entry.isDirectory && selector(entry.name)) {
                    val bytes = readLimited(zip, MAX_ENTRY_BYTES)
                    total += bytes.size
                    if (total > MAX_TOTAL_BYTES) {
                        throw IllegalArgumentException("压缩文档内容过大，已停止读取")
                    }
                    result[entry.name] = bytes
                }
                zip.closeEntry()
            }
        }
        return result
    }

    private fun readLimited(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) {
                throw IllegalArgumentException("单个文档条目过大，已停止读取")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
