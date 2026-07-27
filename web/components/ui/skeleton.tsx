import { cn } from "@/lib/cn";

/**
 * Loading placeholder.
 *
 * ux.md §3: a skeleton MUST match the real element's dimensions, or it
 * causes the very layout shift it exists to prevent.
 *
 * The pulse is `sn-pulse`, not Tailwind's `animate-pulse`: motion.md §4
 * specifies the skeleton at `deliberate` with `standard` easing, and
 * Tailwind's runs 2s on a curve of its own — a duration written by hand,
 * which motion.md rule 1 forbids.
 *
 * It is suppressed under prefers-reduced-motion by the global rule in
 * globals.css, and motion.md requires it still read as a placeholder
 * when static — which it does, because the surface tone carries it.
 */
export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn("bg-surface-sunken sn-pulse rounded-sm", className)}
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
