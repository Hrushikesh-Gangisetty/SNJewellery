import { ProductGrid } from "./product-grid";
import { SectionHeading } from "@/components/ui/section-heading";
import { Container } from "@/components/ui/container";
import type { Product } from "@/lib/data/types";

/**
 * Related products. See components.md `RelatedProducts` — empty hides.
 *
 * "Hides" is the whole behaviour worth stating: a "You may also like"
 * heading over nothing is worse than no section, and the data layer
 * returns fewer results rather than padding with unrelated pieces
 * (CatalogueSource.getRelatedProducts), so an empty result is normal for
 * a thinly stocked category rather than a fault.
 *
 * Exclusion of the product itself, archived pieces, and hidden categories
 * happens in the data layer and is covered by the contract tests. Nothing
 * is filtered here.
 */
export function RelatedProducts({
  products,
}: {
  products: readonly Product[];
}) {
  if (products.length === 0) return null;

  return (
    <Container as="section" className="border-border border-t py-12">
      <SectionHeading eyebrow="More like this" title="You may also like" />
      <div className="mt-8">
        {/* Below the fold on every viewport, so no priority images. */}
        <ProductGrid products={products} />
      </div>
    </Container>
  );
}
