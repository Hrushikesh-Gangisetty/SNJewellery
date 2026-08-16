package com.snjewellery.admin.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.config.ConfigRepository
import com.snjewellery.admin.domain.config.ConfigStatus
import com.snjewellery.admin.domain.dashboard.DashboardMetrics
import com.snjewellery.admin.domain.dashboard.DashboardRepository
import com.snjewellery.admin.domain.dashboard.MetricsResult
import com.snjewellery.admin.domain.draft.DraftRepository
import com.snjewellery.admin.domain.draft.PendingDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the dashboard is showing.
 *
 * A sealed type rather than a `DashboardUiState` with nullable metrics
 * and an `isLoading` flag: those permit states that cannot happen —
 * loading *and* failed, loaded *and* erroring — and every screen reading
 * them has to decide which wins. Here the `when` is exhaustive and there
 * is nothing to decide.
 */
sealed interface DashboardState {
    data object Loading : DashboardState

    data class Loaded(val metrics: DashboardMetrics) : DashboardState

    /** ux.md: an inline error where the content belongs, plus Retry. */
    data class Failed(val failure: RequestFailure) : DashboardState
}

/** The dashboard's full UI state: its metrics, plus the build's health. */
data class DashboardUiState(
    val state: DashboardState = DashboardState.Loading,
    val configured: Boolean = true,
    /** Entries absent from local.properties. Empty when configured. */
    val missingConfig: List<String> = emptyList(),
    /**
     * Pieces entered on this phone that are not in the catalogue yet
     * (M8.9).
     *
     * Beside the metrics rather than inside [DashboardState], because
     * they are read from the device and are still true — and still worth
     * showing — when the counts cannot be loaded at all. In fact that is
     * exactly when there are likely to be some.
     */
    val pending: List<PendingDraft> = emptyList(),
)

/**
 * The dashboard's view model.
 *
 * Loads on creation and on demand. Nothing is cached across a reload:
 * the counts are the point of the screen, and showing a stale number
 * beside a spinner is how an owner concludes an upload did not save.
 *
 * It also still reports whether this build was given credentials (M6.4).
 * That is not a metric, so it sits beside the state rather than inside
 * it — a build with no credentials cannot load metrics *and* needs to
 * say why in terms that name the missing `local.properties` line.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    draftRepository: DraftRepository,
    configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        when (val status = configRepository.status()) {
            is ConfigStatus.Ready -> DashboardUiState()
            is ConfigStatus.Incomplete -> DashboardUiState(
                configured = false,
                missingConfig = status.missing,
            )
        },
    )

    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        // Collected rather than loaded on resume like the metrics: this
        // one is a local database that announces its own changes, so a
        // draft written while the dashboard is open appears without the
        // screen going back to ask.
        viewModelScope.launch {
            draftRepository.pending().collect { drafts ->
                _uiState.update { it.copy(pending = drafts) }
            }
        }
    }

    /**
     * Deliberately **not** called from `init`. The screen loads on
     * resume instead, so returning here after saving a product shows a
     * total that includes it. Loading once at construction would leave
     * the view model alive across that navigation and the count stale —
     * which the owner reads as the save having failed.
     */
    fun load() {
        _uiState.update { it.copy(state = DashboardState.Loading) }

        viewModelScope.launch {
            val next = when (val result = dashboardRepository.metrics()) {
                is MetricsResult.Loaded -> DashboardState.Loaded(result.metrics)
                is MetricsResult.Failed -> DashboardState.Failed(result.failure)
            }
            _uiState.update { it.copy(state = next) }
        }
    }
}
