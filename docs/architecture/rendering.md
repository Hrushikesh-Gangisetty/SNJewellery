# Rendering and Revalidation

Produced by **M4.7**. Implements [ADR-0006](../adr/0006-cache-revalidation-strategy.md), which chose the mechanism; this records what was actually built.

The one-sentence version: **every customer-facing route is static HTML with ISR, every read carries a cache tag, and M9 will invalidate those tags from a database webhook.**

---

## 1 · Per-route rendering

| Route | Mode | Prerendered at build | Revalidate |
|---|---|---|---|
| `/` | Static + ISR | yes | 10 min |
| `/catalogue` | Static + ISR | yes (first page only) | 10 min |
| `/category/[slug]` | Static + ISR | all visible categories | 10 min |
| `/product/[slug]` | Static + ISR | every known slug | 10 min |
| `/_not-found` | Static | yes | — |

No route uses a dynamic function — no `cookies()`, no `headers()`, no `searchParams`. That is what keeps them static, and it is a constraint worth knowing before adding a feature: **reading a search param on any of these routes converts it to per-request rendering and silently discards the cache.** M10's filters must therefore either live on their own route or use a client-side transition.

### `dynamicParams` is left at its default

Both dynamic segments prerender their known slugs and leave `dynamicParams` true. A product or category created after the last build renders on first request and is cached from then on, so **a new piece is visible without a deploy** — which is the entire point of the revalidation path.

Pinning the slug list and setting `dynamicParams = false` would have meant a redeploy per upload, which fails the PRD's one-minute freshness metric outright.

### Enumerating product slugs

`generateStaticParams` for `/product/[slug]` pages through the catalogue via `getAllProductSlugs`, because the data layer offers keyset pagination and deliberately no "give me everything" method. At the real catalogue size — ~500 growing to ~1,000 ([Open Question 3](../../DEVELOPMENT_PLAN.md#risks--open-questions)) — that is twenty to forty queries once per build.

The loop is bounded at 500 pages and throws rather than spinning. An unadvancing cursor would otherwise hang the build, which is far harder to diagnose than a failure.

---

## 2 · Cache tags

Tags are declared once, in `web/lib/data/cache.ts`, and nowhere else. ADR-0006 warns that a mutation whose tag is missing from M9.3's map is a silently stale page, so the vocabulary is a single exported object rather than string literals spread across routes.

| Tag | Applied to | Invalidated by |
|---|---|---|
| `products` | every product list — home rows, catalogue, category pages, related products, the slug list | any product create, update, delete, or status change |
| `categories` | the category list, and `getCategoryBySlug` | category create, rename, reorder, or visibility change |
| `product:<slug>` | one product's detail page and its related list | an edit to that product |
| `category:<slug>` | one category's listing | an edit to that category, or a product moving in or out of it |
| `rates` | today's gold and silver rates | the owner setting a rate |

### Why lists share one tag and details do not

ADR-0006's requirement is that **one product edit invalidates one product's pages, not the whole site**, and M9.3's acceptance criterion tests exactly that.

Detail pages get their own slug tag, so editing one piece leaves every other product page cached. Lists share `products` because they genuinely all change: a new product can appear in the newest row, the catalogue's first page, its category, and a sibling's related list simultaneously. Tagging them individually would be precision that buys nothing and forgets a page.

### The one that is easy to get backwards

`getRelatedProducts` is tagged with the **source** product's slug, not with each related product's slug. Tagging it with every sibling would mean editing any one piece purged its siblings' related lists — `products` already covers that case, and the source slug keeps the entry addressable when the product itself moves category.

---

## 3 · The interval is a fallback, not the mechanism

**10 minutes**, on every cacheable route.

ADR-0006 chose an event-driven webhook as the primary signal precisely so that freshness costs nothing when nothing changes. The interval exists only to bound the damage when a webhook is dropped — a real failure mode for a silent one-way HTTP call.

Ten minutes against a catalogue that changes a few times a day is a handful of wasted rebuilds. The PRD's **one-minute** freshness budget is met by the webhook, not by this number, and **M9.5 tests that by disabling the webhook deliberately.**

Raising it would widen the stale window when the webhook fails; lowering it would spend rebuilds to shorten a window that should not be reached in the first place.

---

## 4 · Why `unstable_cache`

Next's tagging is built on `fetch`. These reads go through supabase-js, which uses its own client, so `fetch` tags never apply. `unstable_cache` is what the App Router documents for attaching a tag to anything that is not a `fetch`, and it is stable in practice on 15.x despite the name.

The consequence worth stating: **pages import from `lib/data/cache.ts`, not from `lib/data` directly.** A page that calls `catalogue.getProductBySlug` still works and still reads through the data boundary — it simply produces an untagged, uncached read that M9 can never invalidate. That is the failure this file exists to prevent, and it is invisible until a page goes stale in production.

The server action behind "Load more" reads through the same cached layer, so a second page of the catalogue is invalidated by the same tag as the first.

---

## 5 · What M9 must do

Nothing here invalidates anything yet — no webhook exists. M9 adds:

| Task | What it needs from this file |
|---|---|
| M9.1 / M9.2 | A secret-protected route calling `revalidateTag` with the names in §2 |
| M9.3 | The mutation → tag map. §2's right-hand column is the draft |
| M9.5 | Confirmation that a dropped webhook degrades to §3's interval rather than to permanent staleness |
| M9.6 | The end-to-end measurement: upload to visible, under 60 seconds |

---

## 6 · Verified

At the time of writing, against the live database:

- `npm run build` reports every route as `○` Static or `●` SSG with a 10 min revalidate — no route fell through to `ƒ` dynamic.
- All eleven categories and all eleven product slugs prerender.
- An unknown slug on either dynamic route still returns 404 rather than a cached empty page.

Re-check the build output whenever a route gains a feature. A route quietly turning dynamic is the regression this design is most exposed to, and the build table is where it shows.
