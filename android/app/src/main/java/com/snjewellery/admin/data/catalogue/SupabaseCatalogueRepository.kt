package com.snjewellery.admin.data.catalogue

import com.snjewellery.admin.data.models.CategoryRow
import com.snjewellery.admin.data.models.PurityRow
import com.snjewellery.admin.data.remote.RequestFailureClassifier
import com.snjewellery.admin.domain.catalogue.CatalogueRepository
import com.snjewellery.admin.domain.catalogue.CatalogueResult
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.Purity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The category and purity lists behind the Add Product form.
 *
 * **Ordered by `display_order`, not by name.** The owner arranges these
 * (M8.7), and that arrangement is the order they expect to see on the
 * phone. Sorting alphabetically here would quietly discard a decision
 * they made deliberately on another screen.
 *
 * **Hidden categories are included.** `categories_admin_all` returns
 * them and the form must offer them: filing a piece into a hidden
 * category before a launch is what hidden categories exist for. The
 * picker marks them instead of dropping them — the same reasoning as
 * the dashboard's archived filter, in the opposite direction, and for
 * the same reason: the public policy is not this app's policy.
 */
@Singleton
class SupabaseCatalogueRepository @Inject constructor(
    private val client: SupabaseClient,
    private val failures: RequestFailureClassifier,
) : CatalogueRepository {

    override suspend fun categories(): CatalogueResult<Category> = fetch {
        client.postgrest.from(TABLE_CATEGORIES)
            .select { order(COLUMN_DISPLAY_ORDER, Order.ASCENDING) }
            .decodeList<CategoryRow>()
            .map { Category(id = it.id, name = it.name, isVisible = it.isVisible) }
    }

    override suspend fun purities(): CatalogueResult<Purity> = fetch {
        client.postgrest.from(TABLE_PURITIES)
            .select { order(COLUMN_DISPLAY_ORDER, Order.ASCENDING) }
            .decodeList<PurityRow>()
            .map { Purity(id = it.id, code = it.code, label = it.label) }
    }

    private suspend inline fun <T> fetch(block: () -> List<T>): CatalogueResult<T> = try {
        CatalogueResult.Loaded(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        CatalogueResult.Failed(failures.classify(e))
    }

    private companion object {
        const val TABLE_CATEGORIES = "categories"
        const val TABLE_PURITIES = "purities"
        const val COLUMN_DISPLAY_ORDER = "display_order"
    }
}
