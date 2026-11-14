package com.xjb.linti.data.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * 校对页「查看原页」的 PDF 页渲染：用系统 PdfRenderer 渲染临时 PDF 的指定页（0 起）。
 * 输入是 OCR 导入期间保留的临时文件，不入库、不进备份；失败返回 null 由调用方提示。
 * 独立成对象以便设备测试用合成 PDF 实际渲染验证（含第 2 页）。
 */
object OcrSourcePageRenderer {

    fun renderPage(path: String, pageIndex: Int): Bitmap? = runCatching {
        val file = File(path)
        if (!file.exists()) return null
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val scale = RENDER_MAX_SIDE.toFloat() / maxOf(page.width, page.height)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }.getOrNull()

    private const val RENDER_MAX_SIDE = 1600
}
