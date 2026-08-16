package com.snjewellery.admin.domain.draft

import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.product.ProductDraft
import kotlinx.coroutines.flow.Flow

/**
 * A piece the owner entered that is not in the catalogue yet.
 *
 * ── Why the id is here ───────────────────────────────────────────────
 * [productId] is the id the save attempt already chose (M7.9), not a
 * separate draft key. Keeping it means a draft that is finally uploaded
 * writes the row it was always going to write — and that photographs
 * already in Storage under `products/{id}/…` from a part-finished attempt
 * still belong to it. A new id would strand them.
 *
 * ── Why the photographs are URIs and not bytes ───────────────────────
 * The files stay on disk; this records where. They are **moved out of the
 * cache** when the draft is written, because the cache is reclaimable by
 * the system and a draft that outlives an eviction would be a piece with
 * no photographs — see `StagedImages.retain`.
 */
data class PendingDraft(
    val productId: String,
    /** Everything the `products` row will be written from. */
    val draft: ProductDraft,
    /** Staged photographs, in the order the owner put them in. */
    val photoUris: List<String>,
    /** When it was last written, epoch milliseconds. */
    val savedAt: Long,
    /**
     * Why the last attempt stopped, or null when it has not been tried
     * since. Kept so the owner is told *why* a piece is still waiting,
     * rather than only that it is.
     */
    val failure: RequestFailure? = null,
)

/**
 * Drafts held on the device.
 *
 * The one repository in this app that talks to local storage rather than
 * Supabase, which is the whole point of it: it has to work with the radio
 * off. Everything else here fails when the network does.
 */
interface DraftRepository {
    /**
     * Every draft waiting to be uploaded, oldest first.
     *
     * A `Flow` rather than a suspend call: the dashboard shows these, and
     * a draft can appear or disappear while it is on screen — a save
     * finishing, or M8.10's sync landing one. Polling for that would be
     * the same read on a timer for something the database can announce.
     */
    fun pending(): Flow<List<PendingDraft>>

    suspend fun byId(productId: String): PendingDraft?

    /** Writes it, replacing any earlier draft for the same piece. */
    suspend fun save(draft: PendingDraft)

    /** Forgets it. Deleting one that is not there is not an error. */
    suspend fun delete(productId: String)
}
