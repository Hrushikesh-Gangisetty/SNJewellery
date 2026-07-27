import type { Metadata } from "next";
import { ContactMethods } from "@/components/shop/contact-methods";
import { MetalRates } from "@/components/shop/metal-rates";
import { StoreInformation } from "@/components/shop/store-information";
import { Container } from "@/components/ui/container";
import { SectionHeading } from "@/components/ui/section-heading";
import { site } from "@/lib/config/site";
import { getMetalRates } from "@/lib/data/cache";

/**
 * ISR. The rates panel is the only live data here, and it carries the
 * tag M9 invalidates; this interval is the fallback ADR-0006 requires.
 * See docs/architecture/rendering.md.
 */
export const revalidate = 600;

export const metadata: Metadata = {
  title: "Contact",
  description: `Visit ${site.name} in ${site.address.city}, or reach us on WhatsApp or by phone. Open ${site.hours.display}.`,
};

/**
 * Contact page.
 *
 * The whole site exists to produce a visit or a phone call (CLAUDE.md
 * §2), so this is the page every other page is pointing at. It answers
 * three questions in the order a customer asks them: how do I reach you
 * now, where are you, and when are you open.
 *
 * Every value comes from lib/config/site.ts (ADR-0010) — this page adds
 * layout, not content. Nothing here is written twice: the address block
 * and the contact buttons are the same components the home page uses,
 * which is why the shop's details cannot drift between the two pages.
 *
 * ── Reaching us comes first ──────────────────────────────────────────
 * WhatsApp and the phone number lead, above the map, and on a phone they
 * are the first thing under the heading. A customer on this page has
 * already decided to make contact; making them scroll past a map embed
 * to do it would be the site working against its own purpose.
 */
export default async function ContactPage() {
  const rates = await getMetalRates();

  return (
    <main id="main" className="flex-1">
      <Container as="section" className="py-10 md:py-14">
        <SectionHeading
          as="h1"
          eyebrow="Visit or call"
          title="Contact the shop"
        />

        <p className="text-body-l text-text-secondary mt-4 max-w-prose">
          Ask about a piece, check what is in store today, or arrange a time to
          visit. Nothing is sold online — every enquiry is answered by the shop
          directly.
        </p>

        <div className="mt-12 grid gap-12 md:grid-cols-2 md:gap-10">
          <div>
            <h2 className="text-label text-text-muted">Reach us</h2>
            <ContactMethods className="mt-4" />
          </div>

          <div>
            <h2 className="text-label text-text-muted">Where to find us</h2>
            {/* Address, hours, and the directions button — the last of
                which hides itself until a Maps location is supplied. */}
            <StoreInformation className="mt-4" />
          </div>
        </div>

        {/* No embedded map. The owner confirmed on 2026-07-27 that there
            is none, so the address and the directions link are how a
            customer finds the shop — see M4.10 in DEVELOPMENT_PLAN.md. */}

        {/* M4.14's second placement: a customer asking about a piece is
            the one most likely to want today's rate. Renders nothing
            until both metals are published. */}
        <MetalRates rates={rates} className="mt-16" />
      </Container>
    </main>
  );
}
