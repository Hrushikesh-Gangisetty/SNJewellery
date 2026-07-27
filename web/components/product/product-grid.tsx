import { ProductCard } from "./product-card";
import { ProductCardSkeleton } from "@/components/ui/skeleton";
import { EmptyState } from "@/components/ui/empty-state";
import { cn } from "@/lib/cn";
import type { Product } from "@/lib/data/types";

/**
 * Responsive product grid. Column counts and gaps come from
 * docs/design/layout.md §5.
 *
 * Two columns at 375px, not one: a single-column jewellery catalogue on a
 * phone means endless scrolling, and these pieces suit comparison at a
 * glance. Four is the ceiling before photographs shrink below the size at
 * which detail is visible.
 *
 * Gaps tighten on mobile before gutters do (responsive.md §3).
 */

const grid = cn(
  "grid grid-cols-2 gap-3",
  "sm:gap-4",
  "md:grid-cols-3",
  "lg:gap-6",
  "xl:grid-cols-4",
);

export function ProductGrid({
  products,
  /** Number of leading images to mark priority — above-the-fold only. */
  priorityCount = 0,
  enterFrom,
  className,
}: {
  products: readonly Product[];
  priorityCount?: number;
  /**
   * Index of the first card that arrived *after* an interaction. Those
   * cards fade and rise in; everything before is server-rendered and
   * must not animate (motion.md §4). Omitted, nothing animates — which
   * is the right default, since most grids on this site are the page.
   */
  enterFrom?: number;
  className?: string;
}) {
  if (products.length === 0) {
    return (
      <EmptyState
        title="No pieces to show yet"
        description="New pieces are added regularly. Please check back, or ask us what is in store."
      />
    );
  }

  return (
    <div className={cn(grid, className)}>
      {products.map((product, i) => (
        <ProductCard
          key={product.id}
          product={product}
          priority={i < priorityCount}
          className={
            enterFrom !== undefined && i >= enterFrom ? "sn-enter" : undefined
          }
        />
      ))}
    </div>
  );
}

/** Same geometry as ProductGrid, so swapping in real cards shifts nothing. */
export function ProductGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <div className={grid} aria-busy="true" aria-live="polite">
      {Array.from({ length: count }, (_, i) => (
        <ProductCardSkeleton key={i} />
      ))}
    </div>
  );
}
