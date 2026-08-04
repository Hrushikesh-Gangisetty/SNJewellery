package com.snjewellery.admin.data.media

import android.content.Context
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
        /** Must match `path` in res/xml/file_paths.xml. */
        const val STAGING_DIRECTORY = "staged"

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
         * Must match `android:authorities` in the manifest, which carries
         * `${applicationId}` — so the debug build's `.debug` suffix is
         * part of it and this cannot be a literal.
         */
        val AUTHORITY = "${BuildConfig.APPLICATION_ID}.staged"
    }
}
