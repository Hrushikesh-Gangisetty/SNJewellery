package com.snjewellery.admin.domain.draft

import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.media.StagedImages
import com.snjewellery.admin.domain.product.CreateProductResult
import com.snjewellery.admin.domain.product.ProductImageRepository
import com.snjewellery.admin.domain.product.ProductRepository
import com.snjewellery.admin.domain.product.StagedUpload
import com.snjewellery.admin.domain.product.UploadImageResult
import com.snjewellery.admin.domain.product.WriteImagesResult
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DraftUploadResult {
    data object Sent : DraftUploadResult

    /**
     * No free slug for the name. Its own case because it is the one
     * outcome retrying cannot fix: the sync must stop offering to try
     * again and the owner has to change the name.
     */
    data object NameUnavailable : DraftUploadResult

    data class Failed(val failure: RequestFailure) : DraftUploadResult
}

/**
 * Gets one waiting draft into the catalogue.
 *
 * ── The ordering is the invariant, and it is the same one ────────────
 * Photographs into Storage first, the `products` row **last** — the rule
 * android-app.md §2.6c states and M7.9 established. An interruption here
 * therefore leaves objects nothing points at and no row at all, so
 * nothing a customer can reach is ever half-made. That holds whether the
 * upload was driven by the form or by the sync.
 *
 * ── What this is not ─────────────────────────────────────────────────
 * It is not the Add Product form's pipeline, and deliberately not a
 * shared one. That pipeline additionally reports per-image progress,
 * clears objects abandoned by reordering, handles the edit case, and
 * offers a rollback — all of which exist because a person is watching.
 * None of it applies to a draft being sent from the background, where
 * the photograph list is final and nobody is looking.
 *
 * The two therefore share the repositories, the [StagedUpload] record and
 * the ordering rule, but not the code. That is a real duplication of the
 * *sequence*, and the place it is stated once is §2.6c.
 */
@Singleton
class DraftUploader @Inject constructor(
    private val products: ProductRepository,
    private val images: ProductImageRepository,
    private val staged: StagedImages,
) {

    /**
     * [onUploaded] is called after each photograph lands, before the next
     * is started, so the caller can persist what has been done.
     *
     * That is not bookkeeping for its own sake: without it, a sync
     * cancelled half-way — the signal drops again, the process is killed —
     * forgets which objects exist, and the next attempt uploads them a
     * second time and pays for both copies forever.
     */
    suspend fun upload(
        draft: PendingDraft,
        onUploaded: suspend (StagedUpload) -> Unit,
    ): DraftUploadResult {
        val landed = draft.uploaded.associateBy { it.localUri }.toMutableMap()

        for (localUri in draft.photoUris) {
            if (landed.containsKey(localUri)) continue

            when (
                val result = images.upload(draft.productId, localUri) { _, _ -> }
            ) {
                is UploadImageResult.Uploaded -> {
                    val upload = StagedUpload(
                        localUri = localUri,
                        storagePath = result.storagePath,
                        url = result.url,
                        // Measured per photograph, as the form does it: a
                        // long chain gets the 4:5 frame and a ring does
                        // not, and guessing one answer for the whole
                        // piece would letterbox half of them. It is a
                        // header read, not a decode.
                        portrait = staged.isPortrait(localUri),
                    )
                    landed[localUri] = upload
                    onUploaded(upload)
                }

                is UploadImageResult.Failed -> return DraftUploadResult.Failed(result.failure)
            }
        }

        // The slug is not kept: nothing here links to the piece, unlike
        // the form's confirmation screen (M7.12).
        when (val created = products.create(draft.productId, draft.draft)) {
            is CreateProductResult.Created -> Unit
            is CreateProductResult.SlugExhausted -> return DraftUploadResult.NameUnavailable
            is CreateProductResult.Failed -> return DraftUploadResult.Failed(created.failure)
        }

        // The rows in the owner's order, which is the order of photoUris —
        // not the order the objects happened to go up in.
        val rows = draft.photoUris.mapIndexedNotNull { index, uri ->
            landed[uri]?.toRow(index)
        }

        return when (val written = images.replaceImages(draft.productId, rows)) {
            is WriteImagesResult.Written -> DraftUploadResult.Sent

            // The row exists and its photographs are not attached. The
            // draft stays, and the next attempt re-runs a create that
            // answers with the row already there (M7.9) and rewrites the
            // image rows, which `replaceImages` makes idempotent.
            is WriteImagesResult.Failed -> DraftUploadResult.Failed(written.failure)
        }
    }
}
