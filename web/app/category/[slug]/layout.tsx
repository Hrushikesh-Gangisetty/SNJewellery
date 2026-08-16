import { notFound } from "next/navigation";
import { getCategoryBySlug } from "@/lib/data/cache";

/**
 * Resolves the slug so that a category which does not exist answers with
 * a real HTTP 404. The reasoning is identical to the product route's —
 * see `app/product/[slug]/layout.tsx` for why a layout, and not the
 * page, has to be the one that asks.
 */
export default async function CategoryLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  // A hidden category is indistinguishable from an unknown one, by
  // design — RLS returns null for both. See CatalogueSource rule 2.
  if (!(await getCategoryBySlug(slug))) notFound();

  return children;
}
