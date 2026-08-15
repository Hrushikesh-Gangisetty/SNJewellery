package com.snjewellery.admin.domain.product

import com.snjewellery.admin.domain.RequestFailure

/**
 * What the owner entered on the Add Product form, before it is a row.
 *
 * Every field maps to one `products` column, named to match — the PRD's
 * vocabulary and the schema's, which CLAUDE.md §5 requires to be the
 * same words.
 *
 * **`summary` is absent, and that is a gap, not an oversight.** The
 * PRD's Add Product section lists eight fields and Summary is not among
 * them, but the website's product card renders exactly that column as
 * its short description. So a piece uploaded from the app will show no
 * short description on the catalogue grid, where the seeded products
 * do. Flagged in DEVELOPMENT_PLAN.md for a decision rather than fixed by
 * inventing a ninth field on a form used between customers, or by
 * silently deriving one from the description.
 */
data class ProductDraft(
    val name: String,
    val categoryId: String,
    /** Optional: the PRD lists Purity, but not every piece has one recorded. */
    val purityId: String? = null,
    /** Grams. Null when not weighed; the column allows it, positive if present. */
    val weightGrams: Double? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val featured: Boolean = false,
)

/** The outcome of saving a draft. */
sealed interface CreateProductResult {
    /** [slug] is what the website's URL will be — M7.12 links to it. */
    data class Created(val slug: String) : CreateProductResult

    data class Failed(val failure: RequestFailure) : CreateProductResult

    /**
     * A unique slug could not be found after repeated attempts. Its own
     * case rather than a generic failure because nothing about it is
     * transient: retrying the same name gets the same answer, and the
     * owner needs to be told to change the name.
     */
    data object SlugExhausted : CreateProductResult
}

/** Whether the row is gone. */
sealed interface DeleteProductResult {
    /** Includes the row never having existed — the end state is the same. */
    data object Deleted : DeleteProductResult
    data class Failed(val failure: RequestFailure) : DeleteProductResult
}

/**
 * The three status actions the PRD names, and what each means to a
 * customer.
 *
 * They are **not** one state machine. A piece can be featured and sold at
 * once, and the difference between Sold and Archived is the whole point of
 * having both — so they are three independent flags, and this enum only
 * says which column an action writes.
 */
enum class ProductStatus {
    /** Promoted on the website's home page. Invisible if archived. */
    Featured,

    /**
     * Sold, and **still on the website** with a badge. The PRD is explicit
     * that this is not a withdrawal: a sold piece is what persuades the
     * next customer that pieces like it exist.
     */
    Sold,

    /**
     * Withdrawn from the website, kept in the app. The only one of the
     * three that removes a piece from what a customer can reach, and it is
     * reversible — which is why the catalogue list still shows archived
     * pieces (M8.1).
     */
    Archived,
}

sealed interface UpdateStatusResult {
    data object Updated : UpdateStatusResult

    /**
     * The update reached the database and **changed nothing**, because no
     * row with that id was visible to it.
     *
     * Its own case rather than a success, and this is not a theoretical
     * one: PostgREST answers `204 No Content` for an `UPDATE` that matched
     * zero rows exactly as it does for one that matched — verified against
     * the live project. Treating the response code alone as success gives
     * an optimistic toggle that never rolls back, showing the owner a
     * state the catalogue does not have.
     *
     * In practice it means the piece was deleted from another device, so
     * the answer is to refresh rather than to retry.
     */
    data object Missing : UpdateStatusResult

    data class Failed(val failure: RequestFailure) : UpdateStatusResult
}

/**
 * One photograph already in Storage, as the edit form needs it back.
 *
 * **Both** the path and the URL, because they answer different questions:
 * the URL is what the screen renders, and the path is what a removal has
 * to delete and what the row points at. ADR-0005 §4 stores both for
 * exactly this reason, and dropping either here would mean deriving it
 * again somewhere else.
 */
data class StoredPhoto(val storagePath: String, val url: String)

/**
 * An existing piece, as the edit form needs it back.
 *
 * [slug] is carried but **never editable**: see [ProductRepository.update].
 * [photos] are in `display_order`, so the first is the primary image.
 */
data class EditableProduct(
    val id: String,
    val slug: String,
    val draft: ProductDraft,
    val photos: List<StoredPhoto>,
)

sealed interface LoadProductResult {
    data class Loaded(val product: EditableProduct) : LoadProductResult

    /** No such piece — deleted from another device while the list was stale. */
    data object Missing : LoadProductResult

    data class Failed(val failure: RequestFailure) : LoadProductResult
}

sealed interface UpdateProductResult {
    data object Updated : UpdateProductResult

    /** Nothing matched. See [UpdateStatusResult.Missing] for why this is its own case. */
    data object Missing : UpdateProductResult

    data class Failed(val failure: RequestFailure) : UpdateProductResult
}

interface ProductRepository {
    /**
     * Creates the `products` row with the given [id], and returns the slug
     * the database accepted.
     *
     * ── Why the caller chooses the id ────────────────────────────────
     * The column defaults to `gen_random_uuid()`, so letting the database
     * pick would be the obvious shape. But a photograph's Storage path is
     * `products/{product_id}/…` (ADR-0005 §2), and M7.9 uploads the
     * photographs **before** this row exists — so the id has to be known
     * before there is a row to read it from. Generating it on the device
     * is what makes that ordering possible, and a v4 UUID is a v4 UUID
     * whichever side of the wire produces it.
     *
     * **Does not throw** for an expected failure — a crash here loses
     * everything the owner typed.
     */
    suspend fun create(id: String, draft: ProductDraft): CreateProductResult

    /**
     * Removes the `products` row, and with it — by the M3.3 cascade — its
     * `product_images` rows.
     *
     * The cascade does **not** reach Storage. Deleting the objects is
     * [ProductImageRepository.remove]'s job and has to be done as well;
     * ADR-0005 names the orphans it otherwise leaves as a cost that
     * accumulates silently.
     *
     * Deleting a row that is not there succeeds. Rollback runs when
     * something has already gone wrong, and refusing on that ground would
     * report a failure for the state it was trying to reach.
     */
    suspend fun delete(id: String): DeleteProductResult

    /**
     * Sets one status flag on one piece.
     *
     * One flag per call rather than a whole product: the screen's actions
     * are individual toggles, and sending the other two back would let a
     * stale value on this device overwrite a change made on another.
     */
    suspend fun setStatus(
        id: String,
        status: ProductStatus,
        value: Boolean,
    ): UpdateStatusResult

    /** Reads one piece back, for the edit form to fill itself from. */
    suspend fun byId(id: String): LoadProductResult

    /**
     * Saves changes to an existing piece.
     *
     * ── The slug does not change, even when the name does ────────────
     * The obvious behaviour is to re-derive it, and it is wrong. The slug
     * is the piece's address on the website: re-deriving it breaks every
     * link anyone has shared, every bookmark, and the canonical URL M11
     * builds on — and it does so for something as small as fixing a typo,
     * which is exactly what this screen is most used for. A URL is a
     * promise; a display name is not.
     *
     * `status` is not here either. The toggles (M8.5) write one flag at a
     * time on purpose, and sending all of them back from a form the owner
     * may have had open for a minute would let a stale value overwrite a
     * change made elsewhere.
     */
    suspend fun update(id: String, draft: ProductDraft): UpdateProductResult
}
