import { fixtureCatalogueSource } from "./fixture-source";
import { supabaseCatalogueSource } from "./supabase-source";
import type { CatalogueSource } from "./source";

/**
 * The active catalogue source.
 *
 * This is the one line ADR-0009 was built around, and it has now been
 * flipped from fixtures to Supabase. No page and no component changed —
 * which is the whole point of the boundary.
 *
 * Import from `@/lib/data`, never from a source module directly, or the
 * next swap stops being a one-line change.
 *
 * **One exception, for client components.** Importing anything from this
 * barrel pulls in the active source, and therefore supabase-js — roughly
 * 58 kB of client bundle for code the browser never runs. A client
 * component that needs only a pure helper or a type imports it from its
 * own module (`./alt`, `./types`) instead. Neither is a source, so the
 * one-line-swap property is untouched. Nothing client-side may import
 * `catalogue`; it reads through the server actions in ./actions.ts.
 *
 * `NEXT_PUBLIC_USE_FIXTURES=1` forces fixtures back on. Useful for
 * working on empty and error states, or developing offline, without
 * touching this file. It reads `process.env` directly rather than going
 * through lib/config/env because it is a development switch, not
 * configuration the deployed site depends on.
 */
const useFixtures = process.env.NEXT_PUBLIC_USE_FIXTURES === "1";

export const catalogue: CatalogueSource = useFixtures
  ? fixtureCatalogueSource
  : supabaseCatalogueSource;

/** Exported for tests, which run the contract against both sources. */
export { fixtureCatalogueSource } from "./fixture-source";
export { supabaseCatalogueSource } from "./supabase-source";

export { DEFAULT_PAGE_SIZE } from "./source";
export type { CatalogueSource } from "./source";
export { productImageAlt } from "./alt";
export type {
  Category,
  ImageAspect,
  Page,
  PageRequest,
  Product,
  ProductImage,
  Purity,
  Timestamp,
} from "./types";
