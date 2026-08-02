package com.snjewellery.admin.data.product

import com.snjewellery.admin.data.remote.RequestFailureClassifier
import com.snjewellery.admin.domain.product.CreateProductResult
import com.snjewellery.admin.domain.product.ProductDraft
import com.snjewellery.admin.domain.product.ProductRepository
import com.snjewellery.admin.domain.product.Slugs
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
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
        @SerialName("id") val id: String,
        @SerialName("slug") val slug: String,
    )

    override suspend fun create(draft: ProductDraft): CreateProductResult {
        val base = Slugs.slugify(draft.name) ?: fallbackSlug()

        for (candidate in Slugs.candidates(base)) {
            try {
                val created = client.postgrest.from(TABLE_PRODUCTS)
                    .insert(draft.toInsert(candidate)) {
                        select()
                    }
                    .decodeSingle<CreatedRow>()

                return CreateProductResult.Created(id = created.id, slug = created.slug)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PostgrestRestException) {
                // A unique violation on this payload can only be the
                // slug: `products` has exactly two unique constraints,
                // and the other is the primary key, which the database
                // generates. So the SQLSTATE alone identifies it, with
                // no constraint name to match — a name Postgres derives
                // and which no migration in this repository states.
                //
                // Every other constraint — a blank name, a negative
                // weight, a category that does not exist — fails
                // identically on every attempt, so retrying would turn
                // one clear error into twenty pointless round trips.
                if (e.code != UNIQUE_VIOLATION) {
                    return CreateProductResult.Failed(failures.classify(e))
                }
            } catch (e: Exception) {
                return CreateProductResult.Failed(failures.classify(e))
            }
        }

        return CreateProductResult.SlugExhausted
    }

    private fun ProductDraft.toInsert(slug: String) = ProductInsert(
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
