package com.snjewellery.admin.ui.screens.options

import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.rates.Metal
import com.snjewellery.admin.domain.rates.MetalRate
import com.snjewellery.admin.domain.rates.MetalRatesRepository
import com.snjewellery.admin.domain.rates.RateProblem
import com.snjewellery.admin.domain.rates.RatesResult
import com.snjewellery.admin.domain.rates.UpdateRateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Setting the daily rates.
 *
 * The interesting behaviour is all in the gap between **what the field
 * says** and **what the catalogue has**: a half-typed number must never
 * be shown as published, a failed save must not leave the screen claiming
 * a rate the website does not have, and an empty field is a real
 * instruction — unpublish — rather than a way of skipping the write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OptionsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var rates: FakeMetalRatesRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        rates = FakeMetalRatesRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `both metals load, gold first, whatever order they arrive in`() = runTest(dispatcher) {
        rates.rates = listOf(silver(null), gold(9240.0))

        val rows = viewModel().uiState.value.rows

        // PostgREST guarantees no order, and two fields that swap places
        // between loads are unusable.
        assertEquals(listOf(Metal.Gold, Metal.Silver), rows.map { it.rate.metal })
        assertEquals("9240", rows.first().typed)
    }

    @Test
    fun `a whole-rupee rate has no trailing decimal in the field`() = runTest(dispatcher) {
        rates.rates = listOf(gold(9240.0), silver(92.5))

        val rows = viewModel().uiState.value.rows

        // "9240.0" is a number the owner has to edit before retyping it.
        assertEquals("9240", rows[0].typed)
        assertEquals("92.5", rows[1].typed)
    }

    @Test
    fun `an unpublished rate is an empty field, not a zero`() = runTest(dispatcher) {
        rates.rates = listOf(gold(null), silver(null))

        val rows = viewModel().uiState.value.rows

        assertEquals("", rows.first().typed)
        assertTrue(!rows.first().rate.published)
    }

    @Test
    fun `saving sends the typed rate and takes the timestamp from the server`() =
        runTest(dispatcher) {
            rates.rates = listOf(gold(null), silver(null))
            val viewModel = viewModel()

            viewModel.onRateChange(Metal.Gold, "9240")
            viewModel.onSave(Metal.Gold)

            assertEquals(listOf(Metal.Gold to 9240.0), rates.writes)
            val row = viewModel.uiState.value.rows.first()
            // The trigger writes updated_at; this app must never compute
            // it, so the row comes back from the database.
            assertEquals(SET_AT, row.rate.setAt)
            assertTrue(row.justSaved)
        }

    @Test
    fun `an empty field unpublishes rather than doing nothing`() = runTest(dispatcher) {
        rates.rates = listOf(gold(9240.0), silver(92.5))
        val viewModel = viewModel()

        viewModel.onRateChange(Metal.Gold, "")
        viewModel.onSave(Metal.Gold)

        // "We are not quoting a rate today" is a state the shop has, and
        // the website hides the panel for it.
        assertEquals(listOf(Metal.Gold to null), rates.writes)
        assertTrue(!viewModel.uiState.value.rows.first().rate.published)
    }

    @Test
    fun `a rate that is not a number never reaches the server`() = runTest(dispatcher) {
        rates.rates = listOf(gold(null), silver(null))
        val viewModel = viewModel()

        viewModel.onRateChange(Metal.Gold, "nine thousand")
        viewModel.onSave(Metal.Gold)

        assertEquals(emptyList<Pair<Metal, Double?>>(), rates.writes)
        assertEquals(RateProblem.NotANumber, viewModel.uiState.value.rows.first().problem)
    }

    @Test
    fun `a rate of zero is refused`() = runTest(dispatcher) {
        rates.rates = listOf(gold(null), silver(null))
        val viewModel = viewModel()

        viewModel.onRateChange(Metal.Gold, "0")
        viewModel.onSave(Metal.Gold)

        assertEquals(emptyList<Pair<Metal, Double?>>(), rates.writes)
        assertEquals(RateProblem.NotPositive, viewModel.uiState.value.rows.first().problem)
    }

    @Test
    fun `a failed save leaves the published rate as it was`() = runTest(dispatcher) {
        rates.rates = listOf(gold(9240.0), silver(92.5))
        val viewModel = viewModel()
        rates.failure = OFFLINE

        viewModel.onRateChange(Metal.Gold, "9300")
        viewModel.onSave(Metal.Gold)

        val row = viewModel.uiState.value.rows.first()
        assertEquals("the catalogue still has the old number", 9240.0, row.rate.ratePerGram)
        assertEquals("and the owner keeps what they typed", "9300", row.typed)
        assertEquals(OFFLINE, row.failure)
        assertTrue(!row.justSaved)
    }

    @Test
    fun `an update that changed nothing is not reported as saved`() = runTest(dispatcher) {
        // PostgREST answers 204 for an update matching zero rows exactly
        // as for one that matched (android-app.md §2.6d).
        rates.rates = listOf(gold(null), silver(null))
        val viewModel = viewModel()
        rates.missing = true

        viewModel.onRateChange(Metal.Gold, "9240")
        viewModel.onSave(Metal.Gold)

        val row = viewModel.uiState.value.rows.first()
        assertTrue("the optimistic value must not stick", !row.rate.published)
        assertTrue(row.failure != null)
    }

    @Test
    fun `saving one metal leaves the other alone`() = runTest(dispatcher) {
        rates.rates = listOf(gold(null), silver(92.5))
        val viewModel = viewModel()

        viewModel.onRateChange(Metal.Gold, "9240")
        viewModel.onSave(Metal.Gold)

        assertEquals(listOf(Metal.Gold to 9240.0), rates.writes)
        assertEquals(92.5, viewModel.uiState.value.rows[1].rate.ratePerGram)
    }

    @Test
    fun `one rate set and the other not is called out`() = runTest(dispatcher) {
        rates.rates = listOf(gold(9240.0), silver(null))

        // The website renders nothing until both are published, so a
        // half-finished morning achieves nothing a customer can see.
        assertTrue(viewModel().uiState.value.halfPublished)
    }

    @Test
    fun `both set or both unset says nothing`() = runTest(dispatcher) {
        rates.rates = listOf(gold(9240.0), silver(92.5))
        assertTrue(!viewModel().uiState.value.halfPublished)

        rates.rates = listOf(gold(null), silver(null))
        assertTrue(!viewModel().uiState.value.halfPublished)
    }

    @Test
    fun `typing clears the problem, because the owner is already fixing it`() =
        runTest(dispatcher) {
            rates.rates = listOf(gold(null), silver(null))
            val viewModel = viewModel()
            viewModel.onRateChange(Metal.Gold, "x")
            viewModel.onSave(Metal.Gold)

            viewModel.onRateChange(Metal.Gold, "9")

            assertNull(viewModel.uiState.value.rows.first().problem)
        }

    @Test
    fun `a failed load is an error with a way back`() = runTest(dispatcher) {
        rates.failure = OFFLINE

        val state = viewModel().uiState.value

        assertEquals(OFFLINE, state.failure)
        assertTrue(state.rows.isEmpty())
        assertTrue(!state.loading)
    }

    // ── Fixtures ─────────────────────────────────────────────────────

    private fun viewModel() = OptionsViewModel(rates)

    private fun gold(rate: Double?) = MetalRate(Metal.Gold, rate, rate?.let { SET_AT })
    private fun silver(rate: Double?) = MetalRate(Metal.Silver, rate, rate?.let { SET_AT })

    private class FakeMetalRatesRepository : MetalRatesRepository {
        var rates: List<MetalRate> = emptyList()
        var failure: RequestFailure? = null
        var missing = false

        /** Every write, in order. */
        val writes = mutableListOf<Pair<Metal, Double?>>()

        override suspend fun rates(): RatesResult =
            failure?.let { RatesResult.Failed(it) } ?: RatesResult.Loaded(rates)

        override suspend fun setRate(metal: Metal, ratePerGram: Double?): UpdateRateResult {
            failure?.let { return UpdateRateResult.Failed(it) }
            if (missing) return UpdateRateResult.Missing

            writes += metal to ratePerGram
            return UpdateRateResult.Updated(
                MetalRate(metal, ratePerGram, ratePerGram?.let { SET_AT }),
            )
        }
    }

    private companion object {
        const val SET_AT = "2026-08-16T04:12:00Z"
        val OFFLINE = RequestFailure(offline = true)
    }
}
