package com.snjewellery.admin.domain.auth

/**
 * Whether anyone is signed in, as far as the app can currently tell.
 *
 * [Restoring] is the case that matters and the one easy to leave out.
 * On launch the stored session is read from disk, which is not instant,
 * and treating "not yet known" as signed-out shows the login screen for a
 * few frames before replacing it. The owner sees a flash of a form they
 * did not need to fill in, which reads as the app having logged them out.
 */
sealed interface AuthState {
    /** Reading persisted state. Show neither screen yet. */
    data object Restoring : AuthState

    data object SignedOut : AuthState

    data class SignedIn(val userId: String) : AuthState

    /**
     * A stored session exists but could not be refreshed. Distinct from
     * [SignedOut] because the cause is usually the network rather than
     * anything the owner did, and M6.9 onward can retry rather than
     * discard the session.
     */
    data class RefreshFailed(val offline: Boolean) : AuthState
}
