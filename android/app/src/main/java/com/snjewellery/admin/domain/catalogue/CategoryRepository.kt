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
     * will never work: the answer is to refile the pieces. **The app
     * blocks rather than reassigning them**, decided and reasoned in
     * `docs/adr/0011-category-deletion-with-products.md`.
     *
     * [pieces] is how many are filed under it, or null when the count
     * itself could not be read — the refusal is still correct, it just
     * cannot say how many.
     */
    data class InUse(val pieces: Int?) : DeleteCategoryResult

    data class Failed(val failure: RequestFailure) : DeleteCategoryResult
}

sealed interface UpdateVisibilityResult {
    data object Updated : UpdateVisibilityResult

    /** Nothing matched — see [RenameCategoryResult.Missing]. */
    data object Missing : UpdateVisibilityResult

    data class Failed(val failure: RequestFailure) : UpdateVisibilityResult
}

/** One category's new place in the owner's order. */
data class CategoryPosition(val id: String, val displayOrder: Int)

sealed interface ReorderResult {
    data object Reordered : ReorderResult

    /**
     * A category in the move is no longer there, so the order on screen
     * describes a list that no longer exists. Its own case because the
     * fix is to re-read, and because part of the move may already have
     * been written — which is exactly what a re-read resolves.
     */
    data object Missing : ReorderResult

    data class Failed(val failure: RequestFailure) : ReorderResult
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
 * ── One column per write ─────────────────────────────────────────────
 * [rename], [setVisible] and [reorder] each write the one column they
 * own, for the reason `ProductRepository.setStatus` gives: sending a
 * whole row back from a screen the owner has had open for a minute lets
 * a stale value overwrite a change made elsewhere. [create] is the one
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
     * Hides or shows the category.
     *
     * Hiding removes it **and every piece filed under it** from the
     * public site. That is the RLS policy's doing, not a filter a query
     * could forget — `categories_public_read` admits only visible rows,
     * and the products policy joins through it.
     */
    suspend fun setVisible(id: String, visible: Boolean): UpdateVisibilityResult

    /**
     * Writes the given categories' positions.
     *
     * Takes only the rows that moved. A reorder in this app is a swap of
     * two adjacent categories, so that is two writes rather than a
     * renumbering of the whole list — which would rewrite every row the
     * owner did not touch, and would need the list to have no gaps.
     *
     * There is no transaction across the two: PostgREST cannot express
     * one, and an RPC would be a migration for a list of a dozen rows.
     * A half-written move is therefore possible, and is reported so the
     * caller re-reads rather than trusting what is on screen.
     */
    suspend fun reorder(positions: List<CategoryPosition>): ReorderResult

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
