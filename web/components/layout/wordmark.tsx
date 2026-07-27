import Image from "next/image";
import Link from "next/link";
import { site } from "@/lib/config/site";
import { cn } from "@/lib/cn";

/**
 * Brand mark in the header and footer.
 *
 * Two forms, per brand.md §4: the winged SN monogram where space is
 * tight, the full lockup where it is not. `compact` picks the monogram —
 * which is the answer to the 28-character trading name being unusable in
 * a mobile header (brand.md §1).
 *
 * `site.logo` may be null, and the Cormorant wordmark below is a
 * legitimate fallback rather than a placeholder, so this keeps working if
 * the asset is ever withdrawn. Set at heading-l (28px) — Cormorant's
 * floor, never smaller (typography.md §1).
 *
 * Height is fixed and width derives from the intrinsic ratio, so the
 * header reserves its box before the image loads and nothing shifts.
 *
 * Deliberately NOT `priority`. A preload link for the logo competes with
 * the hero or gallery image that actually is the LCP element, and this
 * mark optimises down to a couple of kilobytes. `eager` gets it fetched
 * without entering that race; the footer's copy stays lazy.
 */

/** Rendered heights in px. Both clear brand.md §4's 24px legibility floor. */
const LOCKUP_HEIGHT = 44;
const MONOGRAM_HEIGHT = 36;

export function Wordmark({
  /** Compact form for the mobile header. */
  compact = false,
  /** True above the fold — the header, not the footer. */
  eager = false,
  className,
}: {
  compact?: boolean;
  eager?: boolean;
  className?: string;
}) {
  const logo = site.logo;
  const asset = logo ? (compact ? logo.monogram : logo.lockup) : null;
  const height = compact ? MONOGRAM_HEIGHT : LOCKUP_HEIGHT;

  return (
    <Link
      href="/"
      className={cn("inline-flex min-h-11 items-center", className)}
      aria-label={`${site.name} — home`}
    >
      {asset ? (
        <Image
          src={asset.src}
          // The link is already labelled, so giving the image a name too
          // makes a screen reader announce the shop twice.
          alt=""
          width={Math.round((asset.width / asset.height) * height)}
          height={height}
          loading={eager ? "eager" : "lazy"}
          className="h-auto w-auto"
          style={{ height }}
        />
      ) : (
        <span className="font-display text-heading-l text-text-primary leading-none">
          {compact ? site.shortName : site.name}
        </span>
      )}
    </Link>
  );
}
