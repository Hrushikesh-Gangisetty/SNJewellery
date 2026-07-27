"use server";

import { getAllProducts, getProductsByCategory } from "./cache";
import type { Page, Product } from "./types";

/**
 * The client-callable surface of the data boundary.
 *
 * The catalogue's "Load more" appends a page without a navigation, which
 * means the browser has to be able to ask for the next page. Two ways to
 * do that were available:
 *
 *   - Query Supabase from the client. The anon key is public, so this
 *     would work — but it ships supabase-js into the bundle of every
 *     catalogue page, and it puts a query outside `lib/data`, which
 *     CLAUDE.md §3.4 forbids.
 *   - A server action, which is this. Nothing extra reaches the bundle
 *     and the read still goes through `CatalogueSource`.
 *
 * Both arguments are attacker-controlled. Neither is trusted: an unknown
 * or hidden `categorySlug` yields an empty page, an unrecognisable
 * `cursor` yields an empty page, and RLS bounds what either could reach
 * regardless. See `decodeCursor` in ./supabase-source.ts.
 */
export async function fetchMoreProducts({
  categorySlug,
  cursor,
}: {
  /** `null` browses the whole catalogue. */
  categorySlug: string | null;
  cursor: string;
}): Promise<Page<Product>> {
  return categorySlug === null
    ? getAllProducts(cursor)
    : getProductsByCategory(categorySlug, cursor);
}
