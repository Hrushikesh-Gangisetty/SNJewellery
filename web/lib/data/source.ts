import type { Category, Page, PageRequest, Product, Purity } from "./types";

/**
 * THE data-access boundary for the website.
 *
 * No page and no component queries Supabase directly. Everything reads
 * through this interface — which is what lets M2 build against fixtures
 * and M4.1 swap in the real implementation without touching a single
 * component. See ADR-0009.
 *
 * ── Contract every implementation must honour ────────────────────────
 *
 * 1. **Never return an archived product.** Not from any method, not in
 *    related products, not in search.
 * 2. **Never return a product whose category is hidden**, and never
 *    return a hidden category.
 * 3. **DO return sold products.** They stay visible with a badge — the
 *    owner's decision. Filtering them out is a bug, not caution.
 * 4. **Return `null`, not a throw, for a missing single item.** Callers
 *    turn that into a 404.
 * 5. **Ordering is deterministic.** Two calls with the same arguments
 *    return the same order, or pagination breaks.
 *
 * Rules 1–3 are enforced by RLS in the real implementation (M3.7), not by
 * filtering here. The fixture implementation applies them manually so the
 * two behave identically.
 */
export type CatalogueSource = {
  /** Visible categories, in `displayOrder`. */
  getVisibleCategories(): Promise<readonly Category[]>;

  /** `null` if the slug is unknown or the category is hidden. */
  getCategoryBySlug(slug: string): Promise<Category | null>;

  /** Purities in `displayOrder`, for filter controls and labels. */
  getPurities(): Promise<readonly Purity[]>;

  /** Featured products for the home page, newest first. */
  getFeaturedProducts(limit?: number): Promise<readonly Product[]>;

  /** Most recently added products, newest first. */
  getNewestProducts(limit?: number): Promise<readonly Product[]>;

  /** Paginated products within one category, newest first. */
  getProductsByCategory(
    categorySlug: string,
    page?: PageRequest,
  ): Promise<Page<Product>>;

  /** Paginated products across the whole catalogue, newest first. */
  getAllProducts(page?: PageRequest): Promise<Page<Product>>;

  /** `null` if the slug is unknown, archived, or in a hidden category. */
  getProductBySlug(slug: string): Promise<Product | null>;

  /**
   * Related products — same category, excluding the product itself.
   * Returns fewer than `limit`, or none, rather than padding with
   * unrelated items.
   */
  getRelatedProducts(
    product: Product,
    limit?: number,
  ): Promise<readonly Product[]>;
};

/** Default page size. */
export const DEFAULT_PAGE_SIZE = 24;
