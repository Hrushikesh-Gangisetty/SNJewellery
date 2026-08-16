package com.snjewellery.admin.domain.rates

import com.snjewellery.admin.domain.RequestFailure

/**
 * The two metals the shop quotes. Exactly two, permanently — the table's
 * primary key is this enum, so a third rate, a duplicate and a missing
 * row are all impossible.
 */
enum class Metal {
    Gold,
    Silver,
}

/**
 * Today's rate for one metal, per gram.
 *
 * [ratePerGram] and [setAt] are null or non-null **together**, enforced
 * by a CHECK and a trigger rather than by callers remembering. A
 * timestamp outliving the number it describes would tell a customer a
 * stale rate was set this morning — the exact bug
 * `20260727000300_metal_rates_timestamp_invariant.sql` was written for.
 *
 * Null means *not published*. The website hides the panel entirely until
 * **both** metals are set, so an unset rate is a normal state and not a
 * broken one.
 */
data class MetalRate(
    val metal: Metal,
    val ratePerGram: Double?,
    /** ISO-8601, as Postgres sends it. Null while unpublished. */
    val setAt: String?,
) {
    val published: Boolean get() = ratePerGram != null
}

sealed interface RatesResult {
    /** Always both metals — the table cannot hold any other number. */
    data class Loaded(val rates: List<MetalRate>) : RatesResult
    data class Failed(val failure: RequestFailure) : RatesResult
}

sealed interface UpdateRateResult {
    data class Updated(val rate: MetalRate) : UpdateRateResult

    /**
     * The update matched no row.
     *
     * Unreachable in practice — the two rows are permanent and there is no
     * DELETE policy for anyone — but it is what PostgREST's 204 means when
     * nothing changed, and treating it as success is the mistake
     * android-app.md §2.6d is about. Reported rather than assumed away.
     */
    data object Missing : UpdateRateResult

    data class Failed(val failure: RequestFailure) : UpdateRateResult
}

/**
 * Reading and setting the daily metal rates.
 *
 * ── Update only ──────────────────────────────────────────────────────
 * There is no create and no delete, and that is the schema's decision
 * rather than an omission here: `metal_rates` has no INSERT and no DELETE
 * policy for anyone, admins included. The shape of the table is not
 * something a client may change — only the two numbers in it.
 */
interface MetalRatesRepository {
    suspend fun rates(): RatesResult

    /**
     * Publishes a rate, or unpublishes it when [ratePerGram] is null.
     *
     * Null is a real operation, not a way of skipping the write: "we have
     * not set today's rate" is a state the shop genuinely has, and the
     * website hides the panel for it. The trigger clears the timestamp to
     * match, so an unpublished rate cannot keep yesterday's date.
     */
    suspend fun setRate(metal: Metal, ratePerGram: Double?): UpdateRateResult
}

/** What is wrong with a typed rate, before anything is sent. */
enum class RateProblem {
    /** Not a number at all. */
    NotANumber,

    /** `metal_rates_rate_positive`: a published rate is greater than zero. */
    NotPositive,
}

/**
 * Shape checks for the rate field, mirroring what the column accepts.
 *
 * No Android imports, so `domain` stays testable on the JVM — the rule
 * [ProductFormRules][com.snjewellery.admin.domain.product.ProductFormRules]
 * follows.
 */
object RateRules {

    /**
     * Blank is valid and means **unpublished**, the same way a blank
     * weight means "not weighed". Anything else must be a positive
     * number, because that is exactly what the column accepts.
     */
    fun validate(typed: String): RateProblem? {
        val trimmed = typed.trim()
        if (trimmed.isEmpty()) return null

        val value = trimmed.toDoubleOrNull() ?: return RateProblem.NotANumber
        // Also catches NaN, which is not > 0 — a value `toDoubleOrNull`
        // will happily return for the string "NaN".
        return if (value > 0.0) null else RateProblem.NotPositive
    }

    /** The value to send: null when blank, so blank unpublishes. */
    fun parse(typed: String): Double? = typed.trim().toDoubleOrNull()
}
