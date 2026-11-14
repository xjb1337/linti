package com.xjb.linti.data.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.xjb.linti.domain.QuestionImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.roundToInt

/** Content-addressed, normalized files shared by questions and immutable exam snapshots. */
class QuestionImageStore(val directory: File) {
    private val held = mutableMapOf<String, Int>()
    @Synchronized fun retain(image: QuestionImage) { held[image.fileName] = (held[image.fileName] ?: 0) + 1 }
    @Synchronized fun release(image: QuestionImage) {
        val count = held[image.fileName] ?: return
        if (count <= 1) held.remove(image.fileName) else held[image.fileName] = count - 1
    }
    @Synchronized fun retainedNames(): Set<String> = held.keys.toSet()

    companion object {
        const val MAX_IMAGES = 4
        const val MAX_EDGE = 2048
        const val MAX_INPUT_BYTES = 30 * 1024 * 1024
        val FILE_NAME = Regex("[0-9a-f]{64}\\.(jpg|png)")
        fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    @Synchronized fun import(input: InputStream): QuestionImage {
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_INPUT_BYTES) { "图片过大" }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法读取图片" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE) sample *= 2
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
        })) { "无法读取图片" }
        var oriented = bitmap
        var resized = bitmap
        try {
            val orientation = runCatching {
                ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            val factor = minOf(1f, MAX_EDGE.toFloat() / max(oriented.width, oriented.height))
            resized = if (factor < 1f) Bitmap.createScaledBitmap(
                oriented, max(1, (oriented.width * factor).roundToInt()), max(1, (oriented.height * factor).roundToInt()), true,
            ) else oriented
            val png = resized.hasAlpha()
            val output = ByteArrayOutputStream()
            check(resized.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 85, output)) { "图片压缩失败" }
            val normalized = output.toByteArray()
            val hash = sha256(normalized)
            val fileName = hash + if (png) ".png" else ".jpg"
            directory.mkdirs()
            val target = File(directory, fileName)
            if (!target.exists() || target.length() != normalized.size.toLong() || sha256(target.readBytes()) != hash) {
                val temporary = File.createTempFile("image-", ".tmp", directory)
                try {
                    temporary.writeBytes(normalized)
                    check(temporary.renameTo(target) || (target.isFile && target.readBytes().contentEquals(normalized))) { "图片保存失败" }
                } finally { temporary.delete() }
            }
            return QuestionImage(fileName, resized.width, resized.height).also(::retain)
        } finally {
            if (resized !== oriented && resized !== bitmap) resized.recycle()
            if (oriented !== bitmap) oriented.recycle()
            bitmap.recycle()
        }
    }

    fun file(image: QuestionImage): File {
        require(FILE_NAME.matches(image.fileName)) { "图片文件名无效" }
        return File(directory, image.fileName)
    }
}

/** Deletes only unreferenced, content-addressed images; callers supply every live reference. */
class QuestionImageCleaner(private val directory: File) {
    fun clean(referencedNames: Set<String>): List<String> = directory.listFiles().orEmpty()
        .filter { it.isFile && QuestionImageStore.FILE_NAME.matches(it.name) && it.name !in referencedNames }
        .mapNotNull { if (it.delete()) it.name else null }
}
