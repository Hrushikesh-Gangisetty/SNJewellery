import Link from "next/link";
import { site } from "@/lib/config/site";
import { cn } from "@/lib/cn";

/**
 * Brand mark in the header and footer.
 *
 * The logo SVGs have not been supplied yet, so this renders the wordmark
 * in Cormorant — a legitimate fallback per brand.md §4, not a placeholder.
 * When the assets arrive, `site.logo` becomes non-null and this component
 * swaps to the lockup on desktop and the winged SN monogram on mobile,
 * which is what solves the 28-character-name problem (brand.md §1).
 *
 * Set at heading-l (28px) — exactly Cormorant's floor. Never smaller.
 */
export function Wordmark({
  /** Compact form for the mobile header. */
  compact = false,
  className,
}: {
  compact?: boolean;
  className?: string;
}) {
  return (
    <Link
      href="/"
      className={cn("inline-flex min-h-11 items-center", className)}
      aria-label={`${site.name} — home`}
    >
      <span className="font-display text-heading-l text-text-primary leading-none">
        {compact ? site.shortName : site.name}
      </span>
    </Link>
  );
}
