import { cn } from "@/lib/cn";
import type { Product } from "@/lib/data/types";

/**
 * Product specifications — purity, weight, colours.
 *
 * A description list, because that is what this is: paired terms and
 * values, which lets a screen reader announce "Purity, 22K Gold" rather
 * than reading two unrelated strings.
 *
 * **Missing optional fields are omitted, never shown as a dash.** Not
 * every piece is sold by weight and not every piece comes in a choice of
 * colours; printing "Weight —" tells a customer nothing and implies the
 * shop lost the number. See components.md, `SpecList` missing-optional.
 *
 * Values use `text-spec` — the tabular-figure style from typography.md,
 * so weights line up digit for digit down the column.
 */
export function SpecList({
  product,
  className,
}: {
  product: Product;
  className?: string;
}) {
  const specs: { term: string; value: string }[] = [
    { term: "Category", value: product.category.name },
  ];

  // `label` here ("22K Gold"), not the card's short `code` — the product
  // page has room for the full form and no adjacent category to repeat.
  if (product.purity) {
    specs.push({ term: "Purity", value: product.purity.label });
  }

  if (product.weightGrams !== null) {
    specs.push({
      term: "Weight",
      value: `${product.weightGrams.toFixed(2)} g`,
    });
  }

  if (product.colours.length > 0) {
    specs.push({
      term: product.colours.length === 1 ? "Colour" : "Colours",
      value: product.colours.join(", "),
    });
  }

  return (
    <dl
      className={cn("border-border divide-border divide-y border-y", className)}
    >
      {specs.map(({ term, value }) => (
        <div key={term} className="flex gap-4 py-3">
          <dt className="text-body-s text-text-secondary w-28 shrink-0">
            {term}
          </dt>
          <dd className="text-spec text-text-primary">{value}</dd>
        </div>
      ))}
    </dl>
  );
}
