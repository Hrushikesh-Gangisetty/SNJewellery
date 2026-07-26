import { fixtureCatalogueSource } from "./fixture-source";
import type { CatalogueSource } from "./source";

/**
 * The active catalogue source.
 *
 * **M4.1 changes this one line** to the Supabase implementation, and
 * nothing else in the application changes. That is the whole point of the
 * boundary — see ADR-0009.
 *
 * Import from `@/lib/data`, never from `./fixture-source` directly, or
 * that swap stops being a one-line change.
 */
export const catalogue: CatalogueSource = fixtureCatalogueSource;

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
