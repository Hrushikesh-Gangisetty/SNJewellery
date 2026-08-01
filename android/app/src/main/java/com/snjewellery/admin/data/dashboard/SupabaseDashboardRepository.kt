package com.snjewellery.admin.data.dashboard

import com.snjewellery.admin.data.remote.RequestFailureClassifier
import com.snjewellery.admin.domain.dashboard.DashboardMetrics
import com.snjewellery.admin.domain.dashboard.DashboardRepository
import com.snjewellery.admin.domain.dashboard.MetricsResult
import com.snjewellery.admin.domain.dashboard.RecentProduct
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.request.SelectRequestBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Extra filtering applied to a count query, beyond `archived = false`. */
private typealias CountFilter = SelectRequestBuilder.() -> Unit

/**
 * The dashboard's four figures, from Postgrest.
 *
 * ── Counted, not fetched ─────────────────────────────────────────────
 * The three counts use `head = true` with an exact count, so PostgREST
 * answers with a `Content-Range` header and **no rows at all**. Fetching
 * ids and calling `.size` would work today with eleven products and cost
 * the owner a growing download every time they open the app, on mobile
 * data, for a number they could have been told.
 *
 * ── Four requests, run together ──────────────────────────────────────
 * `async` inside one scope, so the screen waits for the slowest rather
 * than the sum. They share the app's single OkHttp client (M6.5), so
 * this is four streams on one HTTP/2 connection, not four handshakes.
 * A single database function would make it one request; that needs a
 * migration, and four cheap reads did not justify one.
 *
 * ── Why `archived = false` is on every query ─────────────────────────
 * `products_admin_all` gives an admin every row, archived included — the
 * website's public policy is what filters those, and it does not apply
 * here. So the filter is this repository's job, and forgetting it would
 * quietly report more pieces than a customer can see.
 */
@Singleton
class SupabaseDashboardRepository @Inject constructor(
    private val client: SupabaseClient,
    private val failures: RequestFailureClassifier,
) : DashboardRepository {

    @Serializable
    private data class RecentRow(
        @SerialName("id") val id: String,
        @SerialName("name") val name: String,
        @SerialName("created_at") val createdAt: String,
    )

    override suspend fun metrics(): MetricsResult = try {
        coroutineScope {
            val cutoff = Instant.now().minus(NEW_UPLOAD_DAYS, ChronoUnit.DAYS).toString()

            val total = async { countProducts() }
            val new = async { countProducts { filter { gte("created_at", cutoff) } } }
            val featured = async { countProducts { filter { eq("featured", true) } } }
            val recent = async { recentProducts() }

            MetricsResult.Loaded(
                DashboardMetrics(
                    totalProducts = total.await(),
                    newUploads = new.await(),
                    featuredProducts = featured.await(),
                    recentlyAdded = recent.await(),
                ),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        MetricsResult.Failed(failures.classify(e))
    }

    /**
     * Counts non-archived products, plus whatever [extra] narrows.
     *
     * A count with no rows can still come back without a `Content-Range`
     * if something upstream strips the header; that reads as zero rather
     * than throwing, because a dashboard showing 0 with the rest of the
     * screen intact beats a dashboard showing an error.
     */
    private suspend fun countProducts(
        narrow: CountFilter = {},
    ): Int = client.postgrest.from(TABLE_PRODUCTS)
        .select(Columns.list("id")) {
            head = true
            count(Count.EXACT)
            filter { eq("archived", false) }
            this.narrow()
        }
        .countOrNull()
        ?.toInt()
        ?: 0

    private suspend fun recentProducts(): List<RecentProduct> = client.postgrest
        .from(TABLE_PRODUCTS)
        .select(Columns.list("id", "name", "created_at")) {
            filter { eq("archived", false) }
            order("created_at", Order.DESCENDING)
            limit(RECENT_LIMIT)
        }
        .decodeList<RecentRow>()
        .map { RecentProduct(id = it.id, name = it.name, addedAt = it.createdAt) }

    private companion object {
        const val TABLE_PRODUCTS = "products"

        /** The "New Uploads" window. Reasoned about in [DashboardMetrics]. */
        const val NEW_UPLOAD_DAYS = 7L

        /** Enough to recognise what was just uploaded, not a second catalogue. */
        const val RECENT_LIMIT = 5L
    }
}
