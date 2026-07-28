package com.snjewellery.admin.data.auth

import com.snjewellery.admin.domain.auth.AuthRepository
import com.snjewellery.admin.domain.auth.SignInResult
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
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
 *   to read a code from. That is an [IOException] from the socket, or
 *   Ktor's own timeout.
 *
 * Matching on a message string would have been the easy version and
 * would break the first time Supabase rewords it.
 *
 * ── Why nothing is logged ────────────────────────────────────────────
 * Not the email, not the exception body. An auth failure's detail can
 * carry the submitted address, and CLAUDE.md §9 forbids logging
 * credentials or sessions. The screen gets a result; logcat gets nothing.
 */
@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val client: SupabaseClient,
) : AuthRepository {

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
        SignInResult.NoNetwork
    } catch (e: CancellationException) {
        // Never swallowed: the screen was navigated away from or the
        // scope closed, and cancellation must keep propagating.
        throw e
    } catch (e: Exception) {
        SignInResult.Failed(e::class.simpleName)
    }
}
