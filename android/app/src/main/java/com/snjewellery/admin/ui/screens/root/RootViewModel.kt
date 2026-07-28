package com.snjewellery.admin.ui.screens.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.auth.AuthRepository
import com.snjewellery.admin.domain.auth.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Decides which screen the app opens on.
 *
 * Exists so the answer comes from the persisted session rather than from a
 * flag in the activity. Before M6.8 the activity held `remember { false }`,
 * which meant every relaunch showed the login screen even though the
 * session was still valid.
 *
 * The initial value is [AuthState.Restoring] deliberately — see the note
 * on that type. Showing the login form before the stored session has been
 * read looks to the owner like having been logged out.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    authRepository: AuthRepository,
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = AuthState.Restoring,
    )

    private companion object {
        /** Survives a configuration change without restarting collection. */
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
