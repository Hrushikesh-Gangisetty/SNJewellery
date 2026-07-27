/**
 * DRAFT SCHEMA CONTRACT
 *
 * These types are hand-written deliberately, before the database exists,
 * per ADR-0009. Building real UI against them exercises the shape while
 * changing it is still free — then M3.2–M3.5 writes migrations to match,
 * and M3.10 replaces this file with generated types, reconciling every
 * difference explicitly.
 *
 * So: if something here turns out to be wrong or missing, that is this
 * file doing its job. Record the finding rather than quietly patching
 * around it.
 *
 * Naming: `snake_case` in the database becomes `camelCase` here, and the
 * mapping happens in the data source, not in components.
 */

/** ISO-8601 timestamp string. */
export type Timestamp = string;

/**
 * Gold and silver purity. A lookup table, not an enum or a CHECK
 * constraint — the owner asked for new purities to be addable without a
 * schema change, and a row is cheaper than a migration.
 *
 * At launch: 22K Gold, 18K Gold, Silver.
 *
 * **Recorded, not displayed.** Since 2026-07-27 the website shows neither
 * purity nor weight on any surface; today's gold and silver rates
 * (`MetalRate`) took their place. The admin app still captures both, so
 * they stay on the contract.
 */
export type Purity = {
  readonly id: string;
  /** Short form shown on cards: "22K", "Silver". */
  readonly code: string;
  /** Full form shown on the product page: "22K Gold". */
  readonly label: string;
  readonly displayOrder: number;
};

/** The two metals whose daily rate the shop publishes. */
export type Metal = "gold" | "silver";

/**
 * Today's rate for one metal, as displayed on the home page.
 *
 * Added 2026-07-27, replacing per-piece purity and weight on the
 * customer-facing site. The owner updates both rates each morning from
 * the Android app.
 *
 * `ratePerGram` is null until the rate has been published for the first
 * time, and `updatedAt` is null with it. A customer must never be shown a
 * placeholder rate, so both consumers hide rather than substitute — the
 * same rule the rest of the site's configuration follows (ADR-0010).
 */
export type MetalRate = {
  readonly metal: Metal;
  /** Rupees per gram. Null means not published yet. */
  readonly ratePerGram: number | null;
  /** When the owner last set it. Null while unpublished. */
  readonly updatedAt: Timestamp | null;
};

export type Category = {
  readonly id: string;
  /** URL segment. Unique. */
  readonly slug: string;
  readonly name: string;
  readonly displayOrder: number;
  /**
   * Hidden categories and all their products are invisible to customers.
   * Enforced by RLS in M3.7, not by filtering in components.
   */
  readonly isVisible: boolean;
};

/**
 * Which fixed ratio this image was shot for. Long pieces — necklaces,
 * bridal sets — are ruined by a square crop, so the owner picks portrait
 * for those at upload. See docs/design/responsive.md §2.
 */
export type ImageAspect = "product" | "product-portrait";

export type ProductImage = {
  readonly id: string;
  /** Public CDN URL of the optimised rendition. */
  readonly url: string;
  /**
   * Storage path, kept so the thumbnail and mobile renditions can be
   * derived. Never constructed ad hoc — see ADR-0005.
   */
  readonly storagePath: string;
  readonly displayOrder: number;
  readonly aspect: ImageAspect;
  /**
   * Alt text is DERIVED from product data, never stored, so it can never
   * be empty or a filename. See `productImageAlt` in ./alt.ts.
   */
};

export type Product = {
  readonly id: string;
  readonly slug: string;
  readonly name: string;
  /** Short description for cards. */
  readonly summary: string | null;
  /** Full description for the product page. */
  readonly description: string | null;

  readonly category: Category;
  readonly purity: Purity | null;

  /**
   * Weight in grams. Optional because not every piece is sold by weight.
   *
   * The owner originally confirmed this was shown to customers and
   * reversed that on 2026-07-27 — see the note on `Purity`. Still
   * captured by the admin app, never rendered by the website.
   */
  readonly weightGrams: number | null;

  /** Optional available colours, e.g. ["Yellow", "Rose"]. */
  readonly colours: readonly string[];

  /** Free-text tags, searchable in M10. */
  readonly tags: readonly string[];

  /** Promoted on the home page. */
  readonly featured: boolean;

  /**
   * Sold pieces STAY VISIBLE with a "Sold" badge — the owner's decision.
   * They are portfolio evidence of what the shop makes.
   */
  readonly sold: boolean;

  /**
   * Archived pieces are hidden from customers but remain in the admin
   * app. Distinct from `sold`, and distinct from deletion.
   *
   * No customer-facing query ever returns an archived product, so this
   * field should always be `false` in anything the website receives. It
   * is present on the type because the admin app shares the contract.
   */
  readonly archived: boolean;

  readonly images: readonly ProductImage[];

  readonly createdAt: Timestamp;
  readonly updatedAt: Timestamp;
};

/**
 * Keyset pagination. Cursor-based rather than offset so deep pages stay
 * fast — see DEVELOPMENT_PLAN M10.6. Cheap to do now, awkward to retrofit.
 */
export type Page<T> = {
  readonly items: readonly T[];
  /** Pass to the next call. `null` means this is the last page. */
  readonly nextCursor: string | null;
  readonly hasMore: boolean;
};

export type PageRequest = {
  readonly limit?: number;
  readonly cursor?: string | null;
};
