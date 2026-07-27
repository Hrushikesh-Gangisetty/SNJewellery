import { cn } from "@/lib/cn";
import type { Product } from "@/lib/data/types";

/**
 * Product specifications.
 *
 * A description list, because that is what this is: paired terms and
 * values, which lets a screen reader announce "Colours, Yellow, Rose"
 * rather than reading two unrelated strings.
 *
 * **Purity and weight used to be the bulk of this and deliberately are
 * not any more.** Per the owner's decision of 2026-07-27, per-piece metal
 * detail is not shown to customers; today's gold and silver rates are
 * shown once on the home page instead. Both columns remain in the
 * database, so putting a row back is an addition here and nowhere else.
 *
 * Category is not listed either — the product page already links to it
 * directly above the name, and repeating it two lines later reads as a
 * mistake.
 *
 * **Missing optional fields are omitted, never shown as a dash**, and
 * when nothing remains the whole list renders as nothing rather than an
 * empty bordered box. See components.md, `SpecList` missing-optional.
 *
 * Values use `text-spec` — the tabular-figure style from typography.md.
 */
export function SpecList({
  product,
  className,
}: {
  product: Product;
  className?: string;
}) {
  const specs: { term: string; value: string }[] = [];

  if (product.colours.length > 0) {
    specs.push({
      term: product.colours.length === 1 ? "Colour" : "Colours",
      value: product.colours.join(", "),
    });
  }

  if (specs.length === 0) return null;

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
