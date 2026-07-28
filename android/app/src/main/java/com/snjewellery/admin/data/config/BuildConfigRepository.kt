package com.snjewellery.admin.data.config

import com.snjewellery.admin.BuildConfig
import com.snjewellery.admin.domain.config.BackendConfig
import com.snjewellery.admin.domain.config.ConfigRepository
import com.snjewellery.admin.domain.config.ConfigStatus
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the backend configuration from `BuildConfig`, where Gradle put
 * it from `local.properties` (docs/architecture/android-build.md §3).
 *
 * **This is the only file in the app that mentions `BuildConfig`.** That
 * is the point of the repository: the build system is a data source like
 * any other, and confining it here means M6.5 can inject a different
 * source — or a test can inject a fake — without a screen noticing.
 *
 * Missing entries are reported by name rather than collapsed into a
 * single "not configured". Whoever prepares a build needs to know which
 * line of `local.properties` they left blank.
 */
@Singleton
class BuildConfigRepository @Inject constructor() : ConfigRepository {

    override fun status(): ConfigStatus {
        val missing = buildList {
            if (BuildConfig.SUPABASE_URL.isBlank()) add(SUPABASE_URL)
            if (BuildConfig.SUPABASE_ANON_KEY.isBlank()) add(SUPABASE_ANON_KEY)
        }

        return if (missing.isEmpty()) {
            ConfigStatus.Ready(
                BackendConfig(
                    url = BuildConfig.SUPABASE_URL,
                    anonKey = BuildConfig.SUPABASE_ANON_KEY,
                ),
            )
        } else {
            ConfigStatus.Incomplete(missing)
        }
    }

    private companion object {
        // The names as they appear in local.properties, so the message
        // names the line to edit rather than a Kotlin symbol.
        const val SUPABASE_URL = "SUPABASE_URL"
        const val SUPABASE_ANON_KEY = "SUPABASE_ANON_KEY"
    }
}
