package com.snjewellery.admin.data.media

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.snjewellery.admin.BuildConfig
import com.snjewellery.admin.domain.media.StagedImages
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Staged photographs under `cache/staged/`, shared through the
 * FileProvider declared in the manifest.
 *
 * The cache is the right home for them. A staged photograph matters for
 * the minutes between choosing it and the M7.7 upload, and the system
 * reclaiming it afterwards costs nothing. `files/` would make deleting
 * every one of them this app's problem, and the one that was missed
 * would sit on the phone forever.
 */
@Singleton
class CacheStagedImages @Inject constructor(
    @ApplicationContext private val context: Context,
    private val compressor: PhotoCompressor,
) : StagedImages {

    /**
     * The file is deliberately **not** created here. `FileProvider` opens
     * the URI in write mode and creates it then, so a capture the owner
     * backs out of leaves nothing behind at all.
     */
    override fun newCaptureTarget(): String? = newFile(CAPTURE_PREFIX, JPEG_EXTENSION)?.let(::uriFor)

    /**
     * `Default`, not `IO`: this opens a few streams but the time is
     * dominated by decoding and re-encoding several megapixels, which is
     * processor work.
     */
    override suspend fun stage(sourceUri: String): String? = withContext(Dispatchers.Default) {
        val target = newFile(PHOTO_PREFIX, WEBP_EXTENSION) ?: return@withContext null

        // A half-written file is worse than none: it would render as a
        // broken thumbnail and upload as a corrupt photograph. The
        // compressor leaves nothing behind when it fails.
        if (!compressor.compress(sourceUri.toUri(), target)) return@withContext null

        uriFor(target)
    }

    /**
     * Bounds only — no pixels are decoded, so this costs a header read
     * rather than a bitmap.
     *
     * The threshold is deliberately not "taller than wide". A photograph
     * a few percent off square is a square photograph held slightly
     * crooked, and framing it 4:5 would letterbox it for no reason. The
     * portrait frame is for pieces that are genuinely long.
     */
    override suspend fun isPortrait(uri: String): Boolean = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = context.contentResolver.openInputStream(uri.toUri())
            ?: return@withContext false

        stream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

        bounds.outHeight > bounds.outWidth * PORTRAIT_THRESHOLD
    }

    /**
     * `cache/staged/x.webp` → `files/drafts/x.webp`, same filename.
     *
     * A rename, not a copy: both directories are inside this app's data
     * directory, so it is a directory-entry move rather than several
     * megabytes of I/O. The copy is only a fallback for the case where
     * the two somehow are not on one volume, which on a phone they are.
     *
     * The filename is carried across so nothing else has to learn a
     * second naming scheme, and so a URI already under `drafts/` is
     * recognisable and returned untouched.
     */
    override suspend fun retain(uri: String): String? = withContext(Dispatchers.IO) {
        val parsed = uri.toUri()
        if (parsed.authority != AUTHORITY) return@withContext null

        val segments = parsed.pathSegments
        val root = segments.firstOrNull() ?: return@withContext null
        val name = segments.lastOrNull() ?: return@withContext null

        // Already retained. Writing a draft again must not move a file
        // that has already been moved, and must not report failure.
        if (root == DRAFT_DIRECTORY) return@withContext uri

        val source = File(File(context.cacheDir, STAGING_DIRECTORY), name)
        if (!source.isFile) return@withContext null

        val directory = File(context.filesDir, DRAFT_DIRECTORY)
        if (!directory.isDirectory && !directory.mkdirs()) return@withContext null

        val target = File(directory, name)
        val moved = source.renameTo(target) || runCatching {
            source.copyTo(target, overwrite = true)
            source.delete()
            true
        }.getOrDefault(false)

        if (moved) uriFor(target) else null
    }

    override suspend fun discard(uri: String): Unit = withContext(Dispatchers.IO) {
        val target = uri.toUri()

        // Only ever this app's own staging area. A URI from somewhere
        // else reaching here would be a bug, and deleting whatever it
        // pointed at would be a considerably worse one.
        if (target.authority != AUTHORITY) return@withContext

        try {
            context.contentResolver.delete(target, null, null)
        } catch (e: IllegalArgumentException) {
            // A URI under our authority that resolves to no configured
            // path. Nothing to delete, and nothing worth failing over —
            // the photograph is already off the form.
        } catch (e: SecurityException) {
            // As above: the cache is reclaimable, so a file left behind
            // is untidy rather than broken.
        }
    }

    private fun newFile(prefix: String, extension: String): File? {
        val directory = File(context.cacheDir, STAGING_DIRECTORY)
        // mkdirs() is false both when the directory could not be made and
        // when it already existed, so existence is checked separately.
        if (!directory.isDirectory && !directory.mkdirs()) return null

        // A random name rather than a counter or a timestamp: two
        // photographs a second apart must not collide, and Coil keys its
        // memory cache on the URI, so a reused name would show the
        // previous photograph in the new thumbnail.
        return File(directory, "$prefix-${UUID.randomUUID()}.$extension")
    }

    private fun uriFor(file: File): String =
        FileProvider.getUriForFile(context, AUTHORITY, file).toString()

    private companion object {
        /** Both must match a `path` in res/xml/file_paths.xml. */
        const val STAGING_DIRECTORY = "staged"

        /**
         * Under `files/`, not the cache: a draft's photographs have to
         * outlive an eviction. See [retain].
         */
        const val DRAFT_DIRECTORY = "drafts"

        /**
         * Two prefixes because the directory holds two different things:
         * a `capture-` file is the camera's raw output and lives only
         * until it has been staged, while a `photo-` file is a finished
         * photograph on the form. Telling them apart matters when reading
         * the directory to work out what happened.
         */
        const val CAPTURE_PREFIX = "capture"
        const val PHOTO_PREFIX = "photo"
        const val JPEG_EXTENSION = "jpg"
        const val WEBP_EXTENSION = "webp"

        /**
         * 15% taller than wide before the portrait frame is used. Below
         * that it is a square photograph held slightly crooked.
         */
        const val PORTRAIT_THRESHOLD = 1.15

        /**
         * Must match `android:authorities` in the manifest, which carries
         * `${applicationId}` — so the debug build's `.debug` suffix is
         * part of it and this cannot be a literal.
         */
        val AUTHORITY = "${BuildConfig.APPLICATION_ID}.staged"
    }
}
