package com.snjewellery.admin.data.product

import androidx.core.net.toUri
import com.snjewellery.admin.data.remote.RequestFailureClassifier
import com.snjewellery.admin.domain.product.ProductImageRepository
import com.snjewellery.admin.domain.product.StoragePaths
import com.snjewellery.admin.domain.product.UploadImageResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.uploadAsFlow
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads to the `product-images` bucket.
 *
 * ── Why the flow form rather than plain `upload` ─────────────────────
 * `uploadAsFlow` emits [UploadStatus.Progress] as the request body is
 * written, which is the only way the SDK exposes how far a photograph
 * has got. M7.7 asks for progress that is *accurate*, so it comes from
 * the bytes actually written rather than from counting whole files.
 *
 * One honest limitation: those bytes are what the client has handed to
 * the socket, not what the far end has acknowledged. On a stalling
 * connection the bar can reach the end slightly before the upload does.
 * Every HTTP client on the platform reports it this way.
 *
 * ── The content type is stated, not inferred ─────────────────────────
 * The bucket's `allowed_mime_types` is exactly webp, jpeg and png, so a
 * guessed wildcard image type would be refused with an error that reads
 * like a permissions problem. Everything reaching here is a WebP,
 * because M7.6 makes it one.
 *
 * ── `upsert = false` ─────────────────────────────────────────────────
 * The image id is a fresh UUID, so a collision would mean the random
 * source is broken. Refusing to overwrite turns that into a loud failure
 * rather than a photograph silently replacing someone else's.
 */
@Singleton
class SupabaseProductImageRepository @Inject constructor(
    private val client: SupabaseClient,
    private val failures: RequestFailureClassifier,
) : ProductImageRepository {

    override suspend fun upload(
        productId: String,
        localUri: String,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit,
    ): UploadImageResult {
        val path = StoragePaths.productImage(productId, UUID.randomUUID().toString())
        val bucket = client.storage.from(StoragePaths.PRODUCT_IMAGES_BUCKET)

        return try {
            bucket.uploadAsFlow(path, localUri.toUri()) {
                upsert = false
                contentType = WEBP
            }.collect { status ->
                when (status) {
                    is UploadStatus.Progress ->
                        onProgress(status.totalBytesSend, status.contentLength)

                    // The path was known before the request; there is
                    // nothing in the response worth preferring over it.
                    is UploadStatus.Success -> Unit
                }
            }

            UploadImageResult.Uploaded(storagePath = path, url = bucket.publicUrl(path))
        } catch (e: CancellationException) {
            // Never swallowed: the save was cancelled or the scope closed,
            // and cancellation must keep propagating.
            throw e
        } catch (e: Exception) {
            UploadImageResult.Failed(failures.classify(e))
        }
    }

    private companion object {
        /** Ktor predefines GIF, JPEG, PNG and SVG, but not this one. */
        val WEBP = ContentType("image", "webp")
    }
}
