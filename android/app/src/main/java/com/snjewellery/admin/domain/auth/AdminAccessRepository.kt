package com.snjewellery.admin.domain.auth

/**
 * Why an account was refused.
 *
 * Two cases rather than one because the remedy differs. `NotAnAdmin` is
 * fixed by an administrator granting the role; `NoProfile` is a setup
 * fault — the account can authenticate but has no row in the catalogue's
 * `users` table at all, which is a different conversation.
 *
 * Deliberately not the schema's `UserRole`: `domain` imports nothing from
 * `data`, and naming the role the account *does* hold would tell the
 * person nothing they can act on. `staff` is granted nothing today.
 */
enum class RefusalReason {
    NotAnAdmin,
    NoProfile,
}

/**
 * Whether the signed-in account may administer the catalogue.
 *
 * ── This is not the security boundary ────────────────────────────────
 * RLS is — ADR-0004. A non-admin session physically cannot write,
 * because every write policy is gated on `is_admin()`, and that holds
 * whether or not this check runs or is honest. What this exists for is
 * the sentence after it in ADR-0004: an unauthorised user gets an
 * explanation instead of a wall of requests that silently do nothing.
 *
 * So [Undetermined] is not a security hole. It is the honest answer when
 * the question could not be asked, and it must not be collapsed into
 * either [Granted] (which would let someone into screens whose every
 * action will fail) or [Refused] (which would accuse an administrator of
 * not being one because their train went into a tunnel).
 */
sealed interface AdminAccess {
    data object Granted : AdminAccess

    data class Refused(val reason: RefusalReason) : AdminAccess

    /**
     * The check could not be completed. [offline] separates "no
     * connection" from a server-side failure, because only the first is
     * something the person holding the phone can do anything about.
     * [detail] is a code or exception name for the second, since they
     * will have to describe it to someone who can act on it.
     */
    data class Undetermined(val offline: Boolean, val detail: String?) : AdminAccess
}

/**
 * Reads the signed-in account's role.
 *
 * Separate from [AuthRepository] because it asks a different source a
 * different question: authentication is the Auth API, authorisation is a
 * row in `users`. Both are repositories over the same client, which
 * ADR-0007 permits — what it forbids is a view model reaching either.
 */
interface AdminAccessRepository {
    /**
     * Checks the current session's role. **Does not throw** — every
     * failure is an [AdminAccess.Undetermined], because this runs on the
     * path between signing in and seeing anything at all, and an
     * exception here is a crash with no screen behind it.
     */
    suspend fun check(): AdminAccess
}
