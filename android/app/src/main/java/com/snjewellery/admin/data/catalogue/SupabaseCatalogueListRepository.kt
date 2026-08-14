package com.snjewellery.admin.data.catalogue

import com.snjewellery.admin.data.remote.RequestFailureClassifier
import com.snjewellery.admin.domain.catalogue.CatalogueCursor
import com.snjewellery.admin.domain.catalogue.CatalogueEntry
import com.snjewellery.admin.domain.catalogue.CatalogueListRepository
import com.snjewellery.admin.domain.catalogue.CataloguePage
import com.snjewellery.admin.domain.catalogue.CataloguePageResult
import com.snjewellery.admin.domain.catalogue.CatalogueQuery
import com.snjewellery.admin.domain.catalogue.StatusFilter
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The catalogue list, from Postgrest.
 *
 * ── One request per page, not one plus N ──────────────────────────────
 * The category name and the photograph come back as **embedded
 * resources** in the same request, so a page of twenty pieces is one round
 * trip rather than forty-one. On the mobile data this app is used over,
 * the per-request cost dominates the payload.
 *
 * `product_images` is embedded *unfiltered* and the primary is chosen from
 * what comes back. PostgREST can narrow an embed, but a piece with no
 * photographs — which M7's form permits — is exactly the row that must
 * still appear in the owner's list, and narrowing risks dropping it. The
 * cost is a handful of URLs per piece, which is bytes.
 *
 * ── The cursor comparison ─────────────────────────────────────────────
 * Ordering by `created_at desc, id desc` needs the boundary expressed as a
 * row comparison, which PostgREST spells as a disjunction:
 * *older than the cursor's timestamp, **or** the same timestamp with a
 * smaller id*. Writing only the first half loses every piece that shares
 * a timestamp with the cursor row; writing `lte` instead repeats it.
 */
@Singleton
class SupabaseCatalogueListRepository @Inject constructor(
    private val client: SupabaseClient,
    private val failures: RequestFailureClassifier,
) : CatalogueListRepository {

    @Serializable
    private data class EmbeddedCategory(@SerialName("name") val name: String)

    @Serializable
    private data class EmbeddedImage(
        @SerialName("url") val url: String,
        @SerialName("display_order") val displayOrder: Int,
    )

    @Serializable
    private data class EntryRow(
        @SerialName("id") val id: String,
        @SerialName("name") val name: String,
        @SerialName("slug") val slug: String,
        @SerialName("featured") val featured: Boolean,
        @SerialName("sold") val sold: Boolean,
        @SerialName("archived") val archived: Boolean,
        @SerialName("created_at") val createdAt: String,
        // Nullable although `category_id` is `not null`: an embed the
        // policy hides would arrive as null, and a crash on the owner's
        // catalogue list is a worse answer than a blank category.
        @SerialName("categories") val category: EmbeddedCategory? = null,
        @SerialName("product_images") val images: List<EmbeddedImage> = emptyList(),
    )

    override suspend fun products(
        query: CatalogueQuery,
        after: CatalogueCursor?,
    ): CataloguePageResult = try {
        val rows = client.postgrest.from(TABLE_PRODUCTS)
            .select(COLUMNS) {
                filter {
                    narrowByStatus(query.status)
                    query.categoryId?.let { eq("category_id", it) }
                    query.term?.let { term ->
                        // Name by substring, tags by whole tag. Both are
                        // columns on `products`, so this disjunction needs
                        // no embedded resource — which is why category is
                        // a separate pick rather than part of the text.
                        or {
                            ilike("name", "%$term%")
                            contains("tags", listOf(term))
                        }
                    }
                    if (after != null) {
                        or {
                            lt("created_at", after.addedAt)
                            and {
                                eq("created_at", after.addedAt)
                                lt("id", after.id)
                            }
                        }
                    }
                }
                order("created_at", Order.DESCENDING)
                order("id", Order.DESCENDING)
                // One more than the page, so whether there is a next page
                // is answered by the rows rather than by a second query.
                limit(PAGE_SIZE + 1)
            }
            .decodeList<EntryRow>()

        val page = rows.take(PAGE_SIZE.toInt())
        CataloguePageResult.Loaded(
            CataloguePage(
                entries = page.map { it.toEntry() },
                nextCursor = if (rows.size > page.size) {
                    page.last().let { CatalogueCursor(addedAt = it.createdAt, id = it.id) }
                } else {
                    null
                },
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CataloguePageResult.Failed(failures.classify(e))
    }

    /**
     * The status filter, as columns.
     *
     * `Live` and `All` are the only two that say anything about `archived`,
     * and they say opposite things: `Live` excludes archived pieces, `All`
     * mentions it at all. `Featured` and `Sold` deliberately **do not**
     * filter `archived` either way — the owner asking "what is featured"
     * wants every featured piece, and silently dropping the archived ones
     * would give a count that disagrees with the Archived filter's.
     */
    private fun PostgrestFilterBuilder.narrowByStatus(status: StatusFilter) = when (status) {
        StatusFilter.Live -> eq("archived", false)
        StatusFilter.All -> Unit
        StatusFilter.Featured -> eq("featured", true)
        StatusFilter.Sold -> eq("sold", true)
        StatusFilter.Archived -> eq("archived", true)
    }

    private fun EntryRow.toEntry() = CatalogueEntry(
        id = id,
        name = name,
        slug = slug,
        categoryName = category?.name.orEmpty(),
        // `minByOrNull`, not `first()`: the rows arrive in whatever order
        // PostgREST returns them, and position 0 is the primary image by
        // its `display_order`, not by where it happens to sit in the JSON.
        thumbnailUrl = images.minByOrNull { it.displayOrder }?.url,
        featured = featured,
        sold = sold,
        archived = archived,
        addedAt = createdAt,
    )

    private companion object {
        const val TABLE_PRODUCTS = "products"

        val COLUMNS = Columns.raw(
            "id,name,slug,featured,sold,archived,created_at," +
                "categories(name),product_images(url,display_order)",
        )

        /**
         * Enough to fill a phone screen twice over, so scrolling reaches
         * the next page before the owner reaches the bottom, and small
         * enough that the first page arrives quickly on mobile data.
         */
        const val PAGE_SIZE = 20L
    }
}
