package com.snjewellery.admin.domain.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The one thing that can go wrong here is a doubled or missing slash,
 * which produces a URL that looks right and 404s on the shop's own site —
 * where the owner would reasonably read it as their piece not having
 * saved.
 *
 * `WEBSITE_URL` is typed into `local.properties` by hand, so both forms
 * will be entered.
 */
class WebsiteLinksTest {

    @Test
    fun `builds the product URL without a trailing slash on the base`() {
        assertEquals(
            "https://example.com/product/kundan-choker",
            WebsiteLinks.product("https://example.com", "kundan-choker"),
        )
    }

    @Test
    fun `a trailing slash on the base does not double`() {
        assertEquals(
            "https://example.com/product/kundan-choker",
            WebsiteLinks.product("https://example.com/", "kundan-choker"),
        )
    }

    @Test
    fun `several trailing slashes do not double either`() {
        assertEquals(
            "https://example.com/product/kundan-choker",
            WebsiteLinks.product("https://example.com///", "kundan-choker"),
        )
    }

    @Test
    fun `a host with a path prefix keeps it`() {
        assertEquals(
            "https://example.com/shop/product/kundan-choker",
            WebsiteLinks.product("https://example.com/shop", "kundan-choker"),
        )
    }
}
