package com.snjewellery.admin.ui.screens.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.CatalogueCursor
import com.snjewellery.admin.domain.catalogue.CatalogueEntry
import com.snjewellery.admin.domain.catalogue.CatalogueListRepository
import com.snjewellery.admin.domain.catalogue.CataloguePageResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The catalogue list's state.
 *
 * ── Why the first page and later pages are different states ───────────
 * A first load has nothing to show, so it gets skeletons and, if it fails,
 * the whole screen. A later page has a screenful of pieces already on it,
 * so it gets a row at the bottom and, if it fails, an error *there* —
 * replacing a working list with an error page because page four did not
 * arrive would be the app throwing away what it already had.
 *
 * ux.md rule 3, applied twice on one screen.
 */
data class CatalogueUiState(
    val entries: List<CatalogueEntry> = emptyList(),
    /** The first page is on its way and there is nothing to show yet. */
    val loading: Boolean = true,
    /** A page after the first is on its way. */
    val loadingMore: Boolean = false,
    /** The first page failed. Covers the whole screen. */
    val failure: RequestFailure? = null,
    /** A later page failed. Shown at the bottom, list intact. */
    val moreFailure: RequestFailure? = null,
    /** Whether there is another page to ask for. */
    val hasMore: Boolean = false,
) {
    /**
     * Nothing uploaded yet — the empty state, not an error. Only true once
     * a load has actually succeeded, so an empty list on the way to the
     * first page never flashes "no pieces yet".
     */
    val isEmpty: Boolean get() = entries.isEmpty() && !loading && failure == null
}

/**
 * The owner's catalogue, newest first, a page at a time.
 *
 * ── Loading is not an optimistic guess ────────────────────────────────
 * `loading` starts true rather than false. The screen's first frame is
 * drawn before the first request has come back, and starting false would
 * show the empty state — "no pieces yet" — to an owner with a full
 * catalogue, for as long as the request takes on mobile data.
 */
@HiltViewModel
class CatalogueViewModel @Inject constructor(
    private val repository: CatalogueListRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogueUiState())
    val uiState: StateFlow<CatalogueUiState> = _uiState.asStateFlow()

    private var nextCursor: CatalogueCursor? = null

    init {
        refresh()
    }

    /**
     * Reads the catalogue again from the newest piece.
     *
     * Everything is discarded rather than merged. The point of a refresh
     * is to see the current truth — a piece deleted on another device, a
     * status changed — and keeping old rows to avoid a flicker is how a
     * list shows something that is no longer there.
     */
    fun refresh() {
        nextCursor = null
        _uiState.value = CatalogueUiState(loading = true)

        viewModelScope.launch {
            when (val result = repository.products()) {
                is CataloguePageResult.Loaded -> {
                    nextCursor = result.page.nextCursor
                    _uiState.value = CatalogueUiState(
                        entries = result.page.entries,
                        loading = false,
                        hasMore = result.page.nextCursor != null,
                    )
                }

                is CataloguePageResult.Failed -> _uiState.value = CatalogueUiState(
                    loading = false,
                    failure = result.failure,
                )
            }
        }
    }

    /**
     * Asks for the next page.
     *
     * Guarded on `loadingMore` because the scroll listener fires on every
     * frame near the bottom, and without it a single flick would request
     * the same page a dozen times. Also guarded on [moreFailure] being
     * clear, so a failed page waits for the owner to retry rather than
     * hammering a connection that has just refused.
     */
    fun loadMore() {
        val cursor = nextCursor ?: return
        val current = _uiState.value
        if (current.loadingMore || current.moreFailure != null) return

        _uiState.update { it.copy(loadingMore = true) }

        viewModelScope.launch {
            when (val result = repository.products(after = cursor)) {
                is CataloguePageResult.Loaded -> {
                    nextCursor = result.page.nextCursor
                    _uiState.update {
                        it.copy(
                            entries = it.entries + result.page.entries,
                            loadingMore = false,
                            hasMore = result.page.nextCursor != null,
                        )
                    }
                }

                is CataloguePageResult.Failed -> _uiState.update {
                    it.copy(loadingMore = false, moreFailure = result.failure)
                }
            }
        }
    }

    /** Clears a page failure so [loadMore] will try again. */
    fun retryMore() {
        _uiState.update { it.copy(moreFailure = null) }
        loadMore()
    }
}
