package com.snjewellery.admin.domain.catalogue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the search box hands to PostgREST.
 *
 * The name and tag conditions are combined in a comma-delimited logic
 * tree, so a comma in the term ends the first condition early and the
 * request comes back `PGRST100` — the owner typing `gold, silver` saw
 * *"The catalogue could not be loaded"*. Verified against the live
 * database on 2026-08-16, then fixed here.
 */
class CatalogueQueryTest {

    @Test
    fun `an ordinary term passes through trimmed`() {
        assertEquals("kundan", CatalogueQuery(text = "  kundan  ").term)
    }

    @Test
    fun `a comma cannot reach the filter, because it would break the request`() {
        val term = CatalogueQuery(text = "gold, silver").term

        assertEquals("gold  silver", term)
        assertTrue("a comma here is a 400, not a bad search", !term.orEmpty().contains(','))
    }

    @Test
    fun `braces and quotes cannot reach the filter either`() {
        // `tags.cs.{x}` is an array literal; a brace or a quote inside the
        // term rewrites it into something else.
        assertNull(CatalogueQuery(text = """{}"\""").term)
    }

    @Test
    fun `a term that is only punctuation is no term at all`() {
        // Not an empty search for the empty string, which would match
        // everything and read as the filter having silently failed.
        assertNull(CatalogueQuery(text = ",,,").term)
    }

    @Test
    fun `blank is no term, not an empty one`() {
        assertNull(CatalogueQuery(text = "   ").term)
        assertNull(CatalogueQuery().term)
    }

    @Test
    fun `spaces inside a term survive, because tags have them`() {
        // `daily wear` is a real seeded tag, and PostgREST accepts the
        // space unquoted — checked against the live database.
        assertEquals("daily wear", CatalogueQuery(text = "daily wear").term)
    }

    @Test
    fun `the default query is not a filtered one`() {
        assertTrue(!CatalogueQuery().isFiltered)
    }

    @Test
    fun `a punctuation-only search does not count as filtering`() {
        // Otherwise the screen would show "no pieces match" with a Clear
        // Filters button, for a search that is not filtering anything.
        assertTrue(!CatalogueQuery(text = ",").isFiltered)
    }

    @Test
    fun `each filter on its own counts as filtering`() {
        assertTrue(CatalogueQuery(text = "kundan").isFiltered)
        assertTrue(CatalogueQuery(categoryId = "some-id").isFiltered)
        assertTrue(CatalogueQuery(status = StatusFilter.Archived).isFiltered)
    }
}
