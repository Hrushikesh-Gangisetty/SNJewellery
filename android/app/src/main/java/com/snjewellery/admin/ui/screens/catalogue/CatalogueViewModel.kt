package com.snjewellery.admin.ui.screens.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.catalogue.CatalogueCursor
import com.snjewellery.admin.domain.catalogue.CatalogueEntry
import com.snjewellery.admin.domain.catalogue.CatalogueListRepository
import com.snjewellery.admin.domain.catalogue.CataloguePageResult
import com.snjewellery.admin.domain.catalogue.CatalogueQuery
import com.snjewellery.admin.domain.catalogue.CatalogueRepository
import com.snjewellery.admin.domain.catalogue.CatalogueResult
import com.snjewellery.admin.domain.catalogue.Category
import com.snjewellery.admin.domain.catalogue.StatusFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
    /** What is being asked for. The text is echoed live, before debounce. */
    val query: CatalogueQuery = CatalogueQuery(),
    /** The owner's categories, for the category filter. Empty until loaded. */
    val categories: List<Category> = emptyList(),
) {
    /**
     * Nothing uploaded yet — the empty state, not an error. Only true once
     * a load has actually succeeded, so an empty list on the way to the
     * first page never flashes "no pieces yet".
     */
    val isEmpty: Boolean get() = entries.isEmpty() && !loading && failure == null

    /**
     * Nothing *matched* — a different message from nothing existing, with a
     * different next step ("clear the filters", not "add a piece"). Getting
     * these two the same way round is the commonest version of the mistake
     * ux.md rule 3 is about.
     */
    val isNoMatch: Boolean get() = isEmpty && query.isFiltered
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
@OptIn(FlowPreview::class)
@HiltViewModel
class CatalogueViewModel @Inject constructor(
    private val repository: CatalogueListRepository,
    private val catalogueRepository: CatalogueRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CatalogueUiState())
    val uiState: StateFlow<CatalogueUiState> = _uiState.asStateFlow()

    private var nextCursor: CatalogueCursor? = null

    /**
     * The first page in flight, held so a new filter can cancel it.
     *
     * Without this, typing "cha" then "chai" leaves two requests racing and
     * the list shows whichever the network happened to finish last — which
     * on a slow connection is quite often the *earlier*, wrong one.
     */
    private var loadJob: Job? = null

    /** Keystrokes, before debounce. Only text goes through here. */
    private val typed = MutableStateFlow("")

    /**
     * The search text the current results were actually read with.
     *
     * Needed because [onSearchChange] echoes the keystroke into
     * [CatalogueUiState.query] straight away, so by the time the debounce
     * fires the query already *says* the new text — and comparing the two
     * would conclude nothing had changed and never reload. This is the
     * text the server has seen, which is the question worth asking.
     */
    private var committedText = ""

    init {
        refresh()
        loadCategories()

        viewModelScope.launch {
            typed
                // The initial empty value is the screen opening, not a
                // search being cleared, and `refresh()` above covers it.
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                // The text is already in the state; what has not happened
                // yet is the read. Skipped when a filter tap has already
                // reloaded with this same text — `onClearFilters` is the
                // case that would otherwise reload twice.
                .collect { text -> if (text != committedText) refresh() }
        }
    }

    /**
     * Reads the catalogue again from the newest matching piece, keeping the
     * current filters.
     *
     * Everything is discarded rather than merged. The point of a refresh
     * is to see the current truth — a piece deleted on another device, a
     * status changed — and keeping old rows to avoid a flicker is how a
     * list shows something that is no longer there.
     */
    fun refresh() {
        val query = _uiState.value.query
        val categories = _uiState.value.categories

        nextCursor = null
        committedText = query.text
        _uiState.value = CatalogueUiState(loading = true, query = query, categories = categories)

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            when (val result = repository.products(query)) {
                is CataloguePageResult.Loaded -> {
                    nextCursor = result.page.nextCursor
                    _uiState.update {
                        it.copy(
                            entries = result.page.entries,
                            loading = false,
                            hasMore = result.page.nextCursor != null,
                        )
                    }
                }

                is CataloguePageResult.Failed -> _uiState.update {
                    it.copy(loading = false, failure = result.failure)
                }
            }
        }
    }

    /**
     * Echoes the keystroke immediately and searches after a pause.
     *
     * The field must never lag behind the finger, so the text is in the
     * state at once; the request waits, because a query per keystroke is
     * eight requests to spell "earrings" on a connection that can barely
     * afford one.
     */
    fun onSearchChange(text: String) {
        _uiState.update { it.copy(query = it.query.copy(text = text)) }
        typed.value = text
    }

    /** A tap, not a keystroke: no debounce, because there is nothing to wait for. */
    fun onStatusChange(status: StatusFilter) = applyQuery { it.copy(status = status) }

    fun onCategoryChange(categoryId: String?) = applyQuery { it.copy(categoryId = categoryId) }

    /** Back to every live piece, in one tap. */
    fun onClearFilters() {
        typed.value = ""
        applyQuery { CatalogueQuery() }
    }

    private fun applyQuery(transform: (CatalogueQuery) -> CatalogueQuery) {
        val next = transform(_uiState.value.query)
        if (next == _uiState.value.query) return

        _uiState.update { it.copy(query = next) }
        refresh()
    }

    /**
     * The category filter's options.
     *
     * A failure here is silent by design: it costs the owner the category
     * filter, not the list, and an error banner over a working catalogue
     * for a control they may not be about to use would be the app
     * complaining about its own furniture.
     */
    private fun loadCategories() {
        viewModelScope.launch {
            val result = catalogueRepository.categories()
            if (result is CatalogueResult.Loaded) {
                _uiState.update { it.copy(categories = result.items) }
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
            when (val result = repository.products(_uiState.value.query, after = cursor)) {
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

    private companion object {
        /**
         * Long enough that an ordinary typing rhythm produces one request
         * rather than eight, short enough that a deliberate pause feels
         * like an answer rather than a wait.
         */
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
