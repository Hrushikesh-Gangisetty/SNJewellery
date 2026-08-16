package com.snjewellery.admin.data.rates

import com.snjewellery.admin.data.models.MetalRateRow
import com.snjewellery.admin.data.remote.RequestFailureClassifier
import com.snjewellery.admin.domain.rates.Metal
import com.snjewellery.admin.domain.rates.MetalRate
import com.snjewellery.admin.domain.rates.MetalRatesRepository
import com.snjewellery.admin.domain.rates.RatesResult
import com.snjewellery.admin.domain.rates.UpdateRateResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton
import com.snjewellery.admin.data.models.Metal as MetalColumn

/**
 * Reads and writes the two `metal_rates` rows.
 *
 * ── The timestamp is never sent ──────────────────────────────────────
 * Only `rate_per_gram` goes on the wire. `updated_at` is the trigger's,
 * and sending it would be a client overwriting a server timestamp — the
 * same rule the insert payloads follow. It also could not be got right
 * from here: the trigger *nulls* the timestamp when the rate is
 * unpublished, which is the invariant
 * `20260727000300_metal_rates_timestamp_invariant.sql` exists to hold.
 */
@Singleton
class SupabaseMetalRatesRepository @Inject constructor(
    private val client: SupabaseClient,
    private val failures: RequestFailureClassifier,
) : MetalRatesRepository {

    override suspend fun rates(): RatesResult = try {
        val rows = client.postgrest.from(TABLE_METAL_RATES)
            .select()
            .decodeList<MetalRateRow>()

        RatesResult.Loaded(rows.map { it.toDomain() })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RatesResult.Failed(failures.classify(e))
    }

    override suspend fun setRate(metal: Metal, ratePerGram: Double?): UpdateRateResult = try {
        // `select()` so the response carries the row that changed —
        // PostgREST answers 204 whether one row matched or none
        // (android-app.md §2.6d). It also brings back the timestamp the
        // trigger just wrote, which is what the screen shows and what
        // this app must never compute for itself.
        val changed = client.postgrest.from(TABLE_METAL_RATES)
            .update({ set(COLUMN_RATE_PER_GRAM, ratePerGram) }) {
                select()
                filter { eq(COLUMN_METAL, metal.column()) }
            }
            .decodeList<MetalRateRow>()

        val row = changed.firstOrNull()
        if (row == null) UpdateRateResult.Missing else UpdateRateResult.Updated(row.toDomain())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        UpdateRateResult.Failed(failures.classify(e))
    }

    private fun MetalRateRow.toDomain() = MetalRate(
        metal = metal.toDomain(),
        ratePerGram = ratePerGram,
        setAt = updatedAt,
    )

    private fun MetalColumn.toDomain(): Metal = when (this) {
        MetalColumn.GOLD -> Metal.Gold
        MetalColumn.SILVER -> Metal.Silver
    }

    /** The enum label Postgres stores. Matched to `@SerialName` in M6.6. */
    private fun Metal.column(): String = when (this) {
        Metal.Gold -> "gold"
        Metal.Silver -> "silver"
    }

    private companion object {
        const val TABLE_METAL_RATES = "metal_rates"
        const val COLUMN_RATE_PER_GRAM = "rate_per_gram"
        const val COLUMN_METAL = "metal"
    }
}
