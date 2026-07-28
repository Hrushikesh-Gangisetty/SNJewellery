package com.snjewellery.admin.domain.auth

/**
 * The outcome of a sign-in attempt.
 *
 * Modelled as distinct cases rather than a boolean plus a message,
 * because M6.7's whole requirement is that the failures are told apart.
 * "Wrong password" and "no signal" call for different actions from the
 * person holding the phone, and a single "Sign-in failed" tells them to
 * retype a password that was correct.
 *
 * A `when` over this is exhaustive, so a new failure mode cannot be added
 * without every screen handling it.
 */
sealed interface SignInResult {
    data object Success : SignInResult

    /** The email or the password is wrong. Supabase does not say which. */
    data object InvalidCredentials : SignInResult

    /** The request never reached Supabase. Retrying is worth offering. */
    data object NoNetwork : SignInResult

    /**
     * Anything else — a 500, an expired project, a malformed response.
     * [detail] is for the screen to show, since the owner cannot act on
     * it and will have to describe it to someone who can.
     */
    data class Failed(val detail: String?) : SignInResult
}

/**
 * Email-and-password authentication.
 *
 * The interface lives in `domain` and the Supabase implementation in
 * `data` — ADR-0007. A view model depending on this has no way to reach
 * the Supabase client, which is what keeps the auth SDK out of the UI.
 */
interface AuthRepository {
    /**
     * Signs in, or reports why not. **Does not throw** for any expected
     * failure: an unhandled exception on this path means a crash on the
     * one screen every session starts at.
     */
    suspend fun signIn(email: String, password: String): SignInResult
}
