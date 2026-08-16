package com.snjewellery.admin.ui.screens.options

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.rates.Metal
import com.snjewellery.admin.domain.rates.MetalRate
import com.snjewellery.admin.domain.rates.MetalRatesRepository
import com.snjewellery.admin.domain.rates.RateProblem
import com.snjewellery.admin.domain.rates.RateRules
import com.snjewellery.admin.domain.rates.RatesResult
import com.snjewellery.admin.domain.rates.UpdateRateResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One metal's row on the screen: what the shop last published, and what
 * the owner is typing now.
 *
 * [typed] is deliberately separate from [rate]. They answer different
 * questions — *what the catalogue says* and *what is in the field* — and
 * merging them is how a half-typed number gets shown as though it had
 * been published, or a failed save leaves the field showing a value the
 * website does not have.
 */
data class RateRow(
    val rate: MetalRate,
    val typed: String,
    val problem: RateProblem? = null,
    /** A write is out. The field and its buttons are inert until it lands. */
    val saving: Boolean = false,
    /** Saved a moment ago, so the screen can say so rather than going quiet. */
    val justSaved: Boolean = false,
    val failure: RequestFailure? = null,
) {
    /** Nothing to save when the field already matches what is published. */
    val changed: Boolean
        get() = RateRules.parse(typed) != rate.ratePerGram
}

data class OptionsUiState(
    val rows: List<RateRow> = emptyList(),
    val loading: Boolean = true,
    val failure: RequestFailure? = null,
) {
    /**
     * The website shows the panel only when **both** metals are set, so
     * one rate on its own achieves nothing a customer can see. The screen
     * says so rather than leaving the owner to discover it.
     */
    val halfPublished: Boolean
        get() = !loading && rows.isNotEmpty() && rows.any { it.rate.published } &&
            rows.any { !it.rate.published }
}

/**
 * The owner's options screen. Today's gold and silver rates live here.
 *
 * ── Why this screen exists ───────────────────────────────────────────
 * The PRD's amendment of 2026-07-27 removed per-piece purity and weight
 * from the website and put *today's rate per gram* in their place,
 * "updated daily by the owner from the Android app". The website side
 * shipped in M4.14 — table, policy, and the panel — and nothing was ever
 * built to set the numbers, so the panel has been correctly invisible
 * since. This is that missing half.
 *
 * ── One metal per write ──────────────────────────────────────────────
 * Each row saves on its own. Sending both would make a partial failure
 * ambiguous — the owner would not know which number the catalogue now
 * has — and the two are independent columns whose only relationship is
 * that the website waits for both.
 */
@HiltViewModel
class OptionsViewModel @Inject constructor(
    private val repository: MetalRatesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OptionsUiState())
    val uiState: StateFlow<OptionsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, failure = null) }

        viewModelScope.launch {
            when (val result = repository.rates()) {
                is RatesResult.Loaded -> _uiState.update {
                    it.copy(
                        // Ordered here rather than trusted from the
                        // response: PostgREST returns rows in no
                        // guaranteed order, and a screen whose two fields
                        // swap places between loads is unusable.
                        rows = Metal.entries.mapNotNull { metal ->
                            result.rates.firstOrNull { rate -> rate.metal == metal }
                        }.map { rate -> RateRow(rate = rate, typed = rate.asField()) },
                        loading = false,
                    )
                }

                is RatesResult.Failed -> _uiState.update {
                    it.copy(loading = false, failure = result.failure)
                }
            }
        }
    }

    /** Clears the problem with the keystroke: the owner is already fixing it. */
    fun onRateChange(metal: Metal, typed: String) = updateRow(metal) {
        it.copy(typed = typed, problem = null, failure = null, justSaved = false)
    }

    fun onSave(metal: Metal) {
        val row = _uiState.value.rows.firstOrNull { it.rate.metal == metal } ?: return
        if (row.saving) return

        val problem = RateRules.validate(row.typed)
        if (problem != null) {
            updateRow(metal) { it.copy(problem = problem) }
            return
        }

        updateRow(metal) { it.copy(saving = true, failure = null, justSaved = false) }

        viewModelScope.launch {
            // Blank sends null, which unpublishes. "We have not set
            // today's rate" is a state the shop genuinely has, and the
            // website hides the panel for it.
            when (val result = repository.setRate(metal, RateRules.parse(row.typed))) {
                is UpdateRateResult.Updated -> updateRow(metal) {
                    // The row comes back from the database, so the
                    // timestamp on screen is the one the trigger wrote
                    // rather than one this app guessed at.
                    it.copy(
                        rate = result.rate,
                        typed = result.rate.asField(),
                        saving = false,
                        justSaved = true,
                    )
                }

                is UpdateRateResult.Failed -> updateRow(metal) {
                    it.copy(saving = false, failure = result.failure)
                }

                // Unreachable while the two rows are permanent, and
                // reported rather than assumed away — a 204 over zero
                // rows is not a write that worked.
                is UpdateRateResult.Missing -> updateRow(metal) {
                    it.copy(saving = false, failure = RequestFailure(offline = false))
                }
            }
        }
    }

    private fun updateRow(metal: Metal, transform: (RateRow) -> RateRow) =
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { if (it.rate.metal == metal) transform(it) else it },
            )
        }
}

/**
 * The published rate as field text, or empty when unpublished.
 *
 * Whole rupees lose their `.0`, because a rate is quoted as "9,240" and a
 * field pre-filled with "9240.0" is a number the owner has to edit before
 * they can retype it.
 */
internal fun MetalRate.asField(): String {
    val value = ratePerGram ?: return ""

    return if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
