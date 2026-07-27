import { Container } from "@/components/ui/container";
import { ProductGridSkeleton } from "@/components/product/product-grid";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Catalogue loading state.
 *
 * ux.md §3: skeleton cards matching the real card's dimensions exactly,
 * so nothing shifts when the content arrives. ProductGridSkeleton shares
 * ProductGrid's geometry for that reason — a skeleton of the wrong size
 * causes the very layout shift it exists to prevent.
 *
 * The heading and chip row are placeheld too. Rendering the real heading
 * over skeleton cards would be quicker to write and would make the page
 * appear to load in two jumps.
 */
export default function Loading() {
  return (
    <main id="main" className="flex-1">
      <Container as="section" className="py-10 md:py-14">
        <Skeleton className="h-4 w-28" />
        <Skeleton className="mt-3 h-9 w-64" />

        <div className="mt-6 flex flex-wrap gap-2">
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} className="h-11 w-28 rounded-md" />
          ))}
        </div>

        <div className="mt-10">
          <ProductGridSkeleton count={8} />
        </div>
      </Container>
    </main>
  );
}
