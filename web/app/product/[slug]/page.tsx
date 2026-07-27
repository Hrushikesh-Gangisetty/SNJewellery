import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { ImageGallery } from "@/components/product/image-gallery";
import { RelatedProducts } from "@/components/product/related-products";
import { SpecList } from "@/components/product/spec-list";
import { ConversionActions } from "@/components/shop/conversion";
import { FeaturedBadge, SoldBadge } from "@/components/ui/badge";
import { Container } from "@/components/ui/container";
import { catalogue } from "@/lib/data";

type Params = { slug: string };

/**
 * Product detail.
 *
 * Gallery, name, category, description, colours, related products, and
 * the three actions that are the point of the whole site.
 *
 * **Purity and weight are not shown**, per the owner's decision of
 * 2026-07-27 — today's gold and silver rates on the home page replace
 * per-piece metal detail. The PRD's Product Details list still names
 * both; this supersedes it. Both columns remain in the database.
 *
 * Optional fields are absent, never blank: SpecList renders nothing at
 * all for a piece with no colours, and the description section does not
 * render for a piece that has none. A page of empty labels would read as
 * a broken record rather than a piece the owner has not finished
 * describing.
 *
 * No `generateStaticParams`. The catalogue grows continuously and M4.7 is
 * where per-route rendering and revalidation tags are decided; pinning a
 * build-time product list here would pre-empt that with the answer that
 * ages worst.
 */
export async function generateMetadata({
  params,
}: {
  params: Promise<Params>;
}): Promise<Metadata> {
  const { slug } = await params;
  const product = await catalogue.getProductBySlug(slug);
  if (!product) return {};

  return {
    title: product.name,
    // Falls back to the category rather than to a generic sentence: a
    // written description beats "Necklaces", and "Necklaces" beats
    // boilerplate that says nothing about this piece at all.
    description:
      product.summary ?? product.description ?? product.category.name,
  };
}

export default async function ProductPage({
  params,
}: {
  params: Promise<Params>;
}) {
  const { slug } = await params;

  // null covers unknown, archived, and hidden-category alike — the data
  // layer makes them indistinguishable on purpose (CatalogueSource rule 4).
  const product = await catalogue.getProductBySlug(slug);
  if (!product) notFound();

  const related = await catalogue.getRelatedProducts(product);

  return (
    <main id="main" className="flex-1">
      <Container as="section" className="py-8 md:py-12">
        <div className="grid gap-10 lg:grid-cols-2 lg:gap-14">
          <ImageGallery product={product} />

          <div className="flex flex-col">
            <Link
              href={`/category/${product.category.slug}`}
              className="text-label text-accent-text hover:text-text-primary inline-flex self-start"
            >
              {product.category.name}
            </Link>

            {/* display-l, not heading-l: the piece's name is the one
                thing this page is about (typography.md §1). */}
            <h1 className="font-display text-display-l text-text-primary mt-3">
              {product.name}
            </h1>

            {product.sold || product.featured ? (
              <div className="mt-4 flex flex-wrap gap-2">
                {/* Sold pieces stay visible with a badge — the owner's
                    decision, and they are evidence of what the shop makes. */}
                {product.sold ? <SoldBadge /> : null}
                {product.featured ? <FeaturedBadge /> : null}
              </div>
            ) : null}

            {product.summary ? (
              <p className="text-body-l text-text-secondary mt-6 max-w-prose">
                {product.summary}
              </p>
            ) : null}

            <SpecList product={product} className="mt-8" />

            {/* Only when it says something the summary did not. */}
            {product.description && product.description !== product.summary ? (
              <div className="mt-8">
                <h2 className="text-label text-text-muted">About this piece</h2>
                <p className="text-body-m text-text-secondary mt-3 max-w-prose whitespace-pre-line">
                  {product.description}
                </p>
              </div>
            ) : null}

            {/* The point of the page. Kept with the specifications rather
                than parked at the bottom, so a customer who has decided
                does not have to scroll to act (ux.md §2). */}
            <ConversionActions product={product} className="mt-10" />

            <p className="text-caption text-text-muted mt-4">
              Prices are not shown online. Ask us and we will confirm
              today&rsquo;s rate.
            </p>
          </div>
        </div>
      </Container>

      <RelatedProducts products={related} />
    </main>
  );
}
