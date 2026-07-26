import { cn } from "@/lib/cn";
import type { ImageAspect } from "@/lib/data";

/**
 * Reserves an image's space before it loads, so layout shift is
 * structurally impossible rather than merely avoided.
 * See docs/design/responsive.md §2.
 *
 * Every product image must sit inside one of these. `surface-sunken` shows
 * through while loading and stays visible for a product with no image —
 * which is a real case, not a hypothetical.
 */

const ratios = {
  product: "aspect-product", // 1/1, the default
  "product-portrait": "aspect-product-portrait", // 4/5, long pieces
  hero: "aspect-hero-mobile md:aspect-hero", // 4/5 on phones, 16/9 up
} as const;

export type AspectName = ImageAspect | "hero";

export function AspectBox({
  aspect = "product",
  className,
  children,
}: {
  aspect?: AspectName;
  className?: string;
  children?: React.ReactNode;
}) {
  return (
    <div
      className={cn(
        "bg-surface-sunken relative w-full overflow-hidden",
        ratios[aspect],
        className,
      )}
    >
      {children}
    </div>
  );
}
