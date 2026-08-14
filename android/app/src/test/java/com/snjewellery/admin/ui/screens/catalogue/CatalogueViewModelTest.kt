package com.snjewellery.admin.ui.screens.catalogue

import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.CatalogueCursor
import com.snjewellery.admin.domain.catalogue.CatalogueEntry
import com.snjewellery.admin.domain.catalogue.CatalogueListRepository
import com.snjewellery.admin.domain.catalogue.CataloguePage
import com.snjewellery.admin.domain.catalogue.CataloguePageResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * The paging state machine.
 *
 * The scroll listener fires on every frame near the bottom of the list, so
 * the interesting behaviour here is all about **what happens when
 * `loadMore` is called more often than there are pages** — and about the
 * two failure states being genuinely different, which is the thing a
 * screenshot of a working list would never show.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CatalogueViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeCatalogueListRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeCatalogueListRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the first page loads on construction`() = runTest(dispatcher) {
        repository.pages = listOf(page("a", "b"), page("c"))

        val state = CatalogueViewModel(repository).uiState.value

        assertEquals(listOf("a", "b"), state.entries.map { it.id })
        assertTrue("another page is available", state.hasMore)
        assertTrue("loading must be over", !state.loading)
        assertNull(state.failure)
    }

    @Test
    fun `loading starts true, so a full catalogue never flashes the empty state`() =
        runTest(dispatcher) {
            repository.gate = CompletableDeferred()
            val viewModel = CatalogueViewModel(repository)

            val beforeTheRequestReturns = viewModel.uiState.value
            assertTrue(beforeTheRequestReturns.loading)
            assertTrue(
                "'no pieces yet' must not show while the first page is in flight",
                !beforeTheRequestReturns.isEmpty,
            )

            repository.gate?.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `a later page appends rather than replacing`() = runTest(dispatcher) {
        repository.pages = listOf(page("a", "b"), page("c", "d"))
        val viewModel = CatalogueViewModel(repository)

        viewModel.loadMore()

        assertEquals(listOf("a", "b", "c", "d"), viewModel.uiState.value.entries.map { it.id })
    }

    @Test
    fun `the cursor advances, so the same page is not read twice`() = runTest(dispatcher) {
        repository.pages = listOf(page("a"), page("b"), page("c"))
        val viewModel = CatalogueViewModel(repository)

        viewModel.loadMore()
        viewModel.loadMore()

        assertEquals(listOf("a", "b", "c"), viewModel.uiState.value.entries.map { it.id })
        // First page, then after "a", then after "b" — never the same
        // cursor twice, which is the whole point of keyset paging.
        assertEquals(listOf(null, "a", "b"), repository.requestedAfter)
    }

    @Test
    fun `the last page stops asking`() = runTest(dispatcher) {
        repository.pages = listOf(page("a"))
        val viewModel = CatalogueViewModel(repository)

        // The scroll listener keeps firing at the bottom of a short list.
        viewModel.loadMore()
        viewModel.loadMore()
        viewModel.loadMore()

        assertTrue(!viewModel.uiState.value.hasMore)
        assertEquals("one request, not four", 1, repository.requestedAfter.size)
    }

    @Test
    fun `a flick near the bottom does not request the same page repeatedly`() =
        runTest(dispatcher) {
            repository.pages = listOf(page("a"), page("b"))
            val viewModel = CatalogueViewModel(repository)
            repository.gate = CompletableDeferred()

            viewModel.loadMore()
            viewModel.loadMore()
            viewModel.loadMore()
            repository.gate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(null, "a"), repository.requestedAfter)
        }

    @Test
    fun `a first-page failure covers the screen`() = runTest(dispatcher) {
        repository.failFrom = 0

        val state = CatalogueViewModel(repository).uiState.value

        assertEquals(OFFLINE, state.failure)
        assertTrue(state.entries.isEmpty())
        assertNull("there is no list to keep, so no bottom error", state.moreFailure)
    }

    @Test
    fun `a later page failing keeps the pieces already loaded`() = runTest(dispatcher) {
        repository.pages = listOf(page("a", "b"), page("c"))
        val viewModel = CatalogueViewModel(repository)
        repository.failFrom = 1

        viewModel.loadMore()

        val state = viewModel.uiState.value
        assertEquals(
            "throwing away a screenful the owner has is worse than the page they did not get",
            listOf("a", "b"),
            state.entries.map { it.id },
        )
        assertEquals(OFFLINE, state.moreFailure)
        assertNull("the whole screen must not become an error", state.failure)
    }

    @Test
    fun `a failed page waits to be retried rather than hammering the connection`() =
        runTest(dispatcher) {
            repository.pages = listOf(page("a"), page("b"))
            val viewModel = CatalogueViewModel(repository)
            repository.failFrom = 1

            viewModel.loadMore()
            val afterTheFailure = repository.requestedAfter.size
            viewModel.loadMore()
            viewModel.loadMore()

            assertEquals(afterTheFailure, repository.requestedAfter.size)

            // Retry is the owner's, and it clears the block.
            repository.failFrom = null
            viewModel.retryMore()
            assertEquals(listOf("a", "b"), viewModel.uiState.value.entries.map { it.id })
        }

    @Test
    fun `refresh discards what was loaded rather than merging`() = runTest(dispatcher) {
        repository.pages = listOf(page("a"), page("b"))
        val viewModel = CatalogueViewModel(repository)
        viewModel.loadMore()

        // A piece deleted elsewhere: the second read returns only one.
        repository.pages = listOf(page("a"))
        repository.requestedAfter.clear()
        viewModel.refresh()

        assertEquals(
            "a merged refresh would still show the piece that is gone",
            listOf("a"),
            viewModel.uiState.value.entries.map { it.id },
        )
        assertEquals(listOf(null), repository.requestedAfter)
    }

    @Test
    fun `an empty catalogue is the empty state, not an error`() = runTest(dispatcher) {
        repository.pages = listOf(CataloguePage(emptyList(), nextCursor = null))

        val state = CatalogueViewModel(repository).uiState.value

        assertTrue(state.isEmpty)
        assertNull(state.failure)
    }

    // ── Fixtures ─────────────────────────────────────────────────────

    /** A page whose entries are named by id, with a cursor on the last. */
    private fun page(vararg ids: String) = CataloguePage(
        entries = ids.map { entry(it) },
        nextCursor = CatalogueCursor(addedAt = "2026-08-14T10:00:00Z", id = ids.last()),
    )

    private fun entry(id: String) = CatalogueEntry(
        id = id,
        name = "Piece $id",
        slug = "piece-$id",
        categoryName = "Necklaces",
        thumbnailUrl = null,
        featured = false,
        sold = false,
        archived = false,
        addedAt = "2026-08-14T10:00:00Z",
    )

    private class FakeCatalogueListRepository : CatalogueListRepository {
        /**
         * Pages in order. The last one's cursor is dropped, so the fake
         * reports "no more" exactly where the real repository would.
         */
        var pages: List<CataloguePage> = listOf(CataloguePage(emptyList(), null))

        /** Refuse from this page index onward. */
        var failFrom: Int? = null

        /** Holds a request open, so concurrent calls can be observed. */
        var gate: CompletableDeferred<Unit>? = null

        /** The cursor id each call asked to start after; null for the first. */
        val requestedAfter = mutableListOf<String?>()

        override suspend fun products(after: CatalogueCursor?): CataloguePageResult {
            gate?.await()

            val index = if (after == null) 0 else pages.indexOfFirst { it.entries.any { e -> e.id == after.id } } + 1
            failFrom?.let { if (index >= it) return CataloguePageResult.Failed(OFFLINE) }

            requestedAfter += after?.id
            val page = pages.getOrNull(index) ?: return CataloguePageResult.Loaded(
                CataloguePage(emptyList(), null),
            )

            // The final page has no next cursor. Getting this wrong in the
            // fake would hide a view model that never stops paging.
            val isLast = index == pages.lastIndex
            return CataloguePageResult.Loaded(
                if (isLast) page.copy(nextCursor = null) else page,
            )
        }
    }

    private companion object {
        val OFFLINE = RequestFailure(offline = true)
    }
}
