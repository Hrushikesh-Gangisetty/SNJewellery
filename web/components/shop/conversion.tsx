import { ButtonLink } from "@/components/ui/button";
import { cn } from "@/lib/cn";
import { publicEnv } from "@/lib/config/env";
import { directionsHref, site, telHref, whatsAppHref } from "@/lib/config/site";
import type { Product } from "@/lib/data/types";

/**
 * The three conversion actions. See components.md — "Conversion, the
 * site's entire purpose".
 *
 * Nothing is sold online, so pressing one of these IS the business
 * outcome (CLAUDE.md §2). They are named components rather than three
 * more inline anchors because the same three appear on the product page,
 * the contact page, the footer and the header, and an enquiry link that
 * differs between them is a defect a customer never reports.
 *
 * All three inherit ButtonLink's 44px floor. They are pressed one-handed,
 * often while walking (layout.md §6).
 */

/**
 * `inline` is the compact form for a row of actions beside other text.
 *
 * WhatsApp is the only one that fills: it is how this shop's customers
 * actually make contact, and two filled buttons side by side would make
 * neither of them the obvious next step.
 */
type Variant = "primary" | "inline";

/**
 * The pre-filled enquiry.
 *
 * Written the way a customer would actually open the conversation, in
 * brand.md §5's register: plain, specific, no exclamation mark. The URL
 * is absolute because the message is read inside WhatsApp, where a
 * relative path is meaningless — and it is what lets the owner see which
 * piece is being asked about without a follow-up question.
 */
export function productEnquiry(product: Product): string {
  const url = `${publicEnv.siteUrl}/product/${product.slug}`;
  return `Hello, I would like to ask about ${product.name} — ${url}`;
}

export function WhatsAppButton({
  message,
  variant = "primary",
  className,
  children = "Ask on WhatsApp",
}: {
  /** Pre-filled text. Encoded by whatsAppHref, so "&" survives. */
  message?: string;
  variant?: Variant;
  className?: string;
  children?: React.ReactNode;
}) {
  return (
    <ButtonLink
      href={whatsAppHref(message)}
      variant={variant === "primary" ? "primary" : "ghost"}
      className={className}
    >
      {children}
    </ButtonLink>
  );
}

export function CallButton({
  variant = "primary",
  /** Spell out the number rather than saying "Call" — it can be dialled by hand. */
  showNumber = true,
  className,
}: {
  variant?: Variant;
  showNumber?: boolean;
  className?: string;
}) {
  return (
    <ButtonLink
      href={telHref()}
      variant={variant === "primary" ? "secondary" : "ghost"}
      className={className}
    >
      {showNumber ? `Call ${site.contact.phoneDisplay}` : "Call the shop"}
    </ButtonLink>
  );
}

/**
 * Returns null when no Maps location has been supplied — the
 * "unavailable" state in components.md, which is a hidden button and not
 * a disabled one. A disabled Get Directions tells a customer the shop
 * cannot be found.
 */
export function DirectionsButton({
  variant = "primary",
  className,
}: {
  variant?: Variant;
  className?: string;
}) {
  const href = directionsHref();
  if (!href) return null;

  return (
    <ButtonLink
      href={href}
      variant={variant === "primary" ? "secondary" : "ghost"}
      className={className}
    >
      Get directions
    </ButtonLink>
  );
}

/**
 * The three together, as they appear on a product page. Stacks on mobile
 * so each is a full-width target for a thumb.
 */
export function ConversionActions({
  product,
  className,
}: {
  product: Product;
  className?: string;
}) {
  return (
    <div
      className={cn("flex flex-col gap-3 sm:flex-row sm:flex-wrap", className)}
    >
      <WhatsAppButton message={productEnquiry(product)}>
        Ask about this piece
      </WhatsAppButton>
      <CallButton />
      <DirectionsButton />
    </div>
  );
}
