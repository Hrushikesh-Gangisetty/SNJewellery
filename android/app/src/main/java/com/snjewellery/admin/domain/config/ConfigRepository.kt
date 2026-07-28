package com.snjewellery.admin.domain.config

/**
 * Supplies the backend configuration.
 *
 * The interface lives in `domain` and its implementation in `data` —
 * ADR-0007's rule that repositories are the only thing touching a data
 * source, stated as a type. A view model depending on this cannot reach
 * `BuildConfig`, or Supabase, or a file, because the interface offers no
 * way to.
 *
 * Not suspending: this particular source is a compile-time constant. A
 * repository reading the network or disk would suspend, and changing
 * this one to do so later is a change to this file and its callers, not
 * to the architecture.
 */
interface ConfigRepository {
    fun status(): ConfigStatus
}
