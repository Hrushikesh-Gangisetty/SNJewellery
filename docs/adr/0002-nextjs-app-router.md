# ADR-0002: Next.js 15 App Router for the customer website

- **Status:** ✅ Accepted
- **Date:** 2026-07-25
- **Deciders:** Hrushikesh Gangisetty — specified in the PRD
- **Affects:** M2, M4, M11, M12

## Context

The PRD's Tech Stack section specifies Next.js 15, TypeScript, React, Tailwind, shadcn/ui, Framer Motion, and Vercel. This ADR records *why* that stack fits the requirements, so that a future reader can tell whether the reasoning still holds.

The website's requirements are unusually well suited to a static-first framework:

- **It never writes.** All mutation happens in the Android app. The website is a read-only view over a catalogue that changes a few times a day.
- **The non-functional targets are strict** — under two seconds on mobile, Lighthouse Performance above 90, SEO above 95.
- **SEO and social previews matter commercially.** Customers find the shop by search, and share products over WhatsApp, so server-rendered metadata and Open Graph tags are requirements, not niceties.
- **Content freshness has a one-minute budget**, not a real-time one.

A read-mostly, SEO-critical, image-heavy site with a one-minute freshness budget is close to the ideal case for incremental static regeneration.

## Decision

We will build the website with **Next.js 15 using the App Router**, in TypeScript with strict mode, deployed on Vercel.

Specifically:

- **Static generation with ISR** for catalogue and product pages, with cache tags placed for M9 to invalidate (M4.7).
- **`generateMetadata`** for per-product titles, descriptions, and Open Graph tags (M11.1, M11.2).
- **`next/image`** against the Supabase renditions from [ADR-0005](0005-image-storage-and-renditions.md), with fixed aspect ratios to prevent layout shift.
- **Server Components by default**; client components only where interactivity requires them.

## Consequences

### What this makes easier

- ISR plus cache tags is a direct mechanism for the PRD's one-minute freshness promise — see [ADR-0006](0006-cache-revalidation-strategy.md).
- Server-rendered metadata makes the SEO target reachable rather than a fight.
- `next/image` handles the sizing, format, and lazy-loading work that the performance target otherwise demands by hand.
- Vercel deployment is close to configuration-free, apart from the monorepo path (M5.2).
- Server Components keep the client bundle small, which serves the sub-two-second target directly.

### What this makes harder

- The App Router's caching model is subtle. Getting freshness right needs measurement, not assumption — which is why M9.6 measures rather than asserts.
- Server/client component boundaries are a real source of mistakes, particularly around hydration.
- Vercel is now a coupling. Self-hosting Next.js is possible but loses the ISR and image handling that make this choice pay.

### What this commits us to

The rendering strategy in M4.7 and the revalidation mechanism in M9 are both consequences of this choice. Moving off Next.js later would mean rebuilding both.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Astro | Arguably a better fit for a mostly-static catalogue, and lighter. But the PRD specifies Next.js, and shadcn/ui plus the React ecosystem is where the component work is cheapest. |
| Plain React SPA (Vite) | Fails the SEO requirement without bolting on SSR, and fails the sub-two-second mobile target on an image-heavy catalogue. |
| Next.js Pages Router | Older caching primitives and no `generateMetadata`. No reason to choose it for a greenfield project. |
| A CMS (Sanity, Contentful) as the content layer | The Android app *is* the CMS. Adding another content system duplicates the schema and the admin surface. |

## Implementation note (added M2.1)

`create-next-app@latest` now installs **Next.js 16**, not 15. The scaffold was
therefore pinned back to **15.5.21** with `--save-exact`, because the PRD
specifies 15 and this ADR records that decision.

Two consequences worth knowing:

- **Tailwind v4 was installed**, which configures its theme from CSS (`@theme`
  in `globals.css`) rather than from `tailwind.config.ts`. This changes what
  M2.4 and [ADR-0008](0008-design-tokens-single-source.md) target — CSS custom
  properties, not a JS config object.
- **`eslint-config-next` 15 uses the legacy eslintrc format**, so
  `eslint.config.mjs` loads it through `FlatCompat`. The Next 16 style of
  importing `eslint-config-next/core-web-vitals` directly does not resolve in
  15. Upgrading to 16 would revert that file to direct imports.

**Whether to stay on 15 or move to 16 is open** — see Open Question 15. Right
now it is a cheap change; after M4 it is not. Next 16's own agent guidance
warns that its APIs differ from pre-16 knowledge, which is an argument for
staying on 15 while the project is agent-built.

## Open sub-questions

- **Next 15 or 16** — Open Question 15. Cheap to change now, expensive after M4.
- Exact per-route rendering strategy and ISR intervals — decided and documented in **M4.7**.
- Whether any route genuinely needs dynamic rendering. Search results (M10) are the candidate.

## References

- [prd.md](../../prd.md) — Tech Stack, Website Requirements, Non-Functional Requirements
- [ADR-0006](0006-cache-revalidation-strategy.md) — how freshness is achieved
- [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — M2, M4, M11, M12
