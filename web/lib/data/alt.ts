import type { Product } from "./types";

/**
 * Alt text, derived from product data — never stored, never a filename,
 * never empty. Required by docs/design/accessibility.md §3 and audited in
 * M11.7.
 *
 * Deriving rather than storing means it cannot rot: it is impossible to
 * have a product whose alt text is missing.
 *
 * Pattern: "{name} — {purity} {category}"
 *   "Temple Design Necklace — 22K Gold Necklaces"
 *   "Silver Anklet Pair — Silver Anklets"
 *   "Kids Bangle Set — Kids Collection"   (no purity recorded)
 */
export function productImageAlt(product: Product, index = 0): string {
  const parts = [product.purity?.label, product.category.name].filter(
    (p): p is string => Boolean(p),
  );

  const descriptor = parts.length > 0 ? ` — ${parts.join(" ")}` : "";

  // Additional views are numbered so a screen reader can distinguish
  // them; the first image needs no qualifier.
  const view = index > 0 ? `, view ${index + 1}` : "";

  return `${product.name}${descriptor}${view}`;
}
