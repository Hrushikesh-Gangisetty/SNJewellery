import type { Product } from "./types";

/**
 * Alt text, derived from product data — never stored, never a filename,
 * never empty. Required by docs/design/accessibility.md §3 and audited in
 * M11.7.
 *
 * Deriving rather than storing means it cannot rot: it is impossible to
 * have a product whose alt text is missing.
 *
 * Pattern: "{name} — {purity} {category}", with repeated words removed.
 *
 * The dedupe is not cosmetic. Purity and category names overlap in this
 * catalogue, so a naive join produces text a screen reader actually reads
 * aloud badly:
 *
 *   "18K Gold" + "Gold Rings"        -> "18K Gold Gold Rings"
 *   "Silver"   + "Silver Jewellery"  -> "Silver Silver Jewellery"
 *
 * Using the short purity code and dropping duplicate words fixes both:
 *
 *   "18K" + "Gold Rings"       -> "18K Gold Rings"
 *   "Silver" + "Silver Jewellery" -> "Silver Jewellery"
 *   "22K" + "Bridal Jewellery" -> "22K Bridal Jewellery"
 */
export function productImageAlt(product: Product, index = 0): string {
  // `code` ("22K", "Silver") rather than `label` ("22K Gold"), because the
  // category name usually already supplies the metal.
  const words = [product.purity?.code, product.category.name]
    .filter((p): p is string => Boolean(p))
    .join(" ")
    .split(/\s+/);

  const seen = new Set<string>();
  const descriptorWords = words.filter((word) => {
    const key = word.toLowerCase();
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });

  const descriptor =
    descriptorWords.length > 0 ? ` — ${descriptorWords.join(" ")}` : "";

  // Additional views are numbered so a screen reader can distinguish
  // them; the first image needs no qualifier.
  const view = index > 0 ? `, view ${index + 1}` : "";

  return `${product.name}${descriptor}${view}`;
}
