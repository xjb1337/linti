package com.xjb.linti.data.backup

import android.graphics.BitmapFactory
import com.xjb.linti.data.db.ExamPaperCodec
import com.xjb.linti.data.images.QuestionImageStore
import com.xjb.linti.domain.QuestionImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Returns every image needed by the database payload, including immutable exam papers. */
fun backupImageReferences(payload: BackupPayload): Map<String, QuestionImage> {
    val result = linkedMapOf<String, QuestionImage>()
    fun add(image: QuestionImage) {
        require(QuestionImageStore.FILE_NAME.matches(image.fileName) && image.width in 1..2048 && image.height in 1..2048) { "备份图片引用无效" }
        val previous = result.putIfAbsent(image.fileName, image)
        require(previous == null || previous == image) { "备份图片尺寸不一致" }
    }
    payload.questionImages.forEach { add(QuestionImage(it.fileName, it.width, it.height)) }
    val chunks = payload.examPaperChunks.groupBy { it.sessionId }
    payload.examPapers.forEach { paper ->
        ExamPaperCodec.decode(paper, chunks[paper.sessionId].orEmpty()).forEach { question -> question.images.forEach(::add) }
    }
    return result
}

object BackupArchive {
    const val MAX_IMAGE_BYTES = 30 * 1024 * 1024
    const val MAX_JSON_BYTES = 64 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 256L * 1024 * 1024
    const val MAX_ENTRIES = 10000

    data class Staged(val directory: File, val json: String, val payload: BackupPayload, val images: Map<String, QuestionImage>)

    fun write(output: OutputStream, json: String, store: QuestionImageStore) {
        val payload = BackupCodec.decode(json)
        val refs = backupImageReferences(payload)
        val jsonBytes = json.toByteArray(Charsets.UTF_8)
        require(jsonBytes.size <= MAX_JSON_BYTES && refs.size + 1 <= MAX_ENTRIES) { "备份内容过大" }
        val totalBytes = refs.keys.sumOf { File(store.directory, it).length() } + jsonBytes.size
        require(totalBytes <= MAX_TOTAL_BYTES) { "备份内容过大" }
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(jsonBytes)
            zip.closeEntry()
            refs.forEach { (name, image) ->
                val file = File(store.directory, name)
                verifyImage(file, image)
                zip.putNextEntry(ZipEntry("images/$name"))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun stage(input: InputStream, parent: File): Staged {
        parent.mkdirs()
        val directory = java.nio.file.Files.createTempDirectory(parent.toPath(), "backup-stage-").toFile()
        try {
            var json: String? = null
            var total = 0L
            val names = mutableSetOf<String>()
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(names.size < MAX_ENTRIES && names.add(entry.name)) { "备份条目重复或过多" }
                    val name = entry.name
                    require(!name.startsWith('/') && !name.contains('\\') && name.split('/').none { it == ".." || it == "." }) { "备份路径无效" }
                    if (entry.isDirectory) {
                        require(name == "images/") { "备份目录无效" }
                        zip.closeEntry()
                        continue
                    }
                    require(name == "backup.json" || (name.startsWith("images/") && QuestionImageStore.FILE_NAME.matches(name.removePrefix("images/")))) { "备份条目无效" }
                    val limit = if (name == "backup.json") MAX_JSON_BYTES else MAX_IMAGE_BYTES
                    val output = if (name == "backup.json") ByteArrayOutputStream() else File(directory, name.removePrefix("images/")).outputStream()
                    var size = 0L
                    output.use { out ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            size += n; total += n
                            require(size <= limit && total <= MAX_TOTAL_BYTES) { "备份条目过大" }
                            out.write(buffer, 0, n)
                        }
                    }
                    if (output is ByteArrayOutputStream) json = output.toString(Charsets.UTF_8.name())
                    zip.closeEntry()
                }
            }
            val text = requireNotNull(json) { "备份缺少 backup.json" }
            val payload = BackupCodec.decode(text)
            require(payload.version == BackupCodec.CURRENT_VERSION) { "ZIP 备份版本无效" }
            val refs = backupImageReferences(payload)
            val actual = names.filter { it.startsWith("images/") && it != "images/" }.map { it.removePrefix("images/") }.toSet()
            require(actual == refs.keys) { "备份图片缺失或多余" }
            refs.forEach { (name, image) -> verifyImage(File(directory, name), image) }
            return Staged(directory, text, payload, refs)
        } catch (error: Throwable) {
            directory.deleteRecursively()
            throw error
        }
    }

    fun verifyImage(file: File, image: QuestionImage) {
        require(file.isFile && file.length() in 1..MAX_IMAGE_BYTES.toLong()) { "备份图片缺失或过大" }
        val bytes = file.readBytes()
        require(QuestionImageStore.sha256(bytes) == image.fileName.substringBefore('.')) { "备份图片校验失败" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val expectedMime = if (image.fileName.endsWith(".png")) "image/png" else "image/jpeg"
        require(bounds.outWidth == image.width && bounds.outHeight == image.height && bounds.outMimeType == expectedMime) { "备份图片尺寸或格式无效" }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        requireNotNull(bitmap) { "备份图片无法解码" }
        bitmap.recycle()
    }
}
