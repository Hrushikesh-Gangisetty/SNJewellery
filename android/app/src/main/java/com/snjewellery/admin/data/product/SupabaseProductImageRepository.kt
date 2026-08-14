package com.snjewellery.admin.data.product

import androidx.core.net.toUri
import com.snjewellery.admin.data.remote.RequestFailureClassifier
import com.snjewellery.admin.domain.product.ProductImageRepository
import com.snjewellery.admin.domain.product.RemoveImagesResult
import com.snjewellery.admin.domain.product.StoragePaths
import com.snjewellery.admin.domain.product.UploadImageResult
import com.snjewellery.admin.domain.product.UploadedImage
import com.snjewellery.admin.domain.product.WriteImagesResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.UploadStatus
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.uploadAsFlow
import io.ktor.http.ContentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    override suspend fun replaceImages(
        productId: String,
        images: List<UploadedImage>,
    ): WriteImagesResult = try {
        val table = client.postgrest.from(TABLE_PRODUCT_IMAGES)

        // Clear first, so a second call with the same set is a no-op
        // rather than a `product_images_unique_order` violation. On a
        // first save this deletes nothing; the round trip is paid once
        // per save, after uploads that took seconds.
        table.delete { filter { eq("product_id", productId) } }
        if (images.isNotEmpty()) table.insert(images.map { it.toInsert(productId) })

        WriteImagesResult.Written
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        WriteImagesResult.Failed(failures.classify(e))
    }

    override suspend fun remove(storagePaths: List<String>): RemoveImagesResult {
        // An empty `delete` would be a pointless round trip, and the
        // common rollback — a save that failed before its first upload
        // finished — has nothing to remove.
        if (storagePaths.isEmpty()) return RemoveImagesResult.Removed

        return try {
            client.storage.from(StoragePaths.PRODUCT_IMAGES_BUCKET).delete(storagePaths)
            RemoveImagesResult.Removed
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RemoveImagesResult.Failed(failures.classify(e))
        }
    }

    /**
     * The insert payload.
     *
     * A write type rather than the M6.6 `ProductImageRow`, for the reason
     * `ProductInsert` exists: that mirrors what the database *returns*,
     * including `id` and `created_at`, which are the database's to set.
     */
    @Serializable
    private data class ProductImageInsert(
        @SerialName("product_id") val productId: String,
        @SerialName("url") val url: String,
        @SerialName("storage_path") val storagePath: String,
        @SerialName("display_order") val displayOrder: Int,
        @SerialName("aspect") val aspect: String,
    )

    private fun UploadedImage.toInsert(productId: String) = ProductImageInsert(
        productId = productId,
        url = url,
        storagePath = storagePath,
        displayOrder = displayOrder,
        aspect = if (portrait) ASPECT_PORTRAIT else ASPECT_SQUARE,
    )

    private companion object {
        const val TABLE_PRODUCT_IMAGES = "product_images"

        /** Values of the `product_image_aspect` enum. */
        const val ASPECT_SQUARE = "product"
        const val ASPECT_PORTRAIT = "product-portrait"

        /** Ktor predefines GIF, JPEG, PNG and SVG, but not this one. */
        val WEBP = ContentType("image", "webp")
    }
}
