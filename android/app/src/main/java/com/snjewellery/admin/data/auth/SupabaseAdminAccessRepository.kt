package com.snjewellery.admin.data.auth

import com.snjewellery.admin.data.models.UserRole
import com.snjewellery.admin.data.models.UserRow
import com.snjewellery.admin.data.remote.TransportFailure
import com.snjewellery.admin.data.remote.TransportFailureRecorder
import com.snjewellery.admin.domain.auth.AdminAccess
import com.snjewellery.admin.domain.auth.AdminAccessRepository
import com.snjewellery.admin.domain.auth.RefusalReason
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.CancellationException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the signed-in account's role from `users`.
 *
 * ── Why the row is fetched rather than read from the token ───────────
 * The session's JWT carries no role claim, and even if it did, a claim is
 * whatever was true when the token was minted — an account demoted this
 * morning would still present an hour-old `admin`. The row is the
 * authority, and it is what the RLS policies themselves evaluate.
 *
 * ── Why the filter is explicit ───────────────────────────────────────
 * `users_read_self` restricts a non-admin to their own row, so an
 * unfiltered select would *look* correct. It is not: `users_admin_read_all`
 * returns every row to an admin, and `decodeSingleOrNull` would then hand
 * back whichever row Postgres listed first. Filtering on the current
 * user's id is what makes the query mean the same thing for both.
 *
 * ── Failure is not refusal ───────────────────────────────────────────
 * Every unexpected path returns [AdminAccess.Undetermined]. See the note
 * on that type: refusing on a failed check would tell the shop owner they
 * are not an administrator of their own catalogue whenever the signal
 * drops.
 *
 * Nothing here is logged, for the same reason as [SupabaseAuthRepository]:
 * the responses on this path carry an account's email.
 */
@Singleton
class SupabaseAdminAccessRepository @Inject constructor(
    private val client: SupabaseClient,
    private val transportFailures: TransportFailureRecorder,
) : AdminAccessRepository {

    override suspend fun check(): AdminAccess = try {
        val userId = currentUserId()
        if (userId == null) {
            // Signed out between the caller deciding to check and the
            // check running. Not a refusal — there is nobody to refuse.
            AdminAccess.Undetermined(offline = false, detail = NO_SESSION)
        } else {
            // The whole row, decoded through the M6.6 contract model
            // rather than a local shape: one definition of `users` in
            // this app, and a schema drift surfaces here as a
            // deserialisation failure instead of a silently absent field.
            val profile = client.postgrest
                .from(TABLE_USERS)
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<UserRow>()

            when (profile?.role) {
                UserRole.ADMIN -> AdminAccess.Granted
                // A row exists and says something other than admin.
                UserRole.STAFF -> AdminAccess.Refused(RefusalReason.NotAnAdmin)
                // Authenticated with no profile row. The signup trigger
                // creates one, so this means an account made before it
                // existed, or a row deleted by hand.
                null -> AdminAccess.Refused(RefusalReason.NoProfile)
            }
        }
    } catch (e: HttpRequestTimeoutException) {
        AdminAccess.Undetermined(offline = true, detail = null)
    } catch (e: IOException) {
        // As in SupabaseAuthRepository: the SDK flattens every transport
        // failure into an IOException carrying no cause, so the real
        // reason comes from the interceptor that saw it first.
        when (val failure = transportFailures.take()) {
            is TransportFailure.Tls ->
                AdminAccess.Undetermined(offline = false, detail = failure.detail)
            is TransportFailure.Other ->
                AdminAccess.Undetermined(offline = false, detail = failure.detail)
            is TransportFailure.Unreachable, null ->
                AdminAccess.Undetermined(offline = true, detail = null)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Includes PostgrestRestException — an expired JWT, a policy
        // change, a project paused. Its `code` is a Postgres or PostgREST
        // code and is safe to show; its message can quote the request.
        AdminAccess.Undetermined(offline = false, detail = e.postgrestCodeOrType())
    }

    /**
     * The session's user id, refetched if the restored session did not
     * carry one. A stored session normally does, but an id read as blank
     * would filter to zero rows and look exactly like a missing profile —
     * a refusal caused by a decoding detail.
     */
    private suspend fun currentUserId(): String? =
        client.auth.currentUserOrNull()?.id?.takeIf { it.isNotBlank() }
            ?: client.auth.currentSessionOrNull()?.let {
                client.auth.retrieveUserForCurrentSession(updateSession = true).id
            }

    private fun Exception.postgrestCodeOrType(): String? =
        (this as? PostgrestRestException)?.code ?: this::class.simpleName

    private companion object {
        const val TABLE_USERS = "users"

        /** Shown as the detail when there is no session to check. */
        const val NO_SESSION = "no session"
    }
}
