package com.snjewellery.admin.data.catalogue

import com.snjewellery.admin.data.remote.RequestFailureClassifier
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.CategoryPosition
import com.snjewellery.admin.domain.catalogue.CategoryRepository
import com.snjewellery.admin.domain.catalogue.CreateCategoryResult
import com.snjewellery.admin.domain.catalogue.DeleteCategoryResult
import com.snjewellery.admin.domain.catalogue.RenameCategoryResult
import com.snjewellery.admin.domain.catalogue.ReorderResult
import com.snjewellery.admin.domain.catalogue.UpdateVisibilityResult
import com.snjewellery.admin.domain.product.Slugs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the `categories` rows.
 *
 * ── The slug is claimed, not checked ─────────────────────────────────
 * The same shape `SupabaseProductRepository` uses, for the same reason: a
 * SELECT that finds the slug free can still be beaten to the INSERT, so
 * the insert is attempted and SQLSTATE **23505** is the signal to try the
 * next candidate. The database is the arbiter, which is the only thing
 * that can be.
 *
 * Simpler here than for a product, because the id is the database's: a
 * unique violation on this table can only be the slug, so there is no
 * second constraint to tell it apart from.
 *
 * ── A name that produces no slug ─────────────────────────────────────
 * A category named entirely in Telugu slugifies to nothing — [Slugs]
 * deliberately does not transliterate. The category is still created,
 * with an opaque token for a slug: the owner named it correctly, and the
 * URL is a technical artefact they never see.
 */
@Singleton
class SupabaseCategoryRepository @Inject constructor(
    private val client: SupabaseClient,
    private val failures: RequestFailureClassifier,
) : CategoryRepository {

    /**
     * The insert payload.
     *
     * A write type rather than the M6.6 `CategoryRow`, which mirrors what
     * the database *returns* — including `id`, `created_at` and
     * `updated_at`, which are the database's to set. Sending them is how
     * a client starts overwriting server timestamps.
     */
    @Serializable
    private data class CategoryInsert(
        @SerialName("slug") val slug: String,
        @SerialName("name") val name: String,
        @SerialName("display_order") val displayOrder: Int,
    )

    /** What a write asks back: enough to put the row on screen. */
    @Serializable
    private data class WrittenRow(
        @SerialName("id") val id: String,
        @SerialName("name") val name: String,
        @SerialName("is_visible") val isVisible: Boolean,
        @SerialName("display_order") val displayOrder: Int,
    )

    @Serializable
    private data class OrderRow(
        @SerialName("display_order") val displayOrder: Int,
    )

    override suspend fun create(name: String): CreateCategoryResult {
        val trimmed = name.trim()
        val base = Slugs.slugify(trimmed) ?: fallbackSlug()

        val order = when (val last = lastDisplayOrder()) {
            is LastOrder.Known -> last.value + 1
            is LastOrder.Unknown -> return CreateCategoryResult.Failed(last.failure)
        }

        for (candidate in Slugs.candidates(base)) {
            try {
                val created = client.postgrest.from(TABLE_CATEGORIES)
                    .insert(CategoryInsert(slug = candidate, name = trimmed, displayOrder = order)) {
                        select()
                    }
                    .decodeSingle<WrittenRow>()

                return CreateCategoryResult.Created(
                    Category(
                        id = created.id,
                        name = created.name,
                        isVisible = created.isVisible,
                        displayOrder = created.displayOrder,
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: PostgrestRestException) {
                // Every other constraint — a blank name, a policy refusal —
                // fails identically on every attempt, so retrying would
                // turn one clear error into twenty pointless round trips.
                if (e.code != UNIQUE_VIOLATION) {
                    return CreateCategoryResult.Failed(failures.classify(e))
                }
            } catch (e: Exception) {
                return CreateCategoryResult.Failed(failures.classify(e))
            }
        }

        return CreateCategoryResult.SlugExhausted
    }

    override suspend fun rename(id: String, name: String): RenameCategoryResult = try {
        // `select()` so the response carries the row that changed. Without
        // it PostgREST answers 204 whether one row matched or none, and a
        // rename that quietly changed nothing would be reported as done.
        // See android-app.md §2.6d.
        val changed = client.postgrest.from(TABLE_CATEGORIES)
            .update({ set("name", name.trim()) }) {
                select()
                filter { eq("id", id) }
            }
            .decodeList<WrittenRow>()

        if (changed.isEmpty()) RenameCategoryResult.Missing else RenameCategoryResult.Renamed
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RenameCategoryResult.Failed(failures.classify(e))
    }

    override suspend fun setVisible(id: String, visible: Boolean): UpdateVisibilityResult = try {
        val changed = client.postgrest.from(TABLE_CATEGORIES)
            .update({ set(COLUMN_IS_VISIBLE, visible) }) {
                select()
                filter { eq("id", id) }
            }
            .decodeList<WrittenRow>()

        if (changed.isEmpty()) UpdateVisibilityResult.Missing else UpdateVisibilityResult.Updated
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        UpdateVisibilityResult.Failed(failures.classify(e))
    }

    override suspend fun reorder(positions: List<CategoryPosition>): ReorderResult {
        // One request per moved row, in order. PostgREST has no way to
        // give two rows two different values in one statement, and an
        // upsert cannot be used because the insert half would need every
        // NOT NULL column — including the slug this must never touch.
        for (position in positions) {
            try {
                val changed = client.postgrest.from(TABLE_CATEGORIES)
                    .update({ set(COLUMN_DISPLAY_ORDER, position.displayOrder) }) {
                        select()
                        filter { eq("id", position.id) }
                    }
                    .decodeList<WrittenRow>()

                if (changed.isEmpty()) return ReorderResult.Missing
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return ReorderResult.Failed(failures.classify(e))
            }
        }

        return ReorderResult.Reordered
    }

    override suspend fun delete(id: String): DeleteCategoryResult = try {
        // No `select()`, and none wanted: a category that was never there
        // and one that has just gone are the same end state.
        client.postgrest.from(TABLE_CATEGORIES).delete { filter { eq("id", id) } }
        DeleteCategoryResult.Deleted
    } catch (e: CancellationException) {
        throw e
    } catch (e: PostgrestRestException) {
        // `products.category_id` is ON DELETE RESTRICT, so this is the
        // database refusing to orphan the pieces filed under it — a
        // sentence the owner can act on, not a fault.
        if (e.code == FOREIGN_KEY_VIOLATION) {
            DeleteCategoryResult.InUse
        } else {
            DeleteCategoryResult.Failed(failures.classify(e))
        }
    } catch (e: Exception) {
        DeleteCategoryResult.Failed(failures.classify(e))
    }

    /** Where the owner's order currently ends. */
    private sealed interface LastOrder {
        data class Known(val value: Int) : LastOrder

        /** The question itself failed, so no position can be assumed. */
        data class Unknown(val failure: RequestFailure) : LastOrder
    }

    private suspend fun lastDisplayOrder(): LastOrder = try {
        val row = client.postgrest.from(TABLE_CATEGORIES)
            .select(Columns.list(COLUMN_DISPLAY_ORDER)) {
                order(COLUMN_DISPLAY_ORDER, Order.DESCENDING)
                limit(LAST_ROW_ONLY)
            }
            .decodeSingleOrNull<OrderRow>()

        // An empty table starts at the column's own default rather than at
        // 1, so the first category the owner adds is position 0.
        LastOrder.Known(row?.displayOrder ?: FIRST_DISPLAY_ORDER - 1)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        LastOrder.Unknown(failures.classify(e))
    }

    /** Opaque but valid: lowercase hex, so the CHECK constraint holds. */
    private fun fallbackSlug(): String =
        UUID.randomUUID().toString().replace("-", "").take(FALLBACK_SLUG_LENGTH)

    private companion object {
        const val TABLE_CATEGORIES = "categories"
        const val COLUMN_DISPLAY_ORDER = "display_order"
        const val COLUMN_IS_VISIBLE = "is_visible"

        /** Postgres `unique_violation`. */
        const val UNIQUE_VIOLATION = "23505"

        /** Postgres `foreign_key_violation`. */
        const val FOREIGN_KEY_VIOLATION = "23503"

        /** Only the last position is wanted, not the whole column. */
        const val LAST_ROW_ONLY = 1L

        const val FIRST_DISPLAY_ORDER = 0
        const val FALLBACK_SLUG_LENGTH = 12
    }
}
