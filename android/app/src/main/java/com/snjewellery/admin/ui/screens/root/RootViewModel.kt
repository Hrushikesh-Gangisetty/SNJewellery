package com.snjewellery.admin.ui.screens.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.snjewellery.admin.domain.auth.AdminAccess
import com.snjewellery.admin.domain.auth.AdminAccessRepository
import com.snjewellery.admin.domain.auth.AuthRepository
import com.snjewellery.admin.domain.auth.AuthState
import com.snjewellery.admin.domain.auth.SessionState
import com.snjewellery.admin.domain.draft.DraftSync
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.scan
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
    private val draftSync: DraftSync,
) : ViewModel() {

    /**
     * Bumped to re-run a check that could not be completed. A counter
     * rather than a signal, so each press is a distinct value and
     * `combine` re-emits even when the auth state has not moved.
     */
    private val accessAttempt = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionState: StateFlow<SessionState> =
        combine(
            // A token refresh re-emits the same signed-in person, and
            // re-running the role query for them is a network round trip
            // that can only produce the answer already held.
            authRepository.authState.distinctUntilChanged(),
            accessAttempt,
        ) { auth, attempt -> auth to attempt }
            // `flatMapLatest`, not `map`: signing out mid-check must
            // cancel the in-flight query rather than let its result
            // arrive afterwards and route a signed-out app somewhere.
            .flatMapLatest { (auth, _) ->
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
            .keepAdmitted()
            .stateIn(
                scope = viewModelScope,
                // Started once and never stopped. `WhileSubscribed` ended
                // collection whenever the activity had been stopped for
                // longer than its timeout, and restarting rebuilt the
                // chain above from nothing — including [keepAdmitted]'s
                // memory of having already admitted this person, which is
                // exactly what must not be forgotten while the owner is in
                // the camera app.
                //
                // A session is a fact about the process, not a
                // subscription belonging to a screen, so nothing is kept
                // alive here that should not be: one StateFlow, collected
                // for as long as the view model lives.
                started = SharingStarted.Lazily,
                initialValue = SessionState.Restoring,
            )

    /**
     * **Must stay below [sessionState].** Kotlin runs initialisers in
     * declaration order, and `viewModelScope` dispatches on
     * `Main.immediate` — so a `launch` from an `init` block above this
     * property runs its body synchronously, inside the constructor,
     * while `sessionState` is still null. That is not a race that
     * sometimes loses: the view model is always built on the main
     * thread, so it crashed the app on every launch.
     */
    init {
        // Started here because this is the one place that knows the
        // session has been admitted, and every write the sync makes needs
        // an admin session — RLS refuses otherwise. Starting it in the
        // Application would mean a signed-out phone recording refusals
        // against the owner's drafts. `start` is idempotent, because this
        // state re-emits Admin whenever the app returns to the
        // foreground.
        viewModelScope.launch {
            sessionState.collect { if (it is SessionState.Admin) draftSync.start() }
        }
    }

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
}

/**
 * Holds [SessionState.Admin] through a re-check.
 *
 * ── The bug this exists for ──────────────────────────────────────────
 * The Supabase SDK reloads its session every time the app returns to the
 * foreground, so its status goes Initializing → Authenticated again, and
 * this flow followed it: **Admin → Restoring → VerifyingAccess → Admin**.
 *
 * `MainActivity` picks a whole screen from that value, so the two middle
 * emissions replaced `AdminNavHost` with the waiting indicator and back
 * again — and a `NavHost` that leaves composition takes its back stack,
 * and every view model scoped to it, with it. The owner filled in the Add
 * Product form, opened the camera, and came back to the dashboard with
 * everything they had typed gone. The photograph was on disk; nothing was
 * left that knew about it. Found by driving M7.3, not in review.
 *
 * ── Why suppressing them is right, not a patch ───────────────────────
 * `Restoring` and `VerifyingAccess` mean *the answer is not known yet*.
 * That is true at launch, and it is what stops the login form flashing
 * past a signed-in owner. It is not true on the way back from the camera:
 * the answer **is** known, and re-deriving it does not un-know it.
 *
 * Only `Admin` is held. A blocked account still shows the waiting state
 * while it retries, because there the answer really is being asked again
 * and the person is waiting for it. And nothing here can keep someone
 * admitted who should not be: a sign-out, a refusal and a failed refresh
 * are all settled answers and all pass straight through.
 */
private fun Flow<SessionState>.keepAdmitted(): Flow<SessionState> =
    scan(SessionState.Restoring as SessionState) { previous, next ->
        val stillDeciding = next is SessionState.Restoring || next is SessionState.VerifyingAccess
        if (stillDeciding && previous is SessionState.Admin) previous else next
    }.distinctUntilChanged()
