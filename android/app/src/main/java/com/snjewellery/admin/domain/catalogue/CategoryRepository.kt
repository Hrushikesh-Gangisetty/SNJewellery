package com.snjewellery.admin.domain.catalogue

import com.snjewellery.admin.domain.RequestFailure

/** The outcome of adding a category. */
sealed interface CreateCategoryResult {
    /** The category as it now exists, ready to append to the list. */
    data class Created(val category: Category) : CreateCategoryResult

    /**
     * A free slug could not be found. Its own case rather than a generic
     * failure because nothing about it is transient: retrying the same
     * name gets the same answer, and the owner needs a different one.
     */
    data object SlugExhausted : CreateCategoryResult

    data class Failed(val failure: RequestFailure) : CreateCategoryResult
}

sealed interface RenameCategoryResult {
    data object Renamed : RenameCategoryResult

    /**
     * The update reached the database and matched no row — the category
     * was deleted from another device. Its own case rather than a
     * success, because PostgREST answers 204 for an update that changed
     * nothing exactly as for one that changed a row (android-app.md
     * §2.6d), and a rename reported as done that was not would leave the
     * owner looking at a name the catalogue does not have.
     */
    data object Missing : RenameCategoryResult

    data class Failed(val failure: RequestFailure) : RenameCategoryResult
}

sealed interface DeleteCategoryResult {
    /** Includes the row already being gone — the end state is the same. */
    data object Deleted : DeleteCategoryResult

    /**
     * Pieces are filed under it, and the foreign key is `ON DELETE
     * RESTRICT` — so the database refused rather than orphaning them.
     *
     * Reported as its own outcome because it is not a fault and retrying
     * will never work: the answer is to move the pieces or keep the
     * category. Whether the app should offer to reassign them instead of
     * simply refusing is M8.8's decision.
     */
    data object InUse : DeleteCategoryResult

    data class Failed(val failure: RequestFailure) : DeleteCategoryResult
}

/**
 * Writing the owner's categories. Reading them is [CatalogueRepository],
 * which the Add Product form and the catalogue filter already use — this
 * is the same split as [CatalogueListRepository] and `ProductRepository`,
 * and it is what stops a second way of listing categories appearing.
 *
 * ── The slug does not change when the name does ──────────────────────
 * The obvious behaviour is to re-derive it, and it is wrong for the same
 * reason it is wrong for a product: the slug is the category's address on
 * the website, and re-deriving it breaks every link anyone has shared for
 * something as small as fixing a typo. A URL is a promise; a display name
 * is not.
 *
 * ── Visibility and order are not here ────────────────────────────────
 * `is_visible` and `display_order` are M8.7's, and each is one column
 * written on its own for the reason `ProductRepository.setStatus` gives:
 * sending a whole row back from a screen the owner has had open lets a
 * stale value overwrite a change made elsewhere. [create] is the one
 * exception, because a row has to start somewhere.
 */
interface CategoryRepository {
    /**
     * Adds a category, placed **last** in the owner's order.
     *
     * The column defaults to 0, which would tie every new category with
     * every other one and leave the list to sort them arbitrarily —
     * quietly discarding the arrangement M8.7 lets the owner make. So the
     * position is read and the new row goes after it.
     */
    suspend fun create(name: String): CreateCategoryResult

    /** Changes the display name. The slug stays as it is — see above. */
    suspend fun rename(id: String, name: String): RenameCategoryResult

    /**
     * Removes the category.
     *
     * Refuses with [DeleteCategoryResult.InUse] while any piece is filed
     * under it. That is the database's refusal, not a check this makes
     * first: a count that comes back zero can still be beaten to the
     * delete by a save on another device.
     */
    suspend fun delete(id: String): DeleteCategoryResult
}
