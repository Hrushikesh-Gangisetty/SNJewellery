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
  name: "SN Jewellery & Silver Palace",

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

  /** Path under web/public/. Falls back to the wordmark while null. */
  logo: null as Pending<{ src: string; srcDark: string; alt: string }>,

  contact: {
    /** E.164, for tel: links. */
    phone: "+919440248401",
    /** As displayed to a customer. */
    phoneDisplay: "+91 94402 48401",
    /** Digits only, no plus — wa.me requires this form. */
    whatsapp: "919440248401",
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
    intro: null as Pending<string>,
    history: null as Pending<string>,
    mission: null as Pending<string>,
    certifications: [] as readonly string[],
  },
} as const;

// ── Derived helpers ────────────────────────────────────────────────────
// Centralised so no component builds these URLs itself.

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
  const base = `https://wa.me/${site.contact.whatsapp}`;
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
