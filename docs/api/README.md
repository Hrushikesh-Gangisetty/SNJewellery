# API & Data Access

The contracts through which the clients reach data. There is no bespoke REST API — Supabase's generated interface plus a small set of database functions is the API — so what needs documenting is the **data-access layer** each client uses, and the rules governing it.

First produced by **M2.5**, then extended by **M4.1** and **M10.1** in [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md).

## Documents

| Document | Contents | Task |
|---|---|---|
| `data-access.md` | The website's data-access interface: every method, its arguments, and its guarantees | M2.5, M4.1 |
| `queries.md` | The actual queries behind each method, their indexes, and their measured cost | M4.1, M10.7 |
| `functions.md` | Database functions and Edge Functions — search, revalidation | M9.2, M10.1 |

## The interface rule

**No page and no component queries Supabase inline.** Every read goes through the data-access layer.

This is not style preference. M2.5 defines the interface and backs it with **fixtures**, because the website foundation is built before the database exists. M4.1 then swaps in the real Supabase implementation behind the same interface. That swap is only free if nothing bypassed the layer — and M4's acceptance criteria explicitly test it by substituting an empty-data implementation and confirming that empty states render rather than crashes.

See [ADR-0009](../adr/0009-website-first-with-mock-data-adapter.md) for why the build order requires this.

The layer keeps earning its place afterwards: it is where query cost is measured (M10.7), where caching tags are attached (M4.7), and the one place to change when a query needs an index.

## Interface surface

Defined in M2.5, implemented against fixtures immediately and against Supabase in M4.1:

| Method | Returns |
|---|---|
| `getFeaturedProducts` | Products flagged featured, for the home page |
| `getNewestProducts` | Most recently added products |
| `getAllProducts` | The whole catalogue, paginated |
| `getProductsByCategory` | Products in one category, paginated |
| `getProductBySlug` | One product with its ordered images |
| `getRelatedProducts` | Related products, excluding the current one |
| `getVisibleCategories` | Categories where `is_visible`, in `display_order` |
| `getCategoryBySlug` | One visible category, or `null` |
| `getPurities` | Purities in `display_order`. **Not customer-facing since 2026-07-27** — retained because the admin app shares the contract |
| `getMetalRates` | Today's gold and silver rate per gram, gold first. Always both metals; an unpublished rate is a null value, never a missing row |

M10 extends this with search and filtering. Every method must respect RLS — a hidden category's or archived product's rows must never appear in any result, and M10.10 verifies that.

## Client-callable surface

Added by **M4.3**, in `web/lib/data/actions.ts`.

The catalogue appends the next page in place rather than navigating, so the browser has to be able to ask for one. It does that through a **server action**, not by querying Supabase from the client — which keeps supabase-js out of the client bundle (≈58 kB) and keeps the read inside the layer.

| Action | Wraps |
|---|---|
| `fetchMoreProducts` | `getAllProducts` or `getProductsByCategory`, depending on whether a category slug is given |

Two rules follow from the arguments being attacker-controlled:

- **Every argument is validated or bounded.** An unknown or hidden slug yields an empty page; an unrecognisable or crafted cursor yields an empty page. Page size is fixed at the default rather than accepted from the caller.
- **Nothing client-side imports `catalogue`.** Importing the `@/lib/data` barrel from a client component pulls the active source, and therefore the whole Supabase SDK, into the browser. Client components import pure helpers and types from their own modules (`lib/data/alt`, `lib/data/types`); everything else goes through an action.

## The draft-contract sequence

M2.5 hand-writes the domain types before the database exists. This is deliberate, not a shortcut:

1. **M2.5** — hand-write the types, build UI against them. Writing UI against types exercises them and surfaces missing fields early.
2. **M3.2–M3.5** — write migrations matching those types.
3. **M3.10** — generate types from the real schema and reconcile against the draft, resolving **every** difference deliberately and recording why in the commit.

Step 3 is the checkpoint. If reconciliation reveals a large gap, that is a real finding about the schema, not a formality to rubber-stamp.

## Android

The Android app talks to Supabase through its repository layer — see [ADR-0007](../adr/0007-android-architecture.md). Its models mirror the same schema contract as the website's generated types, and both are updated by any migration in the same change. See [docs/database/](../database/).
