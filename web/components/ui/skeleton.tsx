import { cn } from "@/lib/cn";

/**
 * Loading placeholder.
 *
 * ux.md §3: a skeleton MUST match the real element's dimensions, or it
 * causes the very layout shift it exists to prevent.
 *
 * The pulse is suppressed under prefers-reduced-motion by the global rule
 * in globals.css, and motion.md requires it still read as a placeholder
 * when static — which it does, because the surface tone carries it.
 */
export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn("bg-surface-sunken animate-pulse rounded-sm", className)}
      aria-hidden="true"
    />
  );
}

/** Matches ProductCard's dimensions exactly. */
export function ProductCardSkeleton() {
  return (
    <div className="flex flex-col gap-3">
      <Skeleton className="aspect-product w-full rounded-none" />
      <Skeleton className="h-5 w-4/5" />
      <Skeleton className="h-4 w-2/5" />
      <Skeleton className="h-4 w-3/5" />
    </div>
  );
}
