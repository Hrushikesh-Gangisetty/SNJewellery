package com.snjewellery.admin.domain.config

/**
 * Where the backend is and which key talks to it.
 *
 * A domain type, so nothing above the repository has to know these
 * values arrive from `BuildConfig` — M6.5's Supabase client depends on
 * this, not on the build system.
 *
 * **The key here is always the anon key.** The app's permission to write
 * comes from the authenticated admin's session, never from its key. The
 * service-role key would be extractable from the APK in minutes and
 * bypasses RLS entirely — see ADR-0004 and CLAUDE.md §9.
 */
data class BackendConfig(
    val url: String,
    val anonKey: String,
)

/**
 * Whether the app has what it needs to reach the backend.
 *
 * Modelled as a type rather than a boolean because "not configured" and
 * "configured" lead somewhere different, and the reason has to survive
 * as far as the screen. A build handed to an admin without credentials
 * must say so plainly; the alternative is a login screen that fails with
 * a network error and sends someone hunting for a signal problem that
 * does not exist.
 */
sealed interface ConfigStatus {
    data class Ready(val config: BackendConfig) : ConfigStatus

    /** Which entries are absent, so the message can name them. */
    data class Incomplete(val missing: List<String>) : ConfigStatus
}
