"use client";

import { useEffect } from "react";
import { fontVariables } from "@/lib/fonts";
import { phoneDisplay } from "@/lib/config/site";
import "./globals.css";

/**
 * The last resort: an error thrown by the root layout itself.
 *
 * `error.tsx` handles a failure inside a route, and it renders *within*
 * the layout — header, footer, fonts and all. That is the right shape for
 * a failed query, which is what almost every error here will be. But it
 * cannot catch a failure in the layout, and the layout does real work:
 * `getVisibleCategories` runs there so the header and footer share one
 * read. If that throws, `error.tsx` never renders and the customer gets
 * the browser's own blank failure page.
 *
 * ── Why this file repeats html and body ──────────────────────────────
 * Because it replaces the root layout rather than sitting inside it —
 * that is Next's contract for this file, not a stylistic choice. For the
 * same reason it imports the stylesheet and the fonts itself: nothing
 * above it has run, so nothing above it has loaded them.
 *
 * Everything visual still comes from the design tokens (ADR-0008). A
 * hand-written colour here would be the one place in the site that does
 * not match it, on the page seen when the site is already broken.
 *
 * ── No Try again, and no route links ─────────────────────────────────
 * `reset` is offered because it costs a re-render rather than a page
 * load. What is *not* offered is a link to another page of this site: if
 * the root layout cannot render, neither can any route inside it, so
 * "browse the catalogue" would be a button that leads to this same page.
 * The shop's phone number is the honest alternative, and it is written
 * out rather than linked for the same reason.
 */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // The digest only. The error object can carry a query, and CLAUDE.md
    // §9 forbids logging anything that might.
    console.error("Root layout error", error.digest ?? "(no digest)");
  }, [error]);

  return (
    <html lang="en" className={`${fontVariables} h-full antialiased`}>
      <body className="bg-surface text-text-primary flex min-h-full flex-col">
        <main className="flex flex-1 items-center justify-center px-6 py-20">
          <div className="max-w-prose text-center">
            <h1 className="text-text-primary text-heading-l">
              The site is having a problem
            </h1>
            <p className="text-text-secondary text-body-m mt-4">
              This is on our side, not your connection. Please try again in a
              moment — or call the shop on {phoneDisplay()}, which is open as
              usual.
            </p>
            <button
              type="button"
              onClick={reset}
              className="bg-accent text-on-accent text-body-m mt-8 inline-flex min-h-11 items-center justify-center rounded-md px-5 py-2 font-medium"
            >
              Try again
            </button>
          </div>
        </main>
      </body>
    </html>
  );
}
