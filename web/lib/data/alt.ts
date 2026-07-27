import type { Product } from "./types";

/**
 * Alt text, derived from product data — never stored, never a filename,
 * never empty. Required by docs/design/accessibility.md §3 and audited in
 * M11.7.
 *
 * Deriving rather than storing means it cannot rot: it is impossible to
 * have a product whose alt text is missing.
 *
 * Pattern: "{name} — {category}".
 *
 * **Purity used to appear here and deliberately no longer does.** The
 * owner's decision (2026-07-27) is that per-piece purity and weight are
 * not shown to customers; today's gold and silver rates are shown
 * instead. Alt text follows that decision rather than being exempted from
 * it — a screen-reader user must not be told something a sighted user is
 * not (accessibility.md §1). The fields are still in the database, so
 * restoring this is a change here and nowhere else.
 */
export function productImageAlt(product: Product, index = 0): string {
  // Additional views are numbered so a screen reader can distinguish
  // them; the first image needs no qualifier.
  const view = index > 0 ? `, view ${index + 1}` : "";

  return `${product.name} — ${product.category.name}${view}`;
}
