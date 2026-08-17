import { unstable_cache } from "next/cache";
import { catalogue } from "./index";
import type { Category, MetalRate, Page, Product } from "./types";

/**
 * The cached read layer. Implements [ADR-0006](../../../docs/adr/0006-cache-revalidation-strategy.md).
 *
 * Every page reads through here rather than through `catalogue` directly,
 * so that each cacheable route carries a tag. ADR-0006's consequences
 * section is explicit that retrofitting tags across a built site costs
 * more than placing them as pages are written.
 *
 * These tags are invalidated by `app/api/revalidate/route.ts`, which a
 * Supabase database webhook calls on every mutation. The mapping from a
 * changed row to the tags it clears lives in ./revalidate.ts.
 *
 * ── Why unstable_cache and not fetch tags ────────────────────────────
 * Next's tagging is built on `fetch`, and these reads go through
 * supabase-js, which uses its own client. `unstable_cache` is the
 * supported way to attach a tag to anything that is not a `fetch` — it is
 * "unstable" in name only in 15.x, and it is what the App Router
 * documents for exactly this case.
 *
 * ── The interval is the fallback, not the mechanism ──────────────────
 * ADR-0006 chose an event-driven webhook as the primary signal precisely
 * so freshness costs nothing when nothing changes. This interval exists
 * only to bound the damage when a webhook is dropped, which is a failure
 * mode a silent one-way HTTP call genuinely has. Ten minutes against a
 * catalogue that changes a few times a day is a handful of wasted
 * rebuilds; the PRD's one-minute budget is met by the webhook.
 *
 * M9.5 tests this by disabling the webhook deliberately.
 */
const REVALIDATE_SECONDS = 600;

/**
 * The tag vocabulary. `tagsFor` in ./revalidate.ts maps each mutation to
 * the tags it invalidates, and ADR-0006 warns that a mutation whose tag
 * is missing from that map is a silently stale page — so the names live
 * here, in one place, rather than as string literals spread across
 * routes.
 *
 * Lists carry `products` because any product change can reorder or
 * repopulate them. Detail pages carry their own slug, so editing one
 * piece leaves every other product page cached — which is ADR-0006's
 * stated requirement and M9.3's acceptance criterion.
 */
export const TAGS = {
  /** Every product list: home rows, catalogue, category pages. */
  products: "products",
  /** The category list itself — the header, footer and chips. */
  categories: "categories",
  /** Today's gold and silver rates. */
  rates: "rates",
  /** One product's detail page. */
  product: (slug: string) => `product:${slug}`,
  /** One category's listing. */
  category: (slug: string) => `category:${slug}`,
} as const;

const shared = { revalidate: REVALIDATE_SECONDS } as const;

export const getVisibleCategories = unstable_cache(
  () => catalogue.getVisibleCategories(),
  ["visible-categories"],
  { ...shared, tags: [TAGS.categories] },
);

export const getFeaturedProducts = unstable_cache(
  (limit?: number) => catalogue.getFeaturedProducts(limit),
  ["featured-products"],
  { ...shared, tags: [TAGS.products] },
);

export const getNewestProducts = unstable_cache(
  (limit?: number) => catalogue.getNewestProducts(limit),
  ["newest-products"],
  { ...shared, tags: [TAGS.products] },
);

export const getMetalRates = unstable_cache(
  () => catalogue.getMetalRates(),
  ["metal-rates"],
  { ...shared, tags: [TAGS.rates] },
);

export const getAllProducts = unstable_cache(
  (cursor?: string | null) => catalogue.getAllProducts({ cursor }),
  ["all-products"],
  { ...shared, tags: [TAGS.products] },
);

/**
 * Slug-scoped entries need the slug in both the cache key and the tag, so
 * each is built per call rather than once at module load. The wrapper is
 * cheap; the cache lookup inside it is what matters.
 */
export function getCategoryBySlug(slug: string): Promise<Category | null> {
  return unstable_cache(
    () => catalogue.getCategoryBySlug(slug),
    ["category-by-slug", slug],
    { ...shared, tags: [TAGS.categories, TAGS.category(slug)] },
  )();
}

export function getProductsByCategory(
  slug: string,
  cursor?: string | null,
): Promise<Page<Product>> {
  return unstable_cache(
    () => catalogue.getProductsByCategory(slug, { cursor }),
    ["products-by-category", slug, cursor ?? ""],
    { ...shared, tags: [TAGS.products, TAGS.category(slug)] },
  )();
}

export function getProductBySlug(slug: string): Promise<Product | null> {
  return unstable_cache(
    () => catalogue.getProductBySlug(slug),
    ["product-by-slug", slug],
    { ...shared, tags: [TAGS.product(slug)] },
  )();
}

/**
 * Every product slug, for `generateStaticParams` and, later, the sitemap.
 *
 * Pages through the catalogue rather than asking for one enormous page:
 * the data layer's contract is keyset pagination and there is no
 * "give me everything" method, deliberately. At the real catalogue size —
 * ~500 growing to ~1,000 — that is twenty to forty queries once per
 * build, which is a fair price for shipping every product page as static
 * HTML.
 *
 * The loop is bounded. A cursor that stopped advancing would otherwise
 * spin forever at build time, which is a hang rather than an error and so
 * much harder to diagnose.
 */
const MAX_PAGES = 500;

export const getAllProductSlugs = unstable_cache(
  async (): Promise<string[]> => {
    const slugs: string[] = [];
    let cursor: string | null = null;

    for (let page = 0; page < MAX_PAGES; page++) {
      const result: Page<Product> = await catalogue.getAllProducts({ cursor });
      slugs.push(...result.items.map((product) => product.slug));
      if (!result.nextCursor) return slugs;
      cursor = result.nextCursor;
    }

    throw new Error(
      `getAllProductSlugs exceeded ${MAX_PAGES} pages — the cursor is not advancing`,
    );
  },
  ["all-product-slugs"],
  { ...shared, tags: [TAGS.products] },
);

/**
 * Tagged with the SOURCE product's slug, not the related ones'.
 *
 * This is the case that is easy to get backwards. What invalidates this
 * list is a change to the category it draws from — but tagging it with
 * every sibling's slug would mean editing any one piece purges its
 * siblings' related lists too. `products` already covers that, and the
 * source slug keeps the entry addressable when the product itself moves
 * category.
 */
export function getRelatedProducts(
  product: Product,
  limit?: number,
): Promise<readonly Product[]> {
  return unstable_cache(
    () => catalogue.getRelatedProducts(product, limit),
    ["related-products", product.id],
    {
      ...shared,
      tags: [TAGS.products, TAGS.product(product.slug)],
    },
  )();
}

export type { Category, MetalRate, Page, Product };
