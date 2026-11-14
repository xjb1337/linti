package com.xjb.linti.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCropMathTest {

    @Test
    fun `scaleRect maps preview coordinates to full resolution`() {
        val rect = CropRect(10f, 20f, 30f, 60f)
        val scaled = ImageCropMath.scaleRect(
            rect,
            previewWidth = 100f,
            previewHeight = 200f,
            fullWidth = 1000f,
            fullWidthHeight = 4000f,
        )
        assertEquals(100f, scaled.left, 0.001f)
        assertEquals(400f, scaled.top, 0.001f)
        assertEquals(300f, scaled.right, 0.001f)
        assertEquals(1200f, scaled.bottom, 0.001f)
    }

    @Test
    fun `rotateRect follows clockwise 90 degree steps`() {
        val rect = CropRect(10f, 20f, 30f, 60f)
        val width = 100f
        val height = 200f

        // 90° CW：(x,y) → (H-y, x)
        val r90 = ImageCropMath.rotateRect(rect, 90, width, height)
        assertEquals(140f, r90.left, 0.001f)
        assertEquals(10f, r90.top, 0.001f)
        assertEquals(180f, r90.right, 0.001f)
        assertEquals(30f, r90.bottom, 0.001f)

        // 180°：(x,y) → (W-x, H-y)
        val r180 = ImageCropMath.rotateRect(rect, 180, width, height)
        assertEquals(70f, r180.left, 0.001f)
        assertEquals(140f, r180.top, 0.001f)
        assertEquals(90f, r180.right, 0.001f)
        assertEquals(180f, r180.bottom, 0.001f)

        // 270° CW：(x,y) → (y, W-x)
        val r270 = ImageCropMath.rotateRect(rect, 270, width, height)
        assertEquals(20f, r270.left, 0.001f)
        assertEquals(70f, r270.top, 0.001f)
        assertEquals(60f, r270.right, 0.001f)
        assertEquals(90f, r270.bottom, 0.001f)

        // 0° 不变
        assertEquals(rect, ImageCropMath.rotateRect(rect, 0, width, height))
    }

    @Test
    fun `normalize clamps and reorders edges`() {
        val normalized = ImageCropMath.normalize(
            CropRect(120f, -10f, -20f, 500f),
            width = 100f,
            height = 200f,
        )
        assertEquals(0f, normalized.left, 0.001f)
        assertEquals(0f, normalized.top, 0.001f)
        assertEquals(100f, normalized.right, 0.001f)
        assertEquals(200f, normalized.bottom, 0.001f)
        assertTrue(normalized.left <= normalized.right)
        assertTrue(normalized.top <= normalized.bottom)
    }

    @Test
    fun `isFullImage detects default whole-image rect`() {
        assertTrue(
            ImageCropMath.isFullImage(
                ImageCropMath.fullImageRect(3000f, 4000f),
                3000f,
                4000f,
            ),
        )
        assertFalse(
            ImageCropMath.isFullImage(
                CropRect(0f, 0f, 1500f, 4000f),
                3000f,
                4000f,
            ),
        )
    }
}
