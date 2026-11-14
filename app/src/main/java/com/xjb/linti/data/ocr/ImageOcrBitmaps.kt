package com.xjb.linti.data.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RectF
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * OCR 图片的本地位图处理（无网络）：
 * - 预览解码：限制解码像素并扶正 EXIF（含翻转）；
 * - 上传副本：按原图（EXIF 扶正 + 用户旋转）坐标裁切，限制解码像素，
 *   压缩只作用于上传副本，且一律编码为 JPEG——文件名/MIME 由 OcrUploadNaming 统一为 .jpg。
 * 抽离成独立对象以便设备测试直接驱动（PNG 输入 → multipart 一致性回归）。
 */
object ImageOcrBitmaps {

    /** 解码预览图：限制解码像素并扶正 EXIF（含翻转）；不做网络与压缩。 */
    fun decodeExifCorrectedPreview(path: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            error("图片无法解码，请重拍或更换图片")
        }
        val sample = previewSampleSize(bounds.outWidth, bounds.outHeight, PREVIEW_TARGET_SIDE)
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(path, options)
            ?: error("图片无法解码，请重拍或更换图片")
        val matrix = Matrix()
        applyExifTransform(matrix, readExifOrientation(path))
        if (matrix.isIdentity) return decoded
        val corrected = Bitmap.createBitmap(
            decoded, 0, 0, decoded.width, decoded.height, matrix, true,
        )
        if (corrected != decoded) decoded.recycle()
        return corrected
    }

    /**
     * 生成上传副本：按原图（EXIF 扶正 + 用户旋转）坐标裁切，限制解码像素，
     * 压缩只作用于该副本（JPEG）。返回临时文件，调用方负责清理。
     */
    fun prepareUploadCopy(
        sourcePath: String,
        rotationDegrees: Int,
        previewCropRect: CropRect,
        previewWidth: Float,
        previewHeight: Float,
        outputDir: File,
    ): File {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourcePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw OcrException(OcrFailureKind.BAD_REQUEST, "图片无法解码，请重拍或更换图片")
        }
        val orientation = readExifOrientation(sourcePath)
        val totalDegrees = exifRotationDegrees(orientation) + rotationDegrees
        // 显示空间（EXIF 扶正 + 用户旋转后）的整图尺寸
        val swapDimensions = ((totalDegrees % 180) + 180) % 180 != 0
        val displayWidth = if (swapDimensions) bounds.outHeight.toFloat() else bounds.outWidth.toFloat()
        val displayHeight = if (swapDimensions) bounds.outWidth.toFloat() else bounds.outHeight.toFloat()
        val fullRect = ImageCropMath.normalize(
            ImageCropMath.scaleRect(
                rect = previewCropRect,
                previewWidth = previewWidth,
                previewHeight = previewHeight,
                fullWidth = displayWidth,
                fullWidthHeight = displayHeight,
            ),
            displayWidth,
            displayHeight,
        )

        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        applyExifTransform(matrix, orientation)

        val sample = uploadSampleSize(bounds.outWidth, bounds.outHeight)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(sourcePath, decodeOptions)
            ?: throw OcrException(OcrFailureKind.BAD_REQUEST, "图片无法解码，请重拍或更换图片")
        try {
            // 采样后坐标与原图差 1/sample：先按比例放大回原图坐标再套用原变换
            val sampledMatrix = Matrix(matrix)
            sampledMatrix.postScale(sample.toFloat(), sample.toFloat())
            val inverse = Matrix()
            if (!sampledMatrix.invert(inverse)) {
                throw OcrException(OcrFailureKind.BAD_REQUEST, "图片变换失败，请重试")
            }
            val sourceRect = RectF(
                fullRect.left, fullRect.top, fullRect.right, fullRect.bottom,
            )
            inverse.mapRect(sourceRect)
            val cropLeft = sourceRect.left.toInt().coerceAtLeast(0)
            val cropTop = sourceRect.top.toInt().coerceAtLeast(0)
            val cropWidth = sourceRect.width().toInt().coerceAtMost(decoded.width - cropLeft)
            val cropHeight = sourceRect.height().toInt().coerceAtMost(decoded.height - cropTop)
            if (cropWidth <= 0 || cropHeight <= 0) {
                throw OcrException(OcrFailureKind.BAD_REQUEST, "裁切区域无效，请重新调整后上传")
            }
            val cropped = if (matrix.isIdentity &&
                ImageCropMath.isFullImage(fullRect, displayWidth, displayHeight)
            ) {
                decoded
            } else {
                Bitmap.createBitmap(
                    decoded, cropLeft, cropTop, cropWidth, cropHeight, sampledMatrix, true,
                )
            }
            try {
                val upload = scaledUploadBitmap(cropped)
                val file = File.createTempFile("ocr_upload_", ".jpg", outputDir)
                file.outputStream().use { output ->
                    if (!upload.compress(Bitmap.CompressFormat.JPEG, OcrLimits.UPLOAD_JPEG_QUALITY, output)) {
                        throw OcrException(OcrFailureKind.BAD_REQUEST, "上传副本生成失败，请重试")
                    }
                }
                if (upload != cropped) upload.recycle()
                return file
            } finally {
                if (cropped != decoded) cropped.recycle()
            }
        } finally {
            decoded.recycle()
        }
    }

    fun readExifOrientation(path: String): Int = runCatching {
        ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    fun exifRotationDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_TRANSPOSE,
        -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSVERSE,
        -> 270
        else -> 0
    }

    /** EXIF 扶正矩阵（含翻转）；变换顺序按 orientation 常量的标准定义。 */
    fun applyExifTransform(matrix: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postScale(-1f, 1f)
                matrix.postRotate(90f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postScale(-1f, 1f)
                matrix.postRotate(-90f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
    }

    private fun scaledUploadBitmap(source: Bitmap): Bitmap {
        val maxSide = maxOf(source.width, source.height)
        if (maxSide <= OcrLimits.UPLOAD_MAX_DIMENSION_PX) return source
        val scale = OcrLimits.UPLOAD_MAX_DIMENSION_PX.toFloat() / maxSide
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun previewSampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (maxOf(width, height) / sample > maxSide) sample *= 2
        return sample
    }

    private fun uploadSampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (width.toLong() * height.toLong() / (sample.toLong() * sample) >
            OcrLimits.PREVIEW_MAX_PIXELS.toLong()
        ) {
            sample *= 2
        }
        return sample
    }

    private const val PREVIEW_TARGET_SIDE = 2048
}
