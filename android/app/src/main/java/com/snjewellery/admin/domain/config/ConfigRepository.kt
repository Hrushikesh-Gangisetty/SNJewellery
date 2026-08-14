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

    /**
     * The customer website's origin, or **null when this build was not
     * told one**.
     *
     * Optional rather than part of [ConfigStatus], because the app is
     * fully usable without it — the only thing it enables is opening a
     * saved piece on the live site (M7.12). Null while M5 has not yet put
     * the site on a domain, and the screen offers no such button rather
     * than one that would open a 404 on the shop's own site.
     */
    fun websiteUrl(): String?
}
