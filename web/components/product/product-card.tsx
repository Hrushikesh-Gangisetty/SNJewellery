import Image from "next/image";
import Link from "next/link";
import { AspectBox } from "@/components/ui/aspect-box";
import { SoldBadge } from "@/components/ui/badge";
import { cn } from "@/lib/cn";
import { productImageAlt, type Product } from "@/lib/data";

/**
 * Catalogue product card.
 *
 * Shows exactly the fields the PRD specifies: image, name, category,
 * purity, weight, short description.
 *
 * Notes on decisions that are easy to get wrong:
 *
 * - **The whole card is the link**, not just the name (ux.md §4).
 * - **Alt text is derived** from product data, never stored, so it cannot
 *   be empty or a filename (accessibility.md §3).
 * - **The name reserves two lines** so a one-line and a two-line card in
 *   the same row stay aligned. At 375px a two-column grid gives ~168px
 *   cards, and real product names wrap (responsive.md §3).
 * - **Sold is a worded badge**, never a tint.
 * - **`sizes` is accurate**, matching the grid column counts from
 *   layout.md §5. An inaccurate `sizes` makes next/image ship the wrong
 *   rendition, which is the most common cause of a slow image-heavy page.
 */
export function ProductCard({
  product,
  priority = false,
  className,
}: {
  product: Product;
  /** True only for above-the-fold images — see motion.md §4 and M12.1. */
  priority?: boolean;
  className?: string;
}) {
  const cover = product.images[0] ?? null;
  const spec = [
    product.purity?.code,
    product.weightGrams !== null ? `${product.weightGrams.toFixed(2)} g` : null,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <Link
      href={`/product/${product.slug}`}
      className={cn(
        "group flex flex-col gap-3 rounded-sm",
        "ease-standard transition-colors duration-[var(--sn-duration-fast)]",
        className,
      )}
    >
      <AspectBox aspect={cover?.aspect ?? "product"}>
        {cover ? (
          <Image
            src={cover.url}
            alt={productImageAlt(product)}
            fill
            priority={priority}
            // 2 cols to md, 3 to xl, 4 above — layout.md §5.
            sizes="(min-width: 1280px) 25vw, (min-width: 768px) 33vw, 50vw"
            className="object-contain"
          />
        ) : (
          // A product with no images is a real case, not a hypothetical.
          <span className="text-caption text-text-muted absolute inset-0 flex items-center justify-center">
            No photograph yet
          </span>
        )}

        {product.sold ? <SoldBadge className="absolute top-2 left-2" /> : null}
      </AspectBox>

      <div className="flex flex-col gap-1">
        {/* Two lines reserved so cards in a row align. */}
        <h3 className="text-heading-s text-text-primary group-hover:text-accent-text line-clamp-2 min-h-[2.8em]">
          {product.name}
        </h3>

        <p className="text-body-s text-text-secondary">
          {product.category.name}
        </p>

        {spec ? <p className="text-spec text-text-primary">{spec}</p> : null}

        {product.summary ? (
          <p className="text-body-s text-text-muted line-clamp-2">
            {product.summary}
          </p>
        ) : null}
      </div>
    </Link>
  );
}
