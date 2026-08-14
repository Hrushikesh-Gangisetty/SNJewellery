package com.snjewellery.admin.domain.config

/**
 * Where a piece lives on the customer website.
 *
 * The same argument as
 * [StoragePaths][com.snjewellery.admin.domain.product.StoragePaths]: one
 * place builds the address, so there is no second spelling of it to
 * drift. A wrong URL here does not fail loudly — it opens a 404 on the
 * shop's own site, which the owner would reasonably read as their piece
 * not having saved.
 *
 * **`/product/{slug}` is the website's route**, defined by
 * `web/app/product/[slug]`. The two have to change together; there is no
 * mechanism that would catch it if they did not, which is why it is
 * written down in one file rather than assembled at a call site.
 *
 * No Android imports, so `domain` stays testable on the JVM.
 */
object WebsiteLinks {

    /**
     * [baseUrl] is the site's origin, with or without a trailing slash —
     * the trailing slash is the thing everyone gets wrong when typing a
     * value into `local.properties`, and doubling it produces a URL that
     * looks right and 404s.
     */
    fun product(baseUrl: String, slug: String): String =
        "${baseUrl.trimEnd('/')}/$PRODUCT_SEGMENT/$slug"

    private const val PRODUCT_SEGMENT = "product"
}
