import type { Metadata } from "next";
import { ContactMethods } from "@/components/shop/contact-methods";
import { StoreInformation } from "@/components/shop/store-information";
import { ButtonLink } from "@/components/ui/button";
import { Container } from "@/components/ui/container";
import { SectionHeading } from "@/components/ui/section-heading";
import { site } from "@/lib/config/site";

export const metadata: Metadata = {
  title: "About",
  description: site.about.intro,
};

/**
 * About page.
 *
 * The PRD's About Us section lists four things: shop history, experience,
 * mission, certifications. The owner supplied one paragraph on 2026-07-27
 * covering the first three in their own words, and said nothing about
 * certifications.
 *
 * So three of the four sections render nothing. That is the correct
 * outcome, not a gap to paper over: this page is about a jeweller's
 * trustworthiness, and inventing a founding year or implying a hallmark
 * certification nobody claimed would undermine the exact thing the copy
 * is asserting. ADR-0010 rule 2 — absent means hidden, never a
 * placeholder — matters more here than anywhere else on the site.
 *
 * The page ends on the shop's details and the two contact actions rather
 * than on prose. A customer who has just read why to trust this shop is
 * at the point of asking something (CLAUDE.md §2), and this is a short
 * page — making them navigate again to act would waste that.
 */
export default function AboutPage() {
  const { intro, history, mission, certifications } = site.about;

  return (
    <main id="main" className="flex-1">
      <Container as="section" className="py-10 md:py-14">
        <SectionHeading
          as="h1"
          eyebrow="Our shop"
          title={`About ${site.name}`}
        />

        <p className="text-body-l text-text-primary mt-8 max-w-prose">
          {intro}
        </p>

        {history ? (
          <div className="mt-12 max-w-prose">
            <h2 className="text-label text-text-muted">Our history</h2>
            <p className="text-body-m text-text-secondary mt-3 whitespace-pre-line">
              {history}
            </p>
          </div>
        ) : null}

        {mission ? (
          <div className="mt-12 max-w-prose">
            <h2 className="text-label text-text-muted">Our mission</h2>
            <p className="text-body-m text-text-secondary mt-3 whitespace-pre-line">
              {mission}
            </p>
          </div>
        ) : null}

        {certifications.length > 0 ? (
          <div className="mt-12 max-w-prose">
            <h2 className="text-label text-text-muted">Certifications</h2>
            <ul className="text-body-m text-text-secondary mt-3 flex flex-col gap-2">
              {certifications.map((certification) => (
                <li key={certification}>{certification}</li>
              ))}
            </ul>
          </div>
        ) : null}

        <div className="border-border mt-16 grid gap-12 border-t pt-12 md:grid-cols-2 md:gap-10">
          <div>
            <h2 className="text-label text-text-muted">Visit the showroom</h2>
            <StoreInformation className="mt-4" />
          </div>

          <div>
            <h2 className="text-label text-text-muted">Ask us anything</h2>
            <ContactMethods className="mt-4" />
          </div>
        </div>

        <div className="mt-16">
          <ButtonLink href="/catalogue">Browse the collection</ButtonLink>
        </div>
      </Container>
    </main>
  );
}
