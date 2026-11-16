package com.xjb.linti.data.images

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class ImageBitmapsTest {
    private fun writePicture(width: Int, height: Int): File {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = File(app.cacheDir, "decode-${width}x$height.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    @Test fun thumbnailDownsamplesLargeSource() {
        val file = writePicture(2048, 1024)
        val decoded = ImageBitmaps.decode(file.absolutePath, ImageBitmaps.THUMB_MAX_EDGE)!!
        assertEquals(512, decoded.width)
        assertEquals(256, decoded.height)
    }

    @Test fun smallSourceKeepsOriginalSize() {
        val file = writePicture(320, 200)
        val decoded = ImageBitmaps.decode(file.absolutePath, ImageBitmaps.THUMB_MAX_EDGE)!!
        assertEquals(320, decoded.width)
        assertEquals(200, decoded.height)
    }

    @Test fun secondCallHitsCache() {
        val file = writePicture(640, 480)
        val first = ImageBitmaps.decode(file.absolutePath, ImageBitmaps.THUMB_MAX_EDGE)
        val second = ImageBitmaps.decode(file.absolutePath, ImageBitmaps.THUMB_MAX_EDGE)
        assertSame(first, second)
    }

    @Test fun differentMaxEdgeDecodesSeparately() {
        val file = writePicture(2048, 1024)
        val thumb = ImageBitmaps.decode(file.absolutePath, ImageBitmaps.THUMB_MAX_EDGE)!!
        val full = ImageBitmaps.decode(file.absolutePath, ImageBitmaps.FULL_MAX_EDGE)!!
        assertTrue(full.width > thumb.width)
        assertEquals(2048, full.width)
    }

    @Test fun missingFileReturnsNull() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertNull(ImageBitmaps.decode(File(app.cacheDir, "missing.png").absolutePath, ImageBitmaps.THUMB_MAX_EDGE))
    }

    @Test fun peekReturnsCachedOnlyAndRejectsNonPositiveMaxEdge() {
        val file = writePicture(400, 300)
        assertNull(ImageBitmaps.peek(file.absolutePath, ImageBitmaps.DIALOG_MAX_EDGE))
        val decoded = ImageBitmaps.decode(file.absolutePath, ImageBitmaps.DIALOG_MAX_EDGE)
        assertSame(decoded, ImageBitmaps.peek(file.absolutePath, ImageBitmaps.DIALOG_MAX_EDGE))
        val error = runCatching { ImageBitmaps.decode(file.absolutePath, 0) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}