package com.snjewellery.admin.domain.product

import java.text.Normalizer

/**
 * Turns a product name into the URL the website will serve it at.
 *
 * The database enforces `^[a-z0-9]+(-[a-z0-9]+)*$` and uniqueness, so
 * anything this produces has to satisfy both or the insert fails with a
 * constraint error the owner cannot act on.
 *
 * ── Accents are folded, other scripts are not ────────────────────────
 * NFD normalisation plus mark-stripping turns `Café` into `cafe`, which
 * is right. It does **not** transliterate Telugu or Devanagari — there
 * is no correct mechanical romanisation, and inventing one would produce
 * URLs that are wrong in a language the shop's customers actually read.
 * A name with no ASCII letters or digits at all therefore slugifies to
 * nothing, and [slugify] returns null rather than a mangled string. The
 * caller supplies the fallback, so the decision is visible where it is
 * made.
 *
 * ── Collisions ───────────────────────────────────────────────────────
 * Two pieces genuinely are often named the same — a shop sells more than
 * one "Plain Gold Bangle Set". [candidates] yields the plain slug first
 * and then numbered ones, and the caller walks them against the
 * database's unique constraint. Suffixing on **rejection** rather than
 * checking first is what makes it correct under a race: a SELECT that
 * finds nothing can still be beaten to the INSERT.
 */
object Slugs {

    /** Longest slug we will generate, so a long name cannot bloat a URL. */
    private const val MAX_LENGTH = 60

    /** How many numbered variants to try before giving up. */
    const val MAX_CANDIDATES = 20

    private val NON_SLUG = Regex("[^a-z0-9]+")
    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val EDGE_HYPHENS = Regex("^-+|-+$")

    /**
     * `"Men's Signet Ring"` → `"mens-signet-ring"`.
     *
     * Returns null when the name contains nothing a slug may hold — see
     * the note on scripts above.
     */
    fun slugify(name: String): String? {
        val folded = Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase()

        val slug = folded
            .replace(NON_SLUG, "-")
            .replace(EDGE_HYPHENS, "")
            .take(MAX_LENGTH)
            // take() can leave a trailing hyphen, which the CHECK rejects.
            .replace(EDGE_HYPHENS, "")

        return slug.ifEmpty { null }
    }

    /**
     * The slugs to try, in order: `bangle-set`, `bangle-set-2`, … Lazy,
     * so a name that is unique costs one string.
     */
    fun candidates(base: String): Sequence<String> = sequence {
        yield(base)
        for (n in 2..MAX_CANDIDATES) {
            // Trimmed so base + suffix still fits; the suffix is what
            // carries the uniqueness, so it is the part that must survive.
            val suffix = "-$n"
            yield(base.take(MAX_LENGTH - suffix.length).replace(EDGE_HYPHENS, "") + suffix)
        }
    }
}
