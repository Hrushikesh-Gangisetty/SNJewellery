import {
  fixtureCategories,
  fixtureProducts,
  fixturePurities,
} from "./fixtures";
import { DEFAULT_PAGE_SIZE, type CatalogueSource } from "./source";
import type { Category, Page, PageRequest, Product, Purity } from "./types";

/**
 * Fixture implementation of CatalogueSource.
 *
 * It applies the visibility rules — no archived products, no products in
 * hidden categories, sold products retained — **manually**, because in
 * M4.1 the real implementation gets them from RLS instead. Both must
 * behave identically, so this file is where the contract in source.ts is
 * made concrete and testable.
 *
 * Kept after M4.1 for tests and empty-state work, per ADR-0009.
 */

/** The single gate every read passes through. */
function isPublic(product: Product): boolean {
  // `sold` is deliberately NOT a filter — sold pieces stay visible.
  return !product.archived && product.category.isVisible;
}

const publicProducts = (): Product[] =>
  fixtureProducts
    .filter(isPublic)
    // Newest first, deterministic tiebreak on id so pagination is stable.
    .sort(
      (a, b) =>
        Date.parse(b.createdAt) - Date.parse(a.createdAt) ||
        a.id.localeCompare(b.id),
    );

/**
 * Keyset pagination over a sorted list. The cursor is the last id seen,
 * which mirrors how the real implementation will page on
 * `(created_at, id)`.
 */
function paginate<T extends { id: string }>(
  all: readonly T[],
  page?: PageRequest,
): Page<T> {
  const limit = page?.limit ?? DEFAULT_PAGE_SIZE;
  const cursor = page?.cursor ?? null;

  const start = cursor ? all.findIndex((i) => i.id === cursor) + 1 : 0;
  // An unknown cursor yields an empty page rather than silently
  // restarting from the beginning, which would loop forever.
  const items = cursor && start === 0 ? [] : all.slice(start, start + limit);
  const last = items.at(-1);
  const hasMore = start + items.length < all.length;

  return {
    items,
    nextCursor: hasMore && last ? last.id : null,
    hasMore,
  };
}

/** Simulates latency so loading states are visible in development. */
const delayMs = Number(process.env.NEXT_PUBLIC_FIXTURE_DELAY_MS ?? 0);
const settle = async <T>(value: T): Promise<T> => {
  if (delayMs > 0) {
    await new Promise((resolve) => setTimeout(resolve, delayMs));
  }
  return value;
};

export const fixtureCatalogueSource: CatalogueSource = {
  getVisibleCategories(): Promise<readonly Category[]> {
    return settle(
      fixtureCategories
        .filter((c) => c.isVisible)
        .sort((a, b) => a.displayOrder - b.displayOrder),
    );
  },

  getCategoryBySlug(slug: string): Promise<Category | null> {
    const found = fixtureCategories.find((c) => c.slug === slug && c.isVisible);
    return settle(found ?? null);
  },

  getPurities(): Promise<readonly Purity[]> {
    return settle(
      [...fixturePurities].sort((a, b) => a.displayOrder - b.displayOrder),
    );
  },

  getFeaturedProducts(limit = 8): Promise<readonly Product[]> {
    return settle(
      publicProducts()
        .filter((p) => p.featured)
        .slice(0, limit),
    );
  },

  getNewestProducts(limit = 8): Promise<readonly Product[]> {
    return settle(publicProducts().slice(0, limit));
  },

  async getProductsByCategory(
    categorySlug: string,
    page?: PageRequest,
  ): Promise<Page<Product>> {
    const category = await this.getCategoryBySlug(categorySlug);
    // Unknown or hidden category yields an empty page, not every product.
    if (!category) {
      return { items: [], nextCursor: null, hasMore: false };
    }
    const inCategory = publicProducts().filter(
      (p) => p.category.id === category.id,
    );
    return settle(paginate(inCategory, page));
  },

  getAllProducts(page?: PageRequest): Promise<Page<Product>> {
    return settle(paginate(publicProducts(), page));
  },

  getProductBySlug(slug: string): Promise<Product | null> {
    const found = publicProducts().find((p) => p.slug === slug);
    return settle(found ?? null);
  },

  getRelatedProducts(product: Product, limit = 4): Promise<readonly Product[]> {
    return settle(
      publicProducts()
        .filter(
          (p) => p.category.id === product.category.id && p.id !== product.id,
        )
        .slice(0, limit),
    );
  },
};
