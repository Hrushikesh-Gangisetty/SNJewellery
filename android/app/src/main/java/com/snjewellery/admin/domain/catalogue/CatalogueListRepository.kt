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
 * Which pieces to show, by status.
 *
 * ── Why this is one choice and not three switches ─────────────────────
 * Featured, Sold and Archived are independent *properties* of a piece
 * (M8.5), and three checkboxes would let the owner ask for combinations
 * that mean nothing — "featured and not featured" — and would need a
 * paragraph of explanation on a screen used one-handed. What the owner
 * actually wants is one of a short list of questions, so the filter is
 * that list.
 *
 * [Live] is the default rather than [All], and that is the one decision
 * here worth stating: the common task is finding a piece that is in the
 * catalogue now, and archived pieces at the top of the list would be
 * noise in front of it. [All] is one tap away.
 */
enum class StatusFilter {
    /** In the catalogue, sold or not. Archived pieces excluded. */
    Live,

    /** Everything, archived included. */
    All,

    Featured,
    Sold,

    /** Withdrawn from the website but kept in the app. */
    Archived,
}

/**
 * What the owner is looking for.
 *
 * The PRD asks for search by **name, category and tags**. Name and tags
 * are [text]; category is [categoryId], a pick from the owner's own list
 * rather than a substring of it — a category the owner selects cannot be
 * misspelt into no results, and there are eleven of them, not a thousand.
 *
 * ── What [text] matches, exactly ──────────────────────────────────────
 * **Name by substring**, case-insensitively. **Tags by whole tag** — a
 * search for `bridal` finds a piece tagged `bridal` and not one tagged
 * `bridalwear`, and it is case-sensitive, because `tags` is a Postgres
 * array and array containment is the only matching PostgREST offers on
 * one. Partial or case-insensitive tag matching needs a migration (a
 * lowercased expression index, or a generated search column), which is
 * **M10's** job — that milestone owns real search and carries a latency
 * budget at 100k products. This is the find-a-piece filter, not that.
 */
data class CatalogueQuery(
    val text: String = "",
    val status: StatusFilter = StatusFilter.Live,
    /** Null means every category. */
    val categoryId: String? = null,
) {
    val term: String? get() = text.trim().ifBlank { null }

    /** Whether anything is narrowing the list. Drives the "clear" affordance. */
    val isFiltered: Boolean
        get() = term != null || categoryId != null || status != StatusFilter.Live
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
     * Reads a page matching [query], starting after [after] — or from the
     * newest matching piece when it is null.
     *
     * **Filtering happens in the database, not after the fact.** Reading
     * the whole catalogue and filtering in Kotlin would work at eleven
     * products and become a full download on mobile data at a thousand,
     * for a screen whose entire purpose is to *avoid* looking at the whole
     * catalogue.
     *
     * **Does not throw** for an expected failure: this list is what the
     * owner opens to find a piece, and an exception is a crash instead of
     * a Retry.
     */
    suspend fun products(
        query: CatalogueQuery = CatalogueQuery(),
        after: CatalogueCursor? = null,
    ): CataloguePageResult
}
