package com.snjewellery.admin.domain.dashboard

import com.snjewellery.admin.domain.RequestFailure

/**
 * One recently added piece, for the dashboard's list.
 *
 * [addedAt] stays the database's ISO-8601 string. Formatting a date is a
 * presentation decision that depends on the device's locale and zone,
 * and `domain` holds no Android imports — the screen formats it.
 */
data class RecentProduct(
    val id: String,
    val name: String,
    val addedAt: String,
)

/**
 * The four figures the PRD's Dashboard section names.
 *
 * ── What the PRD leaves undefined, and what was chosen ───────────────
 * The PRD lists four labels and defines none of them. Two needed a
 * decision, and both are recorded here rather than buried in a query:
 *
 * - **Archived pieces are excluded from every count.** Archiving means
 *   "no longer in the catalogue" (M8), so counting them would tell the
 *   owner they have more pieces than a customer can see. Note this is
 *   not the same as *sold*: a sold piece is still in the catalogue and
 *   still counted, which matches the website showing it with a badge.
 * - **"New Uploads" means the last seven days.** The PRD gives no
 *   window. A week is the shortest span that still reads as non-zero for
 *   a shop photographing a few pieces between customers, and it matches
 *   how the owner would ask the question — "what did I add this week".
 *
 * Both are assumptions, flagged as such in DEVELOPMENT_PLAN.md against
 * Open Question territory rather than silently encoded.
 */
data class DashboardMetrics(
    val totalProducts: Int,
    val newUploads: Int,
    val featuredProducts: Int,
    val recentlyAdded: List<RecentProduct>,
) {
    /**
     * Nothing has been uploaded yet — the empty state, not an error.
     * ux.md rule 3: "nothing here" and "something broke" are different
     * messages with different next steps.
     */
    val isEmpty: Boolean get() = totalProducts == 0
}

/** Loaded, or the reason it was not. */
sealed interface MetricsResult {
    data class Loaded(val metrics: DashboardMetrics) : MetricsResult
    data class Failed(val failure: RequestFailure) : MetricsResult
}

interface DashboardRepository {
    /**
     * Reads all four figures. **Does not throw** for an expected
     * failure — the dashboard is the app's start destination, and an
     * exception here is a crash on launch.
     */
    suspend fun metrics(): MetricsResult
}
