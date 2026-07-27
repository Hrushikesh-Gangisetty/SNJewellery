import { CallButton, WhatsAppButton } from "./conversion";
import { cn } from "@/lib/cn";
import { site } from "@/lib/config/site";

/**
 * The ways a customer can reach the shop.
 *
 * Nothing is sold online, so these *are* the business outcome — see
 * CLAUDE.md §2. WhatsApp leads because it is how this shop's customers
 * actually make contact; the phone number is spelled out rather than
 * hidden behind the word "Call" so it can be read and dialled by hand.
 *
 * Every value comes from lib/config/site.ts (ADR-0010), and anything the
 * owner has not supplied — an email address, a social handle — renders as
 * nothing rather than a dead link.
 *
 * `message` pre-fills the WhatsApp text. The home and contact pages leave
 * it empty; the product page composes its own with `productEnquiry`.
 */
export function ContactMethods({
  message,
  className,
}: {
  message?: string;
  className?: string;
}) {
  return (
    <div className={cn("flex flex-col items-start gap-3", className)}>
      <WhatsAppButton message={message}>Message on WhatsApp</WhatsAppButton>

      <CallButton />

      {site.contact.email ? (
        <a
          href={`mailto:${site.contact.email}`}
          className="text-body-m text-accent-text inline-flex min-h-11 items-center"
        >
          {site.contact.email}
        </a>
      ) : null}

      {/* Empty today, so this renders nothing rather than a bare heading
          above no links. */}
      {site.social.length > 0 ? (
        <ul className="flex flex-wrap items-center gap-4">
          {site.social.map((link) => (
            <li key={link.platform}>
              <a
                href={link.url}
                target="_blank"
                rel="noopener noreferrer"
                className="text-body-m text-text-secondary hover:text-text-primary inline-flex min-h-11 items-center"
              >
                {link.label}
              </a>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}
