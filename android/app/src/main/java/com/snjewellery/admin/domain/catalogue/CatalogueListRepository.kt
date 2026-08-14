package com.snjewellery.admin.domain.catalogue

import com.snjewellery.admin.domain.RequestFailure

/**
 * One piece as the owner's catalogue list shows it.
 *
 * [addedAt] stays the database's ISO-8601 string, for the reason
 * [RecentProduct][com.snjewellery.admin.domain.dashboard.RecentProduct]
 * gives: formatting a date depends on the device's locale and zone, and
 * `domain` holds no Android imports.
 *
 * The three status flags are separate booleans rather than one enum
 * because they are **not exclusive** — a piece can be featured and sold at
 * once, and the PRD names all three as independent actions (M8.5).
 */
data class CatalogueEntry(
    val id: String,
    val name: String,
    val slug: String,
    /** Blank never happens: `category_id` is `not null`. */
    val categoryName: String,
    /** The primary image, or null for a piece with no photographs. */
    val thumbnailUrl: String?,
    val featured: Boolean,
    val sold: Boolean,
    val archived: Boolean,
    val addedAt: String,
)

/**
 * Where to resume reading from.
 *
 * **Keyset, not offset** — CLAUDE.md §8. `OFFSET 400` makes the database
 * walk and discard four hundred rows to hand back twenty, so the last
 * page of a large catalogue is the slowest; and a piece uploaded while the
 * owner is scrolling shifts every subsequent row by one, which shows a
 * duplicate or skips a piece entirely. A cursor pins the position to a
 * row rather than to a count.
 *
 * Both fields, not just [addedAt]: `created_at` is not unique. Two pieces
 * sharing a timestamp would make the boundary ambiguous, and the
 * primary key is what settles it.
 */
data class CatalogueCursor(val addedAt: String, val id: String)

/**
 * One page of the catalogue.
 *
 * [nextCursor] is null when this is the last page — decided by asking for
 * one row more than the page size and seeing whether it arrives, rather
 * than by a separate count. A count is a second round trip to learn
 * something the rows themselves already say.
 */
data class CataloguePage(
    val entries: List<CatalogueEntry>,
    val nextCursor: CatalogueCursor?,
)

sealed interface CataloguePageResult {
    data class Loaded(val page: CataloguePage) : CataloguePageResult
    data class Failed(val failure: RequestFailure) : CataloguePageResult
}

/**
 * The owner's whole catalogue, newest first.
 *
 * ── Archived pieces are included here, and that is deliberate ─────────
 * The dashboard excludes them (android-app.md §2.6b) because its figures
 * answer "how big is my catalogue". This list answers "what have I got",
 * and archiving is reversible — a piece hidden from the website *and* from
 * the app would be unrecoverable, which is exactly what M8.5 promises it
 * is not. So they are shown, badged, and M8.2 adds a filter for anyone
 * who wants them out of the way.
 */
interface CatalogueListRepository {
    /**
     * Reads a page, starting after [after] — or from the newest piece when
     * it is null.
     *
     * **Does not throw** for an expected failure: this list is what the
     * owner opens to find a piece, and an exception is a crash instead of
     * a Retry.
     */
    suspend fun products(after: CatalogueCursor? = null): CataloguePageResult
}
