package com.snjewellery.admin.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Turns a photograph off the camera or out of the gallery into the one
 * image that gets uploaded.
 *
 * ── The numbers, and where they are argued ───────────────────────────
 * 2048px on the longest edge, WebP, quality 82, under 900KB. The
 * reasoning is in [ADR-0005](docs/adr/0005-image-storage-and-renditions.md),
 * which set this task the question — it is a decision about the
 * catalogue's maximum image quality forever, not a tuning constant, and
 * it does not belong in a comment here.
 *
 * **These are provisional.** They were chosen against the website's own
 * `sizes` attributes, not against real jewellery on a real screen, and
 * ADR-0005 is explicit that the second check is the one that matters:
 * compression is irreversible, and a chain link or a gemstone facet lost
 * here cannot be recovered short of re-shooting the piece.
 *
 * ── Why EXIF orientation is applied rather than carried ──────────────
 * `BitmapFactory` ignores it, and re-encoding drops the tag, so a
 * photograph taken with the phone upright would arrive on the website
 * lying on its side. The rotation is baked into the pixels here, which
 * is also what lets every consumer downstream — the thumbnails, Supabase's
 * transformations, `next/image` — treat the file as simply what it looks
 * like.
 */
@Singleton
class PhotoCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Reads [source], writes the compressed result to [target], and says
     * whether it worked. [target] is left absent on failure rather than
     * half-written.
     */
    fun compress(source: Uri, target: File): Boolean {
        val encoded = try {
            val decoded = decode(source) ?: return false

            val prepared = prepare(decoded, orientationOf(source))
            if (prepared !== decoded) decoded.recycle()

            val bytes = encode(prepared)
            prepared.recycle()
            bytes
        } catch (e: OutOfMemoryError) {
            // Ordinarily catching an Error is wrong. Here it is the
            // documented failure mode of bitmap work on a phone with a
            // large sensor and a full heap, and the owner losing one
            // photograph is a great deal better than losing the form.
            null
        } ?: return false

        return try {
            target.outputStream().use { it.write(encoded) }
            true
        } catch (e: IOException) {
            target.delete()
            false
        }
    }

    /**
     * Decodes at the smallest power-of-two reduction that still leaves
     * more pixels than the target needs.
     *
     * Decoding a 50-megapixel photograph in full and then throwing 95% of
     * it away is how an image pipeline runs out of memory. `inSampleSize`
     * is the only reduction `BitmapFactory` does during the decode.
     */
    private fun decode(source: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }

        // `decodeStream` returns null by design while inJustDecodeBounds
        // is set — the answer comes back on `bounds`, not as a bitmap. So
        // the stream is null-checked on its own line: folding this into
        // `openStream(…)?.use { … } ?: return null` reads as if it
        // guards the stream and in fact aborts every single decode.
        // Caught by PhotoCompressorTest, not by reading it back.
        val boundsStream = openStream(source) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }

        val decodeStream = openStream(source) ?: return null
        return decodeStream.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        // Stops one step before the halving that would take it under the
        // target, so the exact scale below is always a reduction.
        while (max(width, height) / (sample * 2) >= MAX_EDGE_PX) sample *= 2
        return sample
    }

    /**
     * Applies the orientation and the exact scale in one pass, so the
     * pixels are resampled once rather than twice.
     *
     * An image already smaller than the target is **not** enlarged.
     * Upscaling adds bytes and no detail, and the website would rather
     * serve a small sharp photograph than a large soft one.
     */
    private fun prepare(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(ROTATE_QUARTER)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(ROTATE_HALF)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(ROTATE_THREE_QUARTER)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(ROTATE_QUARTER)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(ROTATE_THREE_QUARTER)
                matrix.postScale(-1f, 1f)
            }
        }

        val longest = max(bitmap.width, bitmap.height)
        if (longest > MAX_EDGE_PX) {
            val scale = MAX_EDGE_PX.toFloat() / longest
            matrix.postScale(scale, scale)
        }

        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Encodes at the best quality that comes in under the ceiling.
     *
     * The steps exist because file size depends on the photograph, not on
     * the settings: a ring on a plain white cloth compresses to a
     * fraction of a temple necklace covered in detail. A fixed quality
     * would either bloat the second or ruin the first. The lowest step is
     * a floor, not a target — if even that is over the ceiling the
     * photograph is uploaded anyway, because a slightly heavy image beats
     * refusing the owner's piece.
     */
    private fun encode(bitmap: Bitmap): ByteArray? {
        var encoded: ByteArray? = null

        for (quality in QUALITY_STEPS) {
            val stream = ByteArrayOutputStream()
            if (!bitmap.compress(webpFormat(), quality, stream)) return null

            encoded = stream.toByteArray()
            if (encoded.size <= MAX_BYTES) break
        }

        return encoded
    }

    private fun orientationOf(source: Uri): Int = try {
        openStream(source)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: IOException) {
        // No EXIF at all is the normal case for a gallery PNG, and an
        // unreadable tag is not worth failing an upload over. Both mean
        // "assume it is the right way up", which is what the absence of
        // the tag is defined to mean anyway.
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun openStream(source: Uri) = try {
        context.contentResolver.openInputStream(source)
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    /**
     * `WEBP` was split into lossy and lossless forms in API 30 and
     * deprecated. Below that the single constant is lossy whenever the
     * quality is under 100, which is the whole point here.
     */
    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private companion object {
        /** See the class note: argued in ADR-0005, not chosen here. */
        const val MAX_EDGE_PX = 2048
        const val MAX_BYTES = 900 * 1024
        val QUALITY_STEPS = intArrayOf(82, 70, 58)

        const val ROTATE_QUARTER = 90f
        const val ROTATE_HALF = 180f
        const val ROTATE_THREE_QUARTER = 270f
    }
}
