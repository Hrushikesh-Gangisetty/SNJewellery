import { notFound } from "next/navigation";
import { getProductBySlug } from "@/lib/data/cache";

/**
 * Resolves the slug so that a piece which does not exist answers with a
 * real HTTP 404.
 *
 * ── Why this layout exists at all ────────────────────────────────────
 * It renders nothing. It is here for the status code, and without this
 * comment it is the kind of file a future reader deletes as pointless.
 *
 * `loading.tsx` wraps the page below it in a Suspense boundary, so Next
 * flushes that skeleton — with the headers, and therefore with `200` —
 * before `page.tsx` has read anything. By the time the page calls
 * `notFound()` the status is already committed, so the customer got the
 * styled *this page does not exist* screen under a `200`. A crawler
 * reads that as a soft 404: the URL stays in the index, and Search
 * Console reports it against the site.
 *
 * A layout renders *above* its segment's loading boundary. Awaiting the
 * read here means nothing can flush until the answer is known, so
 * `notFound()` still owns the status.
 *
 * ── Why this does not cost a second query ────────────────────────────
 * `getProductBySlug` is `unstable_cache`d on the slug, so the page's own
 * call is the same cache entry, not another round trip to Supabase.
 *
 * The skeleton is not wasted either — it still covers client-side
 * navigation, which is when a customer actually waits for one.
 */
export default async function ProductLayout({
  children,
  params,
}: {
  children: React.ReactNode;
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;

  // null covers unknown, archived, and hidden-category alike — the data
  // layer makes them indistinguishable on purpose (CatalogueSource rule 4).
  if (!(await getProductBySlug(slug))) notFound();

  return children;
}
