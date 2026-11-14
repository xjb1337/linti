package com.xjb.linti.data.ocr

/** 裁切矩形，浮点像素坐标（左/上/右/下），可作用于任意分辨率的同一坐标系。 */
data class CropRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * 裁切/旋转坐标换算（纯函数，无 Android 依赖）：
 * 预览缩放图上的矩形 → 全分辨率图坐标；用户旋转（顺时针 90° 步进）后矩形随图旋转。
 * 裁切始终按原图（EXIF 扶正后）坐标执行，压缩只作用于上传副本。
 */
object ImageCropMath {

    /** 把预览坐标矩形按 previewSize→fullSize 比例缩放。 */
    fun scaleRect(rect: CropRect, previewWidth: Float, previewHeight: Float, fullWidth: Float, fullWidthHeight: Float): CropRect {
        if (previewWidth <= 0f || previewHeight <= 0f) return rect
        val scaleX = fullWidth / previewWidth
        val scaleY = fullWidthHeight / previewHeight
        return CropRect(
            left = rect.left * scaleX,
            top = rect.top * scaleY,
            right = rect.right * scaleX,
            bottom = rect.bottom * scaleY,
        )
    }

    /** 矩形随图片顺时针旋转 degrees（90 的倍数）；width/height 为旋转前图尺寸。 */
    fun rotateRect(rect: CropRect, degrees: Int, width: Float, height: Float): CropRect =
        when (((degrees % 360) + 360) % 360) {
            90 -> CropRect(
                left = height - rect.bottom,
                top = rect.left,
                right = height - rect.top,
                bottom = rect.right,
            )
            180 -> CropRect(
                left = width - rect.right,
                top = height - rect.bottom,
                right = width - rect.left,
                bottom = height - rect.top,
            )
            270 -> CropRect(
                left = rect.top,
                top = width - rect.right,
                right = rect.bottom,
                bottom = width - rect.left,
            )
            else -> rect
        }

    /** 收敛为合法矩形（边界内、l≤r、t≤b）。 */
    fun normalize(rect: CropRect, width: Float, height: Float): CropRect {
        val l = rect.left.coerceIn(0f, width)
        val r = rect.right.coerceIn(0f, width)
        val t = rect.top.coerceIn(0f, height)
        val b = rect.bottom.coerceIn(0f, height)
        return CropRect(
            left = minOf(l, r),
            top = minOf(t, b),
            right = maxOf(l, r),
            bottom = maxOf(t, b),
        )
    }

    fun fullImageRect(width: Float, height: Float): CropRect =
        CropRect(0f, 0f, width, height)

    /** 是否接近整图（默认整图裁切时可跳过 Bitmap 裁切步骤）。 */
    fun isFullImage(rect: CropRect, width: Float, height: Float): Boolean {
        val normalized = normalize(rect, width, height)
        return normalized.width >= width * 0.995f && normalized.height >= height * 0.995f
    }
}
