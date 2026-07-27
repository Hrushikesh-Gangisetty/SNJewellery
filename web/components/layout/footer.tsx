import Link from "next/link";
import { Container } from "@/components/ui/container";
import { Wordmark } from "./wordmark";
import {
  CallButton,
  DirectionsButton,
  WhatsAppButton,
} from "@/components/shop/conversion";
import { addressOneLine, site } from "@/lib/config/site";
import type { Category } from "@/lib/data/types";

/**
 * Site footer: shop details, hours, categories, contact.
 *
 * Every value comes from lib/config/site.ts (ADR-0010) — nothing here is
 * hard-coded. Sections whose values are still null render as NOTHING
 * rather than an empty box or a dead link, which is what lets this ship
 * before the owner has supplied social handles or a Maps location.
 */
export function Footer({ categories }: { categories: readonly Category[] }) {
  return (
    <footer className="bg-surface-raised border-border mt-24 border-t">
      <Container className="grid gap-12 py-16 md:grid-cols-3">
        <div>
          <Wordmark />
          <p className="text-body-s text-text-secondary mt-4 max-w-prose">
            {site.description}
          </p>
          {site.established ? (
            <p className="text-caption text-text-muted mt-4">
              Established {site.established}
            </p>
          ) : null}
        </div>

        <div>
          <h2 className="text-label text-text-muted">Visit us</h2>
          <address className="text-body-s text-text-primary mt-4 not-italic">
            {site.address.lines.map((line) => (
              <span key={line} className="block">
                {line}
              </span>
            ))}
          </address>
          <p className="text-body-s text-text-secondary mt-4">
            {site.hours.display}
          </p>
          {/* Renders nothing at all until a Maps location is supplied. */}
          <DirectionsButton variant="link" className="mt-4" />
        </div>

        <div>
          <h2 className="text-label text-text-muted">Browse</h2>
          <ul className="mt-4 flex flex-col">
            <li>
              <Link
                href="/catalogue"
                className="text-body-s text-text-secondary hover:text-text-primary flex min-h-11 items-center"
              >
                All jewellery
              </Link>
            </li>
            {categories.map((c) => (
              <li key={c.id}>
                <Link
                  href={`/category/${c.slug}`}
                  className="text-body-s text-text-secondary hover:text-text-primary flex min-h-11 items-center"
                >
                  {c.name}
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </Container>

      <Container className="border-border flex flex-wrap items-center justify-between gap-4 border-t py-8">
        <p className="text-caption text-text-muted">
          {site.name} · {addressOneLine()}
        </p>
        <div className="flex flex-wrap items-center gap-4">
          <CallButton variant="link" />
          <WhatsAppButton variant="link">WhatsApp</WhatsAppButton>
          {/* Empty array today, so this renders nothing rather than a
              heading with no links beneath it. */}
          {site.social.map((s) => (
            <a
              key={s.platform}
              href={s.url}
              target="_blank"
              rel="noopener noreferrer"
              className="text-body-s text-text-secondary inline-flex min-h-11 items-center"
            >
              {s.label}
            </a>
          ))}
        </div>
      </Container>
    </footer>
  );
}
