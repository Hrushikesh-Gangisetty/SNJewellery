package com.snjewellery.admin.data.media

import android.content.Context
import androidx.core.content.FileProvider
import com.snjewellery.admin.BuildConfig
import com.snjewellery.admin.domain.media.CaptureTargets
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Capture targets under `cache/captures/`, shared through the
 * FileProvider declared in the manifest.
 *
 * The file is not created here. `FileProvider` opens the URI in write
 * mode and creates it then, so a capture the owner cancels leaves nothing
 * behind at all.
 */
@Singleton
class CacheCaptureTargets @Inject constructor(
    @ApplicationContext private val context: Context,
) : CaptureTargets {

    override fun newTarget(): String? {
        val directory = File(context.cacheDir, CAPTURE_DIRECTORY)
        // mkdirs() is false both when the directory could not be made and
        // when it already existed, so existence is checked separately.
        if (!directory.isDirectory && !directory.mkdirs()) return null

        // A random name rather than a counter or a timestamp: two
        // captures a second apart must not collide, and Coil keys its
        // memory cache on the URI, so a reused name would show the
        // previous photograph in the new thumbnail.
        val file = File(directory, "capture-${UUID.randomUUID()}.jpg")

        return FileProvider.getUriForFile(context, AUTHORITY, file).toString()
    }

    private companion object {
        /** Must match `path` in res/xml/file_paths.xml. */
        const val CAPTURE_DIRECTORY = "captures"

        /**
         * Must match `android:authorities` in the manifest, which carries
         * `${applicationId}` — so the debug build's `.debug` suffix is
         * part of it and this cannot be a literal.
         */
        val AUTHORITY = "${BuildConfig.APPLICATION_ID}.captures"
    }
}
