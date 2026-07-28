package com.snjewellery.admin.ui.screens.shell

import androidx.lifecycle.ViewModel
import com.snjewellery.admin.domain.config.ConfigRepository
import com.snjewellery.admin.domain.config.ConfigStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** What the shell screen renders. */
data class ShellUiState(
    val configured: Boolean,
    /** Entries absent from local.properties. Empty when configured. */
    val missingConfig: List<String> = emptyList(),
)

/**
 * The shell's view model.
 *
 * It exists to answer one question the admin genuinely needs answered
 * before M6.7 gives them a login screen: **has this build been given
 * credentials?** A build handed over without them would otherwise fail
 * at the first sign-in with a network error, and send someone hunting
 * for a signal problem that does not exist.
 *
 * It also demonstrates the wiring M6.4 exists to establish — a view
 * model resolving a repository through Hilt, depending on the `domain`
 * interface and never on the `data` implementation behind it.
 *
 * State is exposed as [StateFlow] rather than as Compose state: view
 * models in this app do not depend on Compose, so they stay testable
 * without a UI toolkit and reusable if a screen is ever rewritten.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    configRepository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        when (val status = configRepository.status()) {
            is ConfigStatus.Ready -> ShellUiState(configured = true)
            is ConfigStatus.Incomplete -> ShellUiState(
                configured = false,
                missingConfig = status.missing,
            )
        },
    )

    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()
}
