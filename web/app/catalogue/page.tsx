import type { Metadata } from "next";
import { CategoryChip } from "@/components/category/category-chip";
import { ProductCollection } from "@/components/product/product-collection";
import { WhatsAppButton } from "@/components/shop/conversion";
import { Container } from "@/components/ui/container";
import { EmptyState } from "@/components/ui/empty-state";
import { SectionHeading } from "@/components/ui/section-heading";
import { getAllProducts, getVisibleCategories } from "@/lib/data/cache";

/**
 * ISR. The cached reads in lib/data/cache.ts carry the tags M9 will
 * invalidate; this interval is the fallback ADR-0006 requires in case a
 * webhook is ever dropped. See docs/architecture/rendering.md.
 */
export const revalidate = 600;

/**
 * The whole catalogue.
 *
 * The category chips are navigation, not a filter panel — pressing one is
 * a route change to /category/[slug]. The real filter controls (purity,
 * featured, latest) are M10, and they reuse this same chip.
 */
export const metadata: Metadata = {
  title: "All jewellery",
  description:
    "Browse the full collection — gold, silver, and diamond jewellery. Visit our showroom in Markapur, or ask us about a piece.",
};

export default async function CataloguePage() {
  const [categories, firstPage] = await Promise.all([
    getVisibleCategories(),
    getAllProducts(),
  ]);

  return (
    <main id="main" className="flex-1">
      <Container as="section" className="py-10 md:py-14">
        <SectionHeading
          as="h1"
          eyebrow="The collection"
          title="All jewellery"
        />

        <nav aria-label="Categories" className="mt-6 flex flex-wrap gap-2">
          {categories.map((category) => (
            <CategoryChip key={category.id} category={category} />
          ))}
        </nav>

        <div className="mt-10">
          <ProductCollection
            initial={firstPage}
            emptyState={
              <EmptyState
                title="The catalogue is not online yet"
                description="Pieces are added as they are photographed. Ask us what is in store today — we will send you photographs."
                action={<WhatsAppButton>Ask what is in store</WhatsAppButton>}
              />
            }
            // The grid is the page here, so its first row is the LCP
            // candidate. Four covers the widest first row (layout.md §5);
            // narrower breakpoints preload one row's worth of the next.
            priorityCount={4}
          />
        </div>
      </Container>
    </main>
  );
}
