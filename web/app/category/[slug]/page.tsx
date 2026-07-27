import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { CategoryChip } from "@/components/category/category-chip";
import { ProductCollection } from "@/components/product/product-collection";
import { ButtonLink } from "@/components/ui/button";
import { Container } from "@/components/ui/container";
import { EmptyState } from "@/components/ui/empty-state";
import { SectionHeading } from "@/components/ui/section-heading";
import { site } from "@/lib/config/site";
import {
  getCategoryBySlug,
  getProductsByCategory,
  getVisibleCategories,
} from "@/lib/data/cache";

/**
 * ISR. The cached reads in lib/data/cache.ts carry the tags M9 will
 * invalidate; this interval is the fallback ADR-0006 requires in case a
 * webhook is ever dropped. See docs/architecture/rendering.md.
 */
export const revalidate = 600;

type Params = { slug: string };

/**
 * One category. Eleven of these exist at launch, one per row in
 * `categories`, and the list is data — adding a twelfth is an insert, not
 * a deploy.
 *
 * Pre-rendering the known slugs keeps them off the runtime path; an
 * unknown slug still resolves on demand and lands on `notFound`. Which
 * routes are static and how they revalidate is settled properly in M4.7.
 */
export async function generateStaticParams(): Promise<Params[]> {
  const categories = await getVisibleCategories();
  return categories.map((category) => ({ slug: category.slug }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<Params>;
}): Promise<Metadata> {
  const { slug } = await params;
  const category = await getCategoryBySlug(slug);

  // Not a 404 here — the page itself raises that. Returning a title for a
  // page that will not render would be worse than returning nothing.
  if (!category) return {};

  return {
    title: category.name,
    description: `${category.name} at ${site.name}, ${site.address.city}. Visit our showroom, or ask us about a piece.`,
  };
}

export default async function CategoryPage({
  params,
}: {
  params: Promise<Params>;
}) {
  const { slug } = await params;

  // A hidden category is indistinguishable from an unknown one, by
  // design — RLS returns null for both. See CatalogueSource rule 2.
  const category = await getCategoryBySlug(slug);
  if (!category) notFound();

  const [categories, firstPage] = await Promise.all([
    getVisibleCategories(),
    getProductsByCategory(slug),
  ]);

  return (
    <main id="main" className="flex-1">
      <Container as="section" className="py-10 md:py-14">
        <SectionHeading as="h1" eyebrow="Category" title={category.name} />

        <nav aria-label="Categories" className="mt-6 flex flex-wrap gap-2">
          {categories.map((other) => (
            <CategoryChip
              key={other.id}
              category={other}
              active={other.id === category.id}
            />
          ))}
        </nav>

        <div className="mt-10">
          {/* Keyed so navigating between two categories does not reuse the
              previous one's accumulated pages. */}
          <ProductCollection
            key={category.slug}
            initial={firstPage}
            categorySlug={category.slug}
            priorityCount={4}
            emptyState={
              <EmptyState
                title={`No ${category.name.toLowerCase()} to show yet`}
                description="New pieces are added regularly. Browse the rest of the collection, or ask us what is in store."
                action={
                  <ButtonLink href="/catalogue" variant="secondary">
                    Browse all jewellery
                  </ButtonLink>
                }
              />
            }
          />
        </div>
      </Container>
    </main>
  );
}
