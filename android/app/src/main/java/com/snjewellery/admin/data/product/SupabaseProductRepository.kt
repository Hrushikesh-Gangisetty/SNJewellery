package com.snjewellery.admin.data.product

import com.snjewellery.admin.data.remote.RequestFailureClassifier
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.product.CreateProductResult
import com.snjewellery.admin.domain.product.DeleteProductResult
import com.snjewellery.admin.domain.product.ProductDraft
import com.snjewellery.admin.domain.product.ProductRepository
import com.snjewellery.admin.domain.product.Slugs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the `products` row.
 *
 * ── The slug is claimed, not checked ─────────────────────────────────
 * The obvious shape — query for the slug, then insert if free — has a
 * race: two saves of "Plain Gold Bangle Set" can both find nothing and
 * both proceed, and the second gets a constraint violation the owner
 * sees as an unexplained failure. So the insert is *attempted* and a
 * unique violation is the signal to try the next candidate. The database
 * is the arbiter, which is the only thing that can be.
 *
 * Postgres reports that as **SQLSTATE 23505**. The code is matched
 * rather than the message, for the reason M6.7 established: a message is
 * a string someone may reword.
 *
 * ── Names that produce no slug ───────────────────────────────────────
 * A name written entirely in Telugu or Devanagari slugifies to nothing
 * ([Slugs] deliberately does not transliterate). Rather than refuse the
 * save — the owner named the piece correctly, and the URL is a technical
 * artefact they never see — it falls back to an opaque token. The piece
 * saves, the website serves it, and the URL is ugly rather than wrong.
 *
 * ── A second insert of the same id is not a second product ───────────
 * Since M7.9 the caller supplies the id, so `products` has two unique
 * constraints that a save can violate rather than one, and SQLSTATE
 * 23505 no longer identifies the slug on its own. The two are told apart
 * by asking the database which row exists — not by reading the
 * constraint name out of the message, which is the string M6.7's rule is
 * about.
 *
 * That distinction is worth having for its own sake. The way this row
 * gets written twice is not a broken UUID source: it is a save whose
 * insert landed and whose *response* did not, so the app saw a failure
 * and the owner tapped Save again. Answering with the row that is
 * already there is what stops that from becoming two copies of one
 * piece.
 */
@Singleton
class SupabaseProductRepository @Inject constructor(
    private val client: SupabaseClient,
    private val failures: RequestFailureClassifier,
) : ProductRepository {

    /**
     * The insert payload.
     *
     * A dedicated write type rather than the M6.6 `ProductRow`: that
     * mirrors what the database *returns*, including `id`, `created_at`
     * and `updated_at`, which are the database's to set and must not be
     * sent. Sending them is how a client starts overwriting server
     * timestamps.
     */
    @Serializable
    private data class ProductInsert(
        // Sent rather than defaulted, because the photographs are already
        // in Storage under `products/{id}/…` by the time this row is
        // written. See ProductRepository.create.
        @SerialName("id") val id: String,
        @SerialName("slug") val slug: String,
        @SerialName("name") val name: String,
        @SerialName("description") val description: String?,
        @SerialName("category_id") val categoryId: String,
        @SerialName("purity_id") val purityId: String?,
        @SerialName("weight_grams") val weightGrams: Double?,
        @SerialName("tags") val tags: List<String>,
        @SerialName("featured") val featured: Boolean,
    )

    @Serializable
    private data class CreatedRow(
        @SerialName("slug") val slug: String,
    )

    override suspend fun create(id: String, draft: ProductDraft): CreateProductResult {
        val base = Slugs.slugify(draft.name) ?: fallbackSlug()

        for (candidate in Slugs.candidates(base)) {
            try {
                val created = client.postgrest.from(TABLE_PRODUCTS)
                    .insert(draft.toInsert(id, candidate)) {
                        select()
                    }
                    .decodeSingle<CreatedRow>()

                return CreateProductResult.Created(slug = created.slug)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PostgrestRestException) {
                // Every other constraint — a blank name, a negative
                // weight, a category that does not exist — fails
                // identically on every attempt, so retrying would turn
                // one clear error into twenty pointless round trips.
                if (e.code != UNIQUE_VIOLATION) {
                    return CreateProductResult.Failed(failures.classify(e))
                }

                // Either the slug is taken or this id is already a row.
                // Only the database can say which, and if it is the id
                // then this save has already succeeded.
                when (val existing = slugOf(id)) {
                    is ExistingRow.Found -> return CreateProductResult.Created(existing.slug)
                    is ExistingRow.Absent -> Unit // The slug. Next candidate.
                    is ExistingRow.Unknown ->
                        return CreateProductResult.Failed(existing.failure)
                }
            } catch (e: Exception) {
                return CreateProductResult.Failed(failures.classify(e))
            }
        }

        return CreateProductResult.SlugExhausted
    }

    override suspend fun delete(id: String): DeleteProductResult = try {
        // No `select()`, so no row is returned and none is expected: a row
        // that was never written and a row that has just been removed are
        // the same end state, which is the state rollback wants.
        client.postgrest.from(TABLE_PRODUCTS).delete { filter { eq("id", id) } }
        DeleteProductResult.Deleted
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DeleteProductResult.Failed(failures.classify(e))
    }

    /** What the database says about a row with this id. */
    private sealed interface ExistingRow {
        data class Found(val slug: String) : ExistingRow
        data object Absent : ExistingRow

        /** The question itself failed, so neither answer can be assumed. */
        data class Unknown(val failure: RequestFailure) : ExistingRow
    }

    private suspend fun slugOf(id: String): ExistingRow = try {
        val row = client.postgrest.from(TABLE_PRODUCTS)
            .select(Columns.list("slug")) { filter { eq("id", id) } }
            .decodeSingleOrNull<CreatedRow>()

        if (row == null) ExistingRow.Absent else ExistingRow.Found(row.slug)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ExistingRow.Unknown(failures.classify(e))
    }

    private fun ProductDraft.toInsert(id: String, slug: String) = ProductInsert(
        id = id,
        slug = slug,
        name = name.trim(),
        // Blank and absent are the same thing to the website, and the
        // column is nullable — so an emptied field stores NULL rather
        // than an empty string that later renders as a blank paragraph.
        description = description?.trim()?.ifBlank { null },
        categoryId = categoryId,
        purityId = purityId,
        weightGrams = weightGrams,
        tags = tags,
        featured = featured,
    )

    /** Opaque but valid: lowercase hex, so the CHECK constraint holds. */
    private fun fallbackSlug(): String =
        UUID.randomUUID().toString().replace("-", "").take(FALLBACK_SLUG_LENGTH)

    private companion object {
        const val TABLE_PRODUCTS = "products"

        /** Postgres `unique_violation`. */
        const val UNIQUE_VIOLATION = "23505"

        const val FALLBACK_SLUG_LENGTH = 12
    }
}
