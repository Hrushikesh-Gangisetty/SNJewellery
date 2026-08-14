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
}
