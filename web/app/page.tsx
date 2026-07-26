import { Container } from "@/components/ui/container";
import { SectionHeading } from "@/components/ui/section-heading";
import { CategoryChip } from "@/components/category/category-chip";
import { ProductGrid } from "@/components/product/product-grid";
import { ButtonLink } from "@/components/ui/button";
import { catalogue } from "@/lib/data";
import { site } from "@/lib/config/site";
import { whatsAppHref } from "@/lib/config/site";

/**
 * Interim home page.
 *
 * The real one is M4.2 — hero, store information, and a proper contact
 * section, reading from Supabase rather than fixtures. This version
 * exercises the shell, the primitives, and the data boundary against
 * fixture data so the foundation is provably working, and it is already
 * shaped so M4.2 is a content change rather than a rewrite.
 */
export default async function Home() {
  const [categories, featured, newest] = await Promise.all([
    catalogue.getVisibleCategories(),
    catalogue.getFeaturedProducts(4),
    catalogue.getNewestProducts(8),
  ]);

  return (
    <main id="main" className="flex-1">
      {/* Hero. Deliberately typographic — the real one gets photography
          in M4.2, but it must not animate in: motion.md §4 forbids
          above-the-fold entrance animation because it delays first sight
          of the merchandise and harms LCP. */}
      <Container className="py-16 md:py-24">
        <h1 className="font-display text-display-xl text-text-primary max-w-3xl">
          {site.name}
        </h1>
        <p className="text-body-l text-text-secondary mt-6 max-w-prose">
          {site.description}
        </p>
        <div className="mt-8 flex flex-wrap gap-3">
          <ButtonLink href="/catalogue">Browse the catalogue</ButtonLink>
          <ButtonLink href={whatsAppHref()} variant="secondary">
            Ask on WhatsApp
          </ButtonLink>
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
          {/* First row is above the fold on desktop, so those images get
              priority — M12.1 audits this. */}
          <ProductGrid products={featured} priorityCount={4} />
        </div>
      </Container>

      <Container as="section" className="py-12">
        <SectionHeading eyebrow="Just added" title="New arrivals" />
        <div className="mt-8">
          <ProductGrid products={newest} />
        </div>
      </Container>
    </main>
  );
}
