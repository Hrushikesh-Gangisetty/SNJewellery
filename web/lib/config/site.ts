/**
 * Site content and shop details — the single source per ADR-0010.
 *
 * No component may hard-code any of this. Not the shop name, not the
 * phone number, not an opening hour. M4's acceptance criteria verify
 * that by searching for literal values.
 *
 * Values the owner has not supplied yet are `null`, never a placeholder
 * string. `null` means "hide the section cleanly"; a placeholder would
 * mean shipping "Coming soon" to a customer. Every consumer must handle
 * `null` by rendering nothing — not an empty box, not a dead link.
 *
 * Updating any of this is a one-line edit here, a commit, and a deploy.
 * That is the agreed cost — see ADR-0010's consequences section.
 */

/** A value the owner will supply later. Renders as nothing until then. */
type Pending<T> = T | null;

/**
 * The shop's number in E.164 — **the only place it is written.**
 *
 * A `tel:` link, a `wa.me` path, and the label a customer reads all need
 * different forms of it, and holding three literals is how a number gets
 * changed in two of them. Every other form is derived below, so M4.9's
 * criterion — one occurrence of the number in the repository — holds by
 * construction rather than by discipline.
 */
const PHONE_E164 = "+919440248401";

/**
 * The trading name, hoisted for the same reason as the number: the About
 * copy opens with it, and a name changed in `site.name` but left standing
 * in a paragraph of prose is the drift this module exists to prevent.
 */
const SHOP_NAME = "SN Jewellery & Silver Palace";

export type SocialLink = {
  readonly platform: "instagram" | "facebook" | "youtube" | "whatsapp";
  readonly url: string;
  /** Accessible name — "Instagram", not "Follow us!" */
  readonly label: string;
};

export type OpeningHours = {
  /** 24-hour, "HH:MM". Feeds LocalBusiness structured data in M11.3. */
  readonly opens: string;
  readonly closes: string;
  /** Human-readable, for display. */
  readonly display: string;
  /** Days closed, as ISO weekday numbers (1 = Monday). Empty = open daily. */
  readonly closedOn: readonly number[];
};

export const site = {
  /** Full trading name. Never abbreviate — see docs/design/brand.md §1. */
  name: SHOP_NAME,

  /** For constrained space, e.g. the mobile header. */
  shortName: "SN Jewellery",

  /**
   * Factual, not promotional. Used as the default meta description and
   * on social previews.
   */
  description:
    "Gold and silver jewellery in Markapur. Browse our collections, then visit our showroom or contact us to enquire.",

  /** Not supplied; brand.md §1 says do not invent one. */
  tagline: null as Pending<string>,

  /** Year established — shown as a trust signal once supplied. */
  established: null as Pending<number>,

  /**
   * Brand assets under web/public/. Null falls back to the wordmark.
   *
   * Two forms, because brand.md §4 assigns them different jobs: the
   * lockup where there is room, the winged monogram where there is not
   * (mobile header, favicon) — that is what solves the 28-character-name
   * problem in the mobile header.
   *
   * Intrinsic dimensions are recorded so next/image can reserve the box
   * and no logo swap can shift the header.
   *
   * There is no separate dark asset: the mark is metallic gold on
   * transparent, which reads on both white and near-black. If a reversed
   * version is ever supplied, add `srcDark` here rather than filtering
   * this one — brand.md §4 forbids recolouring.
   */
  logo: {
    /** Desktop header, footer, social previews. */
    lockup: { src: "/site.logo.png", width: 725, height: 451 },
    /** Mobile header and favicon. */
    monogram: { src: "/site.monogram.png", width: 409, height: 409 },
    alt: "SN Jewellery & Silver Palace",
  } as Pending<{
    lockup: { src: string; width: number; height: number };
    monogram: { src: string; width: number; height: number };
    alt: string;
  }>,

  contact: {
    /**
     * E.164, for `tel:` links. The display form and the `wa.me` form are
     * derived from it — see `phoneDisplay` and `whatsAppHref`.
     *
     * The shop uses one number for calls and for WhatsApp. If that ever
     * stops being true, WhatsApp gets its own constant here; do not
     * reintroduce a second literal for a form of the same number.
     */
    phone: PHONE_E164,
    email: null as Pending<string>,
  },

  address: {
    street: "4-394/A, Temple Street",
    city: "Markapur",
    postalCode: "523316",
    region: "Andhra Pradesh",
    country: "IN",
    /** Multi-line display order. */
    lines: ["4-394/A", "Temple Street", "Markapur - 523316"],
    /**
     * Supplied later. Until then the Contact page shows the address as
     * text, the map is hidden, and the directions button hides itself.
     * M11.3's LocalBusiness structured data wants the coordinates.
     */
    geo: null as Pending<{ latitude: number; longitude: number }>,
    mapsUrl: null as Pending<string>,
  },

  /** Open every day, 10:00–21:00. */
  hours: {
    opens: "10:00",
    closes: "21:00",
    display: "10:00 AM – 9:00 PM, every day",
    closedOn: [],
  } satisfies OpeningHours,

  social: [] as readonly SocialLink[],

  /**
   * About page. Each field hides its section when null, so the page is
   * publishable before the owner has written any of it.
   */
  about: {
    /**
     * Supplied verbatim by the owner, 2026-07-27. It is their words about
     * their shop, so it is reproduced exactly — not rewritten, not
     * expanded with claims nobody made. Note what it does NOT say: no
     * founding year, no number of years in trade, no certification. The
     * fields below stay null for precisely that reason.
     */
    intro: `${SHOP_NAME} has been serving customers with trust, honesty, and quality. We offer genuine gold and silver jewellery at fair prices and always strive to give the best service to every customer.`,
    history: null as Pending<string>,
    mission: null as Pending<string>,
    certifications: [] as readonly string[],
  },
} as const;

// ── Derived helpers ────────────────────────────────────────────────────
// Centralised so no component builds these URLs itself.

/** Digits only, no plus — the form `wa.me` requires. */
function phoneDigits(): string {
  return site.contact.phone.replace(/\D/g, "");
}

/**
 * The number as a customer reads it: `+91 94402 48401`.
 *
 * Indian mobile grouping — country code, then 5 and 5 — because the last
 * ten digits are the national number for `+91`. This site serves one
 * shop in one country, so a general-purpose phone formatter would be a
 * dependency earning nothing. A number for a different country would
 * need this rule revisited, not just the constant changed.
 */
export function phoneDisplay(): string {
  const digits = phoneDigits();
  const national = digits.slice(-10);
  const country = digits.slice(0, -10);
  return `+${country} ${national.slice(0, 5)} ${national.slice(5)}`;
}

/** `tel:` href. */
export function telHref(): string {
  return `tel:${site.contact.phone}`;
}

/**
 * WhatsApp deep link. `message` is encoded, so product names containing
 * spaces and ampersands survive — M4.12's acceptance criteria test
 * exactly that case.
 */
export function whatsAppHref(message?: string): string {
  const base = `https://wa.me/${phoneDigits()}`;
  return message ? `${base}?text=${encodeURIComponent(message)}` : base;
}

/**
 * Directions link, or `null` when no location has been supplied — in
 * which case the button must not render at all.
 */
export function directionsHref(): string | null {
  if (site.address.mapsUrl) return site.address.mapsUrl;
  if (site.address.geo) {
    const { latitude, longitude } = site.address.geo;
    return `https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`;
  }
  return null;
}

/** Single-line address, for structured data and compact display. */
export function addressOneLine(): string {
  const { street, city, postalCode } = site.address;
  return `${street}, ${city} - ${postalCode}`;
}
