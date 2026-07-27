import { AspectBox } from "@/components/ui/aspect-box";
import { cn } from "@/lib/cn";
import { mapEmbedSrc, site } from "@/lib/config/site";

/**
 * The shop's location on a map.
 *
 * ── Renders nothing without a location ───────────────────────────────
 * ADR-0010 rule 2: an absent value hides its section cleanly. An empty
 * grey rectangle where a map should be reads as a broken page, and a map
 * of the wrong place is worse than none — a customer would drive to it.
 * The address above stays readable either way, which is why the map is
 * an addition to the contact details rather than the way they are given.
 *
 * ── Fixed frame ──────────────────────────────────────────────────────
 * Inside an AspectBox at `hero` — 4/5 on phones, 16/9 above — so the
 * iframe cannot shift the page as it loads. An iframe is exactly the
 * kind of slow, third-party element that causes layout shift when left
 * to size itself.
 *
 * ── Lazy ─────────────────────────────────────────────────────────────
 * `loading="lazy"`: this is a map embed on a phone on mobile data, and
 * it is below the address a customer came for. Google's embed pulls a
 * substantial payload, and it must not compete with the page itself.
 */
export function ShopMap({ className }: { className?: string }) {
  const src = mapEmbedSrc();
  if (!src) return null;

  return (
    <AspectBox aspect="hero" className={cn("rounded-sm", className)}>
      <iframe
        src={src}
        // Named, not "map": a screen reader user tabbing into an unnamed
        // frame is told nothing about where they have landed.
        title={`Map showing ${site.name}, ${site.address.city}`}
        loading="lazy"
        referrerPolicy="no-referrer-when-downgrade"
        className="absolute inset-0 size-full border-0"
      />
    </AspectBox>
  );
}
