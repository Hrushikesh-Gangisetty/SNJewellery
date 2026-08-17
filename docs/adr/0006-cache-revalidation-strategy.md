# ADR-0006: Cache revalidation strategy

- **Status:** ✅ **Accepted** — 2026-07-25
- **Date:** 2026-07-25
- **Deciders:** Hrushikesh Gangisetty
- **Affects:** M4.7, M9

## Context

The PRD's headline success metric is that **a product is visible online within one minute of upload**. The website is statically generated with ISR ([ADR-0002](0002-nextjs-app-router.md)), so something must tell it that a page is stale. The Android app writes directly to Supabase and never talks to the website, so the signal has to originate from the database or from the app.

What makes this non-obvious: the cheap answer — a short ISR interval on every page — technically satisfies "within one minute" but rebuilds every page continuously whether or not anything changed, and still leaves a window where a customer sees stale content. The precise answer costs a webhook and a secured endpoint.

The freshness budget is one minute, not real-time. That is a generous budget, and it rules out the more elaborate options.

Three mechanisms are available:

1. **Database webhook → secured revalidation route.** A Postgres trigger or Supabase webhook fires on mutation and calls a Next.js route that runs `revalidateTag`.
2. **Supabase Realtime subscription.** The website subscribes to changes.
3. **Time-based ISR only.** No signal; short revalidation intervals.

## Decision

**Accepted:** option 1 — **a Supabase database webhook calling a secret-protected Next.js revalidation route**, with tag-scoped invalidation and a **conservative ISR interval as a fallback safety net**.

The recommendation rests on three things:

- **It is event-driven, so freshness costs nothing when nothing changes.** A catalogue that changes a few times a day should not rebuild continuously.
- **Tag scoping means one product edit invalidates one product's pages**, not the whole site. M9.3 defines the mutation-to-tag map, and M9.3's acceptance criterion verifies unrelated pages stay cached.
- **The fallback matters more than the primary.** A webhook can fail silently; a site that is permanently stale because one HTTP call was dropped is a worse failure than a slightly slower one. The ISR interval bounds the damage, and M9.5's criterion tests exactly this by disabling the webhook deliberately.

Realtime is rejected for this purpose because it requires a persistent client connection on a statically generated site — solving a build-time cache problem with a runtime client mechanism.

## Consequences

### What this makes easier

- Near-immediate freshness with no wasted rebuilds.
- A single, inspectable, testable path — which is what makes M9.6's measurement meaningful.
- The endpoint can be exercised manually when diagnosing a stale page (M9.7).

### What this makes harder

- **A new public endpoint exists**, so it must be secret-protected and idempotent (M9.2). Webhooks retry; a non-idempotent handler will double-work.
- Two systems must agree on tag names. A mutation whose tag is missing from the map is a silently stale page — which is why the map is documented rather than implicit.
- Local development does not receive production webhooks, so revalidation needs a manual trigger path in development.

### What this commits us to

Cache tags must be placed correctly during M4.7, before M9 exists. Retrofitting tags across an already-built site is more work than placing them as pages are written — which is why M4.7 requires every cacheable route to carry a tag even though nothing invalidates them yet.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Supabase Realtime | Requires a persistent client connection to fix a build-time cache. Genuinely useful if the *app* ever needs live updates — reconsider then, for that purpose. |
| Time-based ISR only | Simplest, and the fallback anyway. As the primary it wastes rebuilds continuously and still leaves a stale window. |
| Android app calls the revalidation endpoint directly | Ties freshness to client success. A dashboard edit, a SQL fix, or a failed request leaves the site stale. The database is the more reliable source of the signal. |
| Fully dynamic rendering, no cache | Removes the problem and the performance target with it. Fails the sub-two-second goal on an image-heavy catalogue. |

## Open sub-questions

All four are now settled. The implementation is documented in [docs/architecture/sync.md](../architecture/sync.md).

- ~~The mechanism itself~~ — **decided 2026-07-25: webhook + `revalidateTag`.**
- ~~The fallback ISR interval~~ — **decided 2026-08-17 (M9.5): 600 seconds.** Ten minutes wastes almost no rebuilds on a catalogue edited a few times a day, and bounds a dropped webhook to an annoyance. `REVALIDATE_SECONDS` in `web/lib/data/cache.ts` is the single source; the endpoint imports it rather than restating the number.
- ~~Whether image reordering and status toggles need distinct tags~~ — **decided 2026-08-17 (M9.3): they share the product-level tag.** A `product_images` row carries `product_id` and no slug, so a slug-scoped tag would need a database query on a path that must not fail. The cost is that a reorder updates cards immediately but the detail gallery may wait for the interval. Revisit if that becomes a real complaint.
- ~~Whether to alert on revalidation failure~~ — **decided 2026-08-17 (M9.5): log only, no alerting.** Every delivery logs one line, success or failure, and the failure line names the tags and the fallback interval. Alerting on a failure that self-corrects within ten minutes, on a site with one maintainer, would be noise. The success line matters as much as the failure one: diagnosing a stale page starts with "did the webhook arrive at all", and only a success log answers that.

## References

- [prd.md](../../prd.md) — Success Metrics: "New products visible online within one minute of upload"
- [ADR-0002](0002-nextjs-app-router.md) — the rendering strategy this serves
- [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — M4.7, M9
