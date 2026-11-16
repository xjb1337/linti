package com.xjb.linti.data.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlin.math.max

/** 按目标长边分档降采样 + 内存缓存：缩略图不解原图，离屏回滚不重解。 */
object ImageBitmaps {
    const val THUMB_MAX_EDGE = 512
    const val DIALOG_MAX_EDGE = 1280
    const val FULL_MAX_EDGE = 2048

    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun decode(path: String, maxEdge: Int): Bitmap? {
        val key = "$path@$maxEdge"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // 淘汰不 recycle：被淘汰的 bitmap 可能仍被界面持有，回收交给 GC
        cache.put(key, bitmap)
        return bitmap
    }
}