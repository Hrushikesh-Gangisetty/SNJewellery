"use client";

import { useRef, useState } from "react";
import Link from "next/link";
import { Container } from "@/components/ui/container";
import { Wordmark } from "./wordmark";
import { MobileDrawer } from "./mobile-drawer";
import { WhatsAppButton } from "@/components/shop/conversion";
import { telHref } from "@/lib/config/site";
import type { Category } from "@/lib/data/types";

/**
 * Site header. See docs/design/responsive.md §3.
 *
 * Sticky, but deliberately short on mobile — vertical space is the
 * scarcest resource on a phone showing photographs.
 *
 * Below lg: compact wordmark (the winged monogram, once its SVG arrives)
 * plus a hamburger. At lg and above: full name, horizontal nav, inline
 * contact actions.
 */
export function Header({ categories }: { categories: readonly Category[] }) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);

  return (
    <header className="bg-surface/95 border-border sticky top-0 z-30 border-b backdrop-blur">
      <Container className="flex min-h-16 items-center justify-between gap-4 lg:min-h-20">
        {/* Monogram below lg — neither the 28-character name nor the full
            lockup fits, and the lockup's "& Silver Palace" line would be
            about three pixels tall (brand.md §4). */}
        {/* Both hide with a `max-*` variant rather than a bare `hidden`.
            `cn` concatenates and does not resolve conflicts, so a plain
            `hidden` sits alongside the component's own `inline-flex` at
            equal specificity and loses on stylesheet order — which put
            the monogram AND the lockup in the mobile header at once,
            each squashed by the flex row. A variant outranks the base
            utility, so this cannot silently lose. See wordmark.tsx. */}
        <Wordmark compact eager className="lg:hidden" />
        <Wordmark eager className="max-lg:hidden" />

        <nav aria-label="Main" className="hidden items-center gap-1 lg:flex">
          <NavLink href="/catalogue">All jewellery</NavLink>
          {categories.slice(0, 4).map((c) => (
            <NavLink key={c.id} href={`/category/${c.slug}`}>
              {c.name}
            </NavLink>
          ))}
          <NavLink href="/about">About</NavLink>
          <NavLink href="/contact">Contact</NavLink>
        </nav>

        <div className="flex items-center gap-2">
          {/* Persistent conversion actions — see ux.md §2.
              Icon-only, so this is the one call action that does not use
              CallButton: there is no room for the number at this width,
              and a fourth variant for a single site would cost more than
              it saves. It still takes its href from telHref, so the
              number itself is not duplicated. */}
          <a
            href={telHref()}
            aria-label="Call the shop"
            className="text-text-primary hover:bg-surface-sunken inline-flex min-h-11 min-w-11 items-center justify-center rounded-md"
          >
            <PhoneIcon />
          </a>
          {/* `max-sm:hidden`, not `hidden sm:inline-flex`, for the reason
              given at the wordmarks above — the button's own base class
              sets `inline-flex` too. */}
          <WhatsAppButton className="text-body-s max-sm:hidden" />

          <button
            ref={triggerRef}
            type="button"
            onClick={() => setDrawerOpen(true)}
            aria-label="Open menu"
            aria-expanded={drawerOpen}
            className="text-text-primary hover:bg-surface-sunken inline-flex min-h-11 min-w-11 items-center justify-center rounded-md lg:hidden"
          >
            <MenuIcon />
          </button>
        </div>
      </Container>

      <MobileDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        categories={categories}
        triggerRef={triggerRef}
      />
    </header>
  );
}

function NavLink({
  href,
  children,
}: {
  href: string;
  children: React.ReactNode;
}) {
  return (
    <Link
      href={href}
      className="text-body-s text-text-secondary hover:bg-surface-sunken hover:text-text-primary inline-flex min-h-11 items-center rounded-md px-3"
    >
      {children}
    </Link>
  );
}

function PhoneIcon() {
  return (
    <svg
      width="18"
      height="18"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      aria-hidden="true"
    >
      <path
        d="M4 3h3l1.5 4L6.5 8.5a9 9 0 005 5L13 11.5l4 1.5v3a1 1 0 01-1.1 1A13.5 13.5 0 013 4.1A1 1 0 014 3z"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function MenuIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      aria-hidden="true"
    >
      <path d="M3 6h14M3 10h14M3 14h14" strokeLinecap="round" />
    </svg>
  );
}
