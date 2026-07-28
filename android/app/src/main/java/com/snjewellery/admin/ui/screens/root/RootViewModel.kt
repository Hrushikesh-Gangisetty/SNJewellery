package com.snjewellery.admin.ui.screens.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.auth.AdminAccess
import com.snjewellery.admin.domain.auth.AdminAccessRepository
import com.snjewellery.admin.domain.auth.AuthRepository
import com.snjewellery.admin.domain.auth.AuthState
import com.snjewellery.admin.domain.auth.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides which screen the app opens on.
 *
 * Exists so the answer comes from the persisted session rather than from a
 * flag in the activity. Before M6.8 the activity held `remember { false }`,
 * which meant every relaunch showed the login screen even though the
 * session was still valid.
 *
 * From M6.9 it answers a second question as well: signing in is not the
 * same as being allowed in. The role check happens **here**, once, on the
 * one path everything else is behind — rather than in each screen, where
 * the first screen that forgot would be a gate that is not a gate.
 *
 * The initial value is [SessionState.Restoring] deliberately — see the
 * note on [AuthState.Restoring]. Showing the login form before the stored
 * session has been read looks to the owner like having been logged out.
 */
@HiltViewModel
class RootViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val adminAccessRepository: AdminAccessRepository,
) : ViewModel() {

    /**
     * Bumped to re-run a check that could not be completed. A counter
     * rather than a signal, so each press is a distinct value and
     * `combine` re-emits even when the auth state has not moved.
     */
    private val accessAttempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionState: StateFlow<SessionState> =
        combine(authRepository.authState, accessAttempt) { auth, _ -> auth }
            // `flatMapLatest`, not `map`: signing out mid-check must
            // cancel the in-flight query rather than let its result
            // arrive afterwards and route a signed-out app somewhere.
            .flatMapLatest { auth ->
                when (auth) {
                    is AuthState.Restoring -> flowOf(SessionState.Restoring)

                    is AuthState.SignedOut -> flowOf(SessionState.SignedOut)

                    // A refresh that failed lands on sign-in. Considered
                    // and rejected in M6.9: an offline failure could keep
                    // the session and retry, but the login screen already
                    // says "no connection — your details were not the
                    // problem" on the next attempt, which is the same
                    // information without a second retry surface to build
                    // and get wrong.
                    is AuthState.RefreshFailed -> flowOf(SessionState.SignedOut)

                    is AuthState.SignedIn -> flow {
                        emit(SessionState.VerifyingAccess)
                        emit(adminAccessRepository.check().toSessionState())
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = SessionState.Restoring,
            )

    /** Re-runs a check that could not be completed, or that has since been granted. */
    fun retryAccessCheck() = accessAttempt.update { it + 1 }

    /**
     * The way out of a refusal. Without it a wrong account is permanent:
     * M6.8 persists the session, so relaunching returns to the same
     * screen. M6.10's logout is this same call from the navigation shell.
     */
    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    private fun AdminAccess.toSessionState(): SessionState = when (this) {
        is AdminAccess.Granted -> SessionState.Admin
        is AdminAccess.Refused -> SessionState.Refused(reason)
        is AdminAccess.Undetermined -> SessionState.AccessUnavailable(offline, detail)
    }

    private companion object {
        /** Survives a configuration change without restarting collection. */
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
