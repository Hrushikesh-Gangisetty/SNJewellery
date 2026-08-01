package com.snjewellery.admin.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import com.snjewellery.admin.domain.config.ConfigRepository
import com.snjewellery.admin.domain.config.ConfigStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** What the dashboard renders. The four metrics arrive in M6.11. */
data class DashboardUiState(
    val configured: Boolean,
    /** Entries absent from local.properties. Empty when configured. */
    val missingConfig: List<String> = emptyList(),
)

/**
 * The dashboard's view model. Was `ShellViewModel` until M6.10 made the
 * post-login screen a real destination.
 *
 * It answers one question today: **has this build been given
 * credentials?** A build handed to an admin without them would otherwise
 * fail at the first request and send someone hunting for a signal problem
 * that does not exist.
 *
 * M6.11 adds the four live metrics beside that. Nothing here fabricates
 * one in the meantime — the screen says the dashboard is unbuilt rather
 * than showing a plausible zero.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        when (val status = configRepository.status()) {
            is ConfigStatus.Ready -> DashboardUiState(configured = true)
            is ConfigStatus.Incomplete -> DashboardUiState(
                configured = false,
                missingConfig = status.missing,
            )
        },
    )

    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
}
