package com.snjewellery.admin.domain.catalogue

import com.snjewellery.admin.domain.RequestFailure

/**
 * A category, as the form needs it.
 *
 * [isVisible] is carried because the owner may file a piece into a
 * hidden category deliberately — that is what a hidden category is
 * *for*, per the seed's "Unreleased Collection". The picker says so
 * rather than hiding the option, since a category the owner cannot pick
 * is a category they cannot use.
 */
data class Category(
    val id: String,
    val name: String,
    val isVisible: Boolean,
    /**
     * The owner's own position for it, as `display_order` holds it.
     *
     * Carried rather than derived from the list index because M8.7
     * reorders by **swapping the two rows' values**, and an index would
     * only be the same thing while the column happened to be numbered
     * 0, 1, 2 with no gaps. The seed is 1-based, so it never was.
     */
    val displayOrder: Int,
)

/**
 * A purity. [code] is the short form (`22K`); [label] the full one
 * ("22K Gold").
 *
 * Still captured even though the website stopped showing per-piece
 * purity in M4.15 — the owner's decision replaced it with daily metal
 * rates on the customer side, not in the record.
 */
data class Purity(
    val id: String,
    val code: String,
    val label: String,
)

/**
 * The lookup lists the Add Product form is built from.
 *
 * Both are **tables, not enums**, so a new purity or category is a row
 * the owner adds rather than a migration and an app release — see
 * schema.md § Enums. That is exactly why the form reads them live
 * instead of hard-coding the eleven the PRD happens to name today.
 */
interface CatalogueRepository {
    /** Every category, ordered as the owner arranged them. */
    suspend fun categories(): CatalogueResult<Category>

    /** Every purity, in display order. */
    suspend fun purities(): CatalogueResult<Purity>
}

sealed interface CatalogueResult<out T> {
    data class Loaded<T>(val items: List<T>) : CatalogueResult<T>
    data class Failed(val failure: RequestFailure) : CatalogueResult<Nothing>
}
