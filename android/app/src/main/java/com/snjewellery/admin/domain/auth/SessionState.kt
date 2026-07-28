package com.snjewellery.admin.domain.auth

/**
 * What the app should show, once authentication and authorisation are
 * both accounted for.
 *
 * [AuthState] answers "is anyone signed in"; this answers "and may they
 * be here", which is the question the app's entry point actually has.
 * Keeping them separate types would mean every screen that routes had to
 * combine two flows correctly, and the first one that got it wrong would
 * show the dashboard to a refused account for a frame.
 *
 * Every state below renders something. There is no case that leaves the
 * screen blank — which is the whole of M6.9's requirement.
 */
sealed interface SessionState {
    /** Reading the persisted session. Neither screen yet — see [AuthState.Restoring]. */
    data object Restoring : SessionState

    data object SignedOut : SessionState

    /** Signed in; the role query is in flight. */
    data object VerifyingAccess : SessionState

    /** Signed in and `role = 'admin'`. The only state that reaches the dashboard. */
    data object Admin : SessionState

    /**
     * Signed in, but not through to the catalogue — either refused or
     * not established.
     *
     * Grouped so the screen that renders them takes this type rather than
     * the whole of [SessionState]. A `when` over two cases is then
     * exhaustive without an `else`, so a third reason to block someone
     * cannot be added without the screen being made to word it.
     */
    sealed interface Blocked : SessionState

    /** Signed in, and definitively not an administrator. */
    data class Refused(val reason: RefusalReason) : Blocked

    /**
     * Signed in, and the role could not be read. Distinct from [Refused]
     * on purpose: telling an administrator they lack access because the
     * check failed is the same class of mistake as reporting a TLS
     * failure as "no connection" (M6.7).
     */
    data class AccessUnavailable(val offline: Boolean, val detail: String?) : Blocked
}
