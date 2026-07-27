import { CategoryChip } from "@/components/category/category-chip";
import { Hero } from "@/components/home/hero";
import { ProductGrid } from "@/components/product/product-grid";
import { ContactMethods } from "@/components/shop/contact-methods";
import { StoreInformation } from "@/components/shop/store-information";
import { ButtonLink } from "@/components/ui/button";
import { Container } from "@/components/ui/container";
import { SectionHeading } from "@/components/ui/section-heading";
import { catalogue, type Product } from "@/lib/data";

/**
 * Home page.
 *
 * Renders every section the PRD's Home Page list names, in the order it
 * names them: hero banner, featured collections, newly added jewellery,
 * category shortcuts, store information, contact. The order is the PRD's
 * rather than a preference of mine — there was no reason to deviate.
 *
 * All six read from live data or from lib/config/site.ts. Nothing on this
 * page queries Supabase itself; every read goes through the data boundary
 * (CLAUDE.md §3.4).
 */

/**
 * The first piece with a photograph. The hero has nothing to show without
 * one, and a featured piece that has not been photographed yet is a real
 * state — so fall through featured, then newest, then give up cleanly.
 */
function firstWithPhotograph(
  ...lists: readonly (readonly Product[])[]
): Product | null {
  for (const list of lists) {
    const found = list.find((product) => product.images.length > 0);
    if (found) return found;
  }
  return null;
}

export default async function Home() {
  const [categories, featured, newest] = await Promise.all([
    catalogue.getVisibleCategories(),
    // One more than the row shows, because the hero consumes one.
    catalogue.getFeaturedProducts(9),
    catalogue.getNewestProducts(8),
  ]);

  const showcase = firstWithPhotograph(featured, newest);

  // The hero already shows this piece at full size; repeating it in the
  // row immediately below reads as a mistake rather than as emphasis.
  const featuredRow = featured
    .filter((product) => product.id !== showcase?.id)
    .slice(0, 8);

  return (
    <main id="main" className="flex-1">
      <Hero showcase={showcase} />

      {/* Omitted entirely when the hero was the only featured piece: an
          empty state under "Featured collection" would be telling the
          customer nothing is featured directly beneath something that is. */}
      {featuredRow.length > 0 ? (
        <Container as="section" className="py-12">
          <SectionHeading
            eyebrow="Selected pieces"
            title="Featured collection"
            action={
              <ButtonLink href="/catalogue" variant="ghost">
                View all
              </ButtonLink>
            }
          />
          <div className="mt-8">
            {/* No priority images here — the hero's is the only one above
                the fold, and competing priorities slow it down. */}
            <ProductGrid products={featuredRow} />
          </div>
        </Container>
      ) : null}

      <Container as="section" className="py-12">
        <SectionHeading eyebrow="Just added" title="New arrivals" />
        <div className="mt-8">
          <ProductGrid products={newest} />
        </div>
      </Container>

      <Container as="section" className="py-12">
        <SectionHeading eyebrow="Browse by" title="Categories" />
        <div className="mt-6 flex flex-wrap gap-2">
          {categories.map((category) => (
            <CategoryChip key={category.id} category={category} />
          ))}
        </div>
      </Container>

      {/* Store information and contact are two PRD sections, paired here
          because they answer one question — how do I reach you — and
          separating them across the page would make a customer hunt. */}
      <Container
        as="section"
        className="border-border grid gap-12 border-t py-16 md:grid-cols-2"
      >
        <div>
          <SectionHeading eyebrow="Come and see" title="Visit the showroom" />
          <StoreInformation className="mt-6" />
        </div>

        <div>
          <SectionHeading eyebrow="Questions" title="Get in touch" />
          <p className="text-body-m text-text-secondary mt-4 max-w-prose">
            Ask about a piece, check what is in store, or arrange a time to
            visit.
          </p>
          <ContactMethods className="mt-6" />
        </div>
      </Container>
    </main>
  );
}
