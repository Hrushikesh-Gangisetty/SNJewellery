"use client";

import { useState } from "react";
import Image from "next/image";
import { AspectBox } from "@/components/ui/aspect-box";
import { SoldBadge } from "@/components/ui/badge";
import { cn } from "@/lib/cn";
import { productImageAlt } from "@/lib/data/alt";
import type { Product } from "@/lib/data/types";

/**
 * Product image gallery. See components.md `ImageGallery` — variants
 * single-image and multi-image, states default and no-images.
 *
 * ── No layout shift, structurally ────────────────────────────────────
 * The frame is locked to the FIRST image's aspect and every image is
 * `object-contain` inside it. A gallery that adopted each image's own
 * ratio would resize the page as the customer tapped through, which is
 * the exact defect AspectBox exists to prevent — and mixed ratios in one
 * product's photographs are likely, not hypothetical.
 *
 * ── Keyboard ─────────────────────────────────────────────────────────
 * The thumbnail strip is a tablist with a roving tabindex: Left/Right
 * (and Home/End) move between images, one Tab stop for the whole strip.
 * Each tab is labelled "Image 2 of 4", which is what accessibility.md
 * requires the gallery to announce.
 *
 * ── Why images mount lazily but then stay ────────────────────────────
 * Only images the customer has actually looked at are mounted. Mounting
 * all of them would fetch every photograph on page load — and this site's
 * primary case is a phone on Indian mobile data (brand.md §2).
 *
 * The consequence is honest: the FIRST time an image is selected it
 * appears as it loads, and only subsequent switches cross-dissolve at
 * `base`/`standard` per motion.md. Buying an entrance transition at the
 * cost of downloading four unseen photographs would be the wrong trade.
 */
export function ImageGallery({
  product,
  className,
}: {
  product: Product;
  className?: string;
}) {
  const images = product.images;
  const [active, setActive] = useState(0);
  // Index 0 is on screen immediately, so it starts mounted.
  const [mounted, setMounted] = useState<ReadonlySet<number>>(
    () => new Set([0]),
  );

  function select(index: number) {
    setActive(index);
    setMounted((current) => new Set(current).add(index));
  }

  // Locked for the life of the gallery — see the note above.
  const frame = images[0]?.aspect ?? "product";

  if (images.length === 0) {
    return (
      <div className={className}>
        <AspectBox aspect="product">
          <span className="text-caption text-text-muted absolute inset-0 flex items-center justify-center">
            No photograph yet
          </span>
          {product.sold ? (
            <SoldBadge className="absolute top-3 left-3" />
          ) : null}
        </AspectBox>
      </div>
    );
  }

  return (
    <div className={className}>
      <AspectBox aspect={frame}>
        {images.map((image, index) =>
          mounted.has(index) ? (
            <Image
              key={image.id}
              src={image.url}
              alt={productImageAlt(product, index)}
              fill
              // The only above-the-fold image on this page.
              priority={index === 0}
              sizes="(min-width: 1024px) 50vw, 100vw"
              className={cn(
                "object-contain",
                "ease-standard transition-opacity duration-[var(--sn-duration-base)]",
                index === active ? "z-10 opacity-100" : "opacity-0",
              )}
            />
          ) : null,
        )}

        {product.sold ? (
          <SoldBadge className="absolute top-3 left-3 z-20" />
        ) : null}
      </AspectBox>

      {images.length > 1 ? (
        <div
          role="tablist"
          aria-label={`${product.name} photographs`}
          onKeyDown={(event) => {
            const last = images.length - 1;
            const next = {
              ArrowRight: active === last ? 0 : active + 1,
              ArrowLeft: active === 0 ? last : active - 1,
              Home: 0,
              End: last,
            }[event.key];

            if (next === undefined) return;
            event.preventDefault();
            select(next);
            // Roving tabindex: focus has to follow selection, or the next
            // arrow press is handled by an element that is no longer the
            // one the user believes is current.
            event.currentTarget
              .querySelectorAll<HTMLButtonElement>('[role="tab"]')
              [next]?.focus();
          }}
          // Horizontal scroll rather than wrapping: a wrapped strip
          // changes the page height as the count changes.
          className="mt-3 flex gap-2 overflow-x-auto pb-1"
        >
          {images.map((image, index) => (
            <button
              key={image.id}
              type="button"
              role="tab"
              aria-selected={index === active}
              aria-label={`Image ${index + 1} of ${images.length}`}
              tabIndex={index === active ? 0 : -1}
              onClick={() => select(index)}
              className={cn(
                "relative size-18 shrink-0 overflow-hidden rounded-sm",
                "bg-surface-sunken",
                "ease-standard transition-[border-color] duration-[var(--sn-duration-fast)]",
                // Selection is carried by a border AND by aria-selected,
                // never by colour alone (accessibility.md §1).
                index === active
                  ? "border-accent border-2"
                  : "border-border hover:border-border-interactive border",
              )}
            >
              <Image
                src={image.url}
                alt=""
                fill
                sizes="72px"
                className="object-contain"
              />
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}
