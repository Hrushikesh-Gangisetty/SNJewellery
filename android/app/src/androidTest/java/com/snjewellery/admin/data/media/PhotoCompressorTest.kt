package com.snjewellery.admin.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.max

/**
 * The compression target ADR-0005 set, asserted rather than eyeballed.
 *
 * These are the checkable half of M7.6. The other half — whether the
 * result is *visibly acceptable for jewellery detail* — is a judgement
 * about real photographs on a real screen that no assertion can make.
 *
 * Instrumented rather than local: every line of the compressor is
 * `Bitmap`, `BitmapFactory` or `ExifInterface`, and all three are
 * unimplemented stubs in a JVM unit test.
 */
@RunWith(AndroidJUnit4::class)
class PhotoCompressorTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val compressor = PhotoCompressor(context)
    private val scratch = mutableListOf<File>()

    @After
    fun cleanUp() {
        scratch.forEach { it.delete() }
    }

    @Test
    fun largePhotographIsResizedToTheDocumentedEdge() {
        val source = sourcePhotograph(width = 3000, height = 2000)
        val target = scratchFile("out.webp")

        assertTrue("compression reported failure", compressor.compress(source, target))

        val bounds = boundsOf(target)
        assertEquals("longest edge", MAX_EDGE, max(bounds.outWidth, bounds.outHeight))
        // 3:2 in, 3:2 out. A stretched photograph would be a worse defect
        // than a large one.
        assertEquals("aspect ratio", 3f / 2f, bounds.outWidth.toFloat() / bounds.outHeight, 0.01f)
    }

    @Test
    fun outputIsWebPUnderTheDocumentedCeiling() {
        val source = sourcePhotograph(width = 3000, height = 2000)
        val target = scratchFile("out.webp")

        assertTrue(compressor.compress(source, target))

        // The extension proves nothing; the container header does.
        val header = target.readBytes().copyOfRange(0, 12)
        assertEquals("RIFF", String(header, 0, 4, Charsets.US_ASCII))
        assertEquals("WEBP", String(header, 8, 4, Charsets.US_ASCII))

        assertTrue(
            "expected at most $MAX_BYTES bytes, got ${target.length()}",
            target.length() <= MAX_BYTES,
        )
    }

    /**
     * The website would rather serve a small sharp photograph than a
     * large soft one, so an image already under the target is left at its
     * own size.
     */
    @Test
    fun smallPhotographIsNotEnlarged() {
        val source = sourcePhotograph(width = 800, height = 600)
        val target = scratchFile("out.webp")

        assertTrue(compressor.compress(source, target))

        val bounds = boundsOf(target)
        assertEquals(800, bounds.outWidth)
        assertEquals(600, bounds.outHeight)
    }

    /**
     * The classic photograph bug: `BitmapFactory` ignores EXIF and
     * re-encoding drops the tag, so a phone held upright produces an
     * image that lies on its side everywhere downstream. A quarter turn
     * must come out with the edges swapped.
     */
    @Test
    fun exifRotationIsBakedIntoThePixels() {
        val source = sourcePhotograph(width = 1200, height = 800, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val target = scratchFile("out.webp")

        assertTrue(compressor.compress(source, target))

        val bounds = boundsOf(target)
        assertEquals("width after a quarter turn", 800, bounds.outWidth)
        assertEquals("height after a quarter turn", 1200, bounds.outHeight)
    }

    /** A JPEG with enough variation in it that it does not compress to nothing. */
    private fun sourcePhotograph(
        width: Int,
        height: Int,
        orientation: Int = ExifInterface.ORIENTATION_NORMAL,
    ): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        // Flat colour would compress to almost nothing and make the size
        // assertion meaningless. This is deliberately busy.
        var x = 0
        while (x < width) {
            var y = 0
            while (y < height) {
                paint.color = Color.rgb((x * 7) % 256, (y * 11) % 256, ((x + y) * 13) % 256)
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + 8).toFloat(), (y + 8).toFloat(), paint)
                y += 8
            }
            x += 8
        }

        val file = scratchFile("source-$width-$height-$orientation.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()

        if (orientation != ExifInterface.ORIENTATION_NORMAL) {
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }

        return Uri.fromFile(file)
    }

    private fun scratchFile(name: String): File =
        File(context.cacheDir, "compressor-test-$name").also(scratch::add)

    private fun boundsOf(file: File): BitmapFactory.Options =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, this)
        }

    private companion object {
        /** Must match PhotoCompressor, which takes them from ADR-0005. */
        const val MAX_EDGE = 2048
        const val MAX_BYTES = 900L * 1024
    }
}
