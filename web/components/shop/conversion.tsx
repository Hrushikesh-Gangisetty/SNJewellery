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
 * outcome (CLAUDE.md §2). They are named components rather than inline
 * anchors because the same three appear on the product page, the contact
 * page, the footer, and the drawer — and an enquiry link that differs
 * between them is a defect a customer never reports.
 *
 * All three hold the 44px floor in every variant. They are pressed
 * one-handed, often while walking (layout.md §6).
 */

/**
 * - `primary` — a real button. WhatsApp is the only one that FILLS: it is
 *   how this shop's customers actually make contact, and two filled
 *   buttons side by side would make neither the obvious next step.
 * - `inline` — ghost button, for a row beside other content.
 * - `link` — bare text, for the footer bar. Still a 44px target.
 */
type Variant = "primary" | "inline" | "link";

const linkClasses =
  "text-body-s inline-flex min-h-11 items-center rounded-md " +
  "ease-standard transition-colors duration-[var(--sn-duration-fast)]";

/**
 * One anchor, so the external-link handling is written once.
 *
 * `tel:` must NOT get target/rel — it is handed to the dialler, not
 * opened in a tab — which is exactly the kind of detail that drifts when
 * every call site builds its own anchor.
 */
function ActionLink({
  href,
  variant,
  external,
  emphasis = false,
  className,
  children,
}: {
  href: string;
  variant: Variant;
  external: boolean;
  /** Gold text, for the one action a bare link should still lead with. */
  emphasis?: boolean;
  className?: string;
  children: React.ReactNode;
}) {
  if (variant !== "link") {
    return (
      <ButtonLink
        href={href}
        variant={
          variant === "inline" ? "ghost" : emphasis ? "primary" : "secondary"
        }
        className={className}
      >
        {children}
      </ButtonLink>
    );
  }

  return (
    <a
      href={href}
      {...(external ? { target: "_blank", rel: "noopener noreferrer" } : {})}
      className={cn(
        linkClasses,
        emphasis
          ? "text-accent-text hover:text-text-primary"
          : "text-text-secondary hover:text-text-primary",
        className,
      )}
    >
      {children}
    </a>
  );
}

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
    <ActionLink
      href={whatsAppHref(message)}
      variant={variant}
      external
      emphasis
      className={className}
    >
      {children}
    </ActionLink>
  );
}

export function CallButton({
  variant = "primary",
  /**
   * Spell the number out rather than saying "Call" — a customer may want
   * to read it and dial by hand, or copy it into their contacts.
   */
  showNumber = true,
  className,
}: {
  variant?: Variant;
  showNumber?: boolean;
  className?: string;
}) {
  return (
    <ActionLink
      href={telHref()}
      variant={variant}
      external={false}
      className={className}
    >
      {showNumber ? `Call ${site.contact.phoneDisplay}` : "Call the shop"}
    </ActionLink>
  );
}

/**
 * Renders NOTHING while no Maps location has been supplied — the
 * "unavailable" state in components.md, which is a hidden button and not
 * a disabled one. A greyed-out Get Directions tells a customer the shop
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
    <ActionLink href={href} variant={variant} external className={className}>
      Get directions
    </ActionLink>
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
