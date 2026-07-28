package com.snjewellery.admin.data.auth

import com.snjewellery.admin.data.remote.TransportFailure
import com.snjewellery.admin.data.remote.TransportFailureRecorder
import com.snjewellery.admin.domain.auth.AuthRepository
import com.snjewellery.admin.domain.auth.AuthState
import com.snjewellery.admin.domain.auth.SignInResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signs in through Supabase Auth.
 *
 * The only file in the app that calls the auth SDK — ADR-0007's rule that
 * repositories are the only thing touching a data source.
 *
 * ── Telling the failures apart ───────────────────────────────────────
 * This is the substance of M6.7, and the two cases are distinguished by
 * *where* they fail rather than by parsing a message:
 *
 * - **Wrong credentials** — the request reached Supabase and it answered
 *   400 with `error_code: invalid_credentials`. The SDK surfaces that as
 *   [AuthRestException] carrying [AuthErrorCode.InvalidCredentials].
 *   Confirmed against the live project, not assumed from documentation.
 * - **No network** — the request never arrived, so there is no response
 *   to read a code from.
 *
 * Matching on a message string would have been the easy version and
 * would break the first time Supabase rewords it.
 *
 * **Not everything that fails to connect is "no connection".** A TLS
 * failure is reported as itself, because telling someone to check their
 * signal when the device clock is wrong sends them to look in the wrong
 * place entirely. Distinguishing them needs
 * [TransportFailureRecorder], because the SDK discards the cause — see
 * the note there.
 *
 * ── Why nothing is logged ────────────────────────────────────────────
 * Not the email, not the exception body. An auth failure's detail can
 * carry the submitted address, and CLAUDE.md §9 forbids logging
 * credentials or sessions. The screen gets a result; logcat gets nothing.
 */
@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val client: SupabaseClient,
    private val transportFailures: TransportFailureRecorder,
) : AuthRepository {

    /**
     * Mapped from the SDK's own session stream rather than tracked here,
     * so a token refresh, a restore from disk and a sign-out all reach the
     * UI through one path. Keeping a second copy of this state is how the
     * screen and the session drift apart.
     */
    override val authState: Flow<AuthState> = client.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Initializing -> AuthState.Restoring
            is SessionStatus.Authenticated -> AuthState.SignedIn(status.session.user?.id.orEmpty())
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            is SessionStatus.RefreshFailure -> AuthState.RefreshFailed(
                offline = status.cause is RefreshFailureCause.NetworkError,
            )
        }
    }

    override suspend fun signIn(email: String, password: String): SignInResult = try {
        client.auth.signInWith(Email) {
            // Trimmed because a keyboard's autocomplete adds a trailing
            // space often enough to matter, and it is never significant.
            this.email = email.trim()
            this.password = password
        }
        SignInResult.Success
    } catch (e: AuthRestException) {
        if (e.errorCode == AuthErrorCode.InvalidCredentials) {
            SignInResult.InvalidCredentials
        } else {
            // A real server-side problem, not a typo. `errorDescription`
            // is Supabase's own wording and is safe to show; the
            // exception message is not, as it can echo the request.
            SignInResult.Failed(e.errorCode?.value ?: e.errorDescription)
        }
    } catch (e: HttpRequestTimeoutException) {
        // A timeout on a slow connection is indistinguishable from being
        // offline, from the owner's point of view, and the advice is the
        // same. Treated as no-network rather than as an unknown error.
        SignInResult.NoNetwork
    } catch (e: IOException) {
        // Every transport failure arrives here, including TLS ones: the
        // SDK rethrows them all as HttpRequestException, which extends
        // IOException and carries no cause. Catching SSLException above
        // this would be dead code — verified, not assumed.
        //
        // So the real reason comes from the interceptor that saw it
        // before the SDK flattened it. See TransportFailureRecorder.
        when (val failure = transportFailures.take()) {
            is TransportFailure.Tls ->
                // NOT no-network. The socket opened and the certificate
                // check failed — on a device that is a wrong clock or an
                // intercepting proxy. Saying "check your signal" sends
                // someone to look at Wi-Fi for an unrelated problem.
                SignInResult.Failed(failure.detail)
            is TransportFailure.Other -> SignInResult.Failed(failure.detail)
            is TransportFailure.Unreachable, null -> SignInResult.NoNetwork
        }
    } catch (e: CancellationException) {
        // Never swallowed: the screen was navigated away from or the
        // scope closed, and cancellation must keep propagating.
        throw e
    } catch (e: Exception) {
        SignInResult.Failed(e::class.simpleName)
    }

    /**
     * Never fails from the caller's point of view. The SDK's default
     * scope revokes locally *and* on the server; a server call that
     * cannot be made must still clear the stored session, or a refused
     * or logged-out account stays signed in on the device because the
     * network was down at the wrong moment.
     */
    override suspend fun signOut() {
        try {
            client.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not belt-and-braces: `signOut` posts to `logout` and only
            // clears local state *afterwards*, so a failed request skips
            // the cleanup entirely and leaves the session on disk.
            // Checked in the SDK's source, not assumed.
            client.auth.clearSession()
        }
    }
}
