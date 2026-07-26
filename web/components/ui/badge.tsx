import { cn } from "@/lib/cn";

/**
 * Small status label. See docs/design/components.md.
 *
 * `SoldBadge` deliberately spells out the word "Sold" rather than tinting
 * the card. accessibility.md §1: colour never carries meaning alone — and
 * a worded badge also survives being screenshotted and forwarded on
 * WhatsApp, which is how these pages actually get shared.
 */

const tones = {
  neutral: "bg-surface-sunken text-text-secondary",
  accent: "bg-accent text-on-accent",
  outline: "border border-border-interactive text-text-secondary",
} as const;

export function Badge({
  tone = "neutral",
  className,
  children,
}: {
  tone?: keyof typeof tones;
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <span
      className={cn(
        "text-label inline-flex items-center rounded-sm px-2 py-1",
        tones[tone],
        className,
      )}
    >
      {children}
    </span>
  );
}

/** Sold pieces stay visible — the owner's decision. */
export function SoldBadge({ className }: { className?: string }) {
  return (
    <Badge tone="neutral" className={className}>
      Sold
    </Badge>
  );
}

export function FeaturedBadge({ className }: { className?: string }) {
  return (
    <Badge tone="accent" className={className}>
      Featured
    </Badge>
  );
}
