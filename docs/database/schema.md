# Schema Contract

**Status:** frozen as of 2026-07-26. Applied to the development project
`SNJewellery` and verified — see [Verification](#verification).

This is the contract **two clients in two languages** code against. A change is
only complete when the migration, the generated TypeScript types, and the Kotlin
data classes all change together, in one commit. One-sided schema changes are
how the clients silently diverge.

SQL lives in [`supabase/migrations/`](../../supabase/migrations/).

---

## Tables

### `categories`
Owner-managed from the Android app (M8.6). Data, not an enum — the final list is
still being decided and must be changeable without a migration ([ADR-0010](../adr/0010-configurable-site-content.md)).

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | PK |
| `slug` | `text` | Unique. Lowercase kebab-case, enforced by CHECK |
| `name` | `text` | Non-blank, enforced |
| `display_order` | `integer` | Owner reorders by drag (M8.7) |
| `is_visible` | `boolean` | **False hides the category AND every product in it** |
| `created_at` / `updated_at` | `timestamptz` | `updated_at` by trigger |

### `purities`
A **lookup table, not a constraint** — the owner asked for new purities to be
addable without a schema change. Adding platinum or 14K is one `INSERT`.

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | PK |
| `code` | `text` | Unique. Short form: `22K`, `18K`, `Silver` |
| `label` | `text` | Full form: "22K Gold" |
| `display_order` | `integer` | |
| `created_at` / `updated_at` | `timestamptz` | `updated_at` by trigger |

Seeded with the launch set: 22K Gold, 18K Gold, Silver.

**Recorded, not displayed since 2026-07-27.** The owner removed per-piece purity and weight from the customer-facing site in favour of the daily `metal_rates` below. The table, the FK and the column all stay: the Android app still captures purity, and putting it back on the site is a UI change rather than a migration. M10's purity filter is deferred with it.

### `products`

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | PK |
| `slug` | `text` | Unique, kebab-case. SEO URLs; generated at upload (M7.11) |
| `name` | `text` | Non-blank |
| `summary` | `text?` | Short form for cards |
| `description` | `text?` | Full form for the product page |
| `category_id` | `uuid` | FK → `categories`, **ON DELETE RESTRICT** |
| `purity_id` | `uuid?` | FK → `purities`, ON DELETE SET NULL. **Recorded, not displayed** |
| `weight_grams` | `numeric(10,2)?` | Grams. CHECK > 0 when present. **Recorded, not displayed** — see `purities` |
| `colours` | `text[]` | Optional available colours |
| `tags` | `text[]` | Searchable in M10. GIN indexed |
| `featured` | `boolean` | Home page |
| `sold` | `boolean` | **Stays visible with a badge** |
| `archived` | `boolean` | **Hidden from customers, kept in the app** |
| `created_at` / `updated_at` | `timestamptz` | |

**`ON DELETE RESTRICT` on `category_id` is deliberate.** Deleting a category that
still holds products fails loudly rather than silently destroying the catalogue.
M8.8 handles the case in the admin UI.

### `product_images`

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | PK |
| `product_id` | `uuid` | FK → `products`, **ON DELETE CASCADE** |
| `url` | `text` | Public CDN URL |
| `storage_path` | `text` | Path in the bucket, so renditions can be derived ([ADR-0005](../adr/0005-image-storage-and-renditions.md)) |
| `display_order` | `integer` | Zero-based. **Unique per product.** Position 0 is primary |
| `aspect` | `product_image_aspect` | Enum: `product` \| `product-portrait` |
| `created_at` | `timestamptz` | No `updated_at`: an image row is replaced, never edited |

**The cascade covers rows, not storage objects.** Deleting a product removes its
image rows automatically but leaves the files in the bucket. M8.4 must delete
both explicitly — orphaned objects accumulate silently and cost money.

### `metal_rates`
Today's gold and silver rate per gram, updated by the owner each morning from
the Android app. Added 2026-07-27, replacing per-piece purity and weight on the
customer-facing site.

**Exactly two rows, permanently.** The metal is the primary key and it is an enum
of two values, so a third rate, a duplicate, or a missing row are all impossible.
There is deliberately **no INSERT and no DELETE policy for anyone, admins
included** — the shape of the table is not something a client can change. Adding
a third metal is a migration, which is the right cost for changing what the shop
publishes.

| Column | Type | Notes |
|---|---|---|
| `metal` | `metal` | PK. Enum: `gold` \| `silver` |
| `rate_per_gram` | `numeric(8,2)?` | Rupees per gram. **NULL means not published yet** |
| `updated_at` | `timestamptz?` | By trigger. NULL while unpublished |

`rate_per_gram` and `updated_at` are null or non-null **together**, enforced by
both a trigger and a CHECK constraint. A timestamp that outlives the number it
describes would tell a customer a stale rate was set this morning, so the pairing
is a property of the table rather than something callers have to remember.

The website hides the rates panel entirely until both metals are published — a
placeholder rate is exactly what [ADR-0010](../adr/0010-configurable-site-content.md)
forbids.

### `users`
Mirrors `auth.users`. A row is created automatically by the
`on_auth_user_created` trigger, defaulting to `admin`, because public signup is
disabled and every account is created deliberately.

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | PK, FK → `auth.users`, ON DELETE CASCADE |
| `name` | `text?` | |
| `email` | `text?` | |
| `role` | `user_role` | Enum: `admin` \| `staff` |
| `created_at` / `updated_at` | `timestamptz` | `updated_at` by trigger |

---

## Enums, and why only these two

| Enum | Values |
|---|---|
| `product_image_aspect` | `product`, `product-portrait` |
| `user_role` | `admin`, `staff` |
| `metal` | `gold`, `silver` |

The distinction that decides enum vs lookup table:

- **Purities and categories are data.** The owner adds and renames them from the
  app, and ADR-0010 requires that to need no migration. Lookup tables.
- **`aspect` and `role` are structural.** Adding an aspect means new CSS, a new
  token, and new layout rules; adding a role means new RLS policies. A migration
  is the right gate, and the enum makes the constraint visible to both clients'
  type systems.

`staff` is granted nothing today. It exists so introducing a narrower role later
needs new policies rather than a type migration.

---

## Fields the PRD's schema section omitted

The PRD's Database Design section and its feature list disagreed. Four fields
are required by the features but absent from the schema, and each carries a SQL
comment citing the requirement that drives it:

| Field | Required by |
|---|---|
| `products.tags` | "Search by: Name, Category, Tags" and the Add Product form |
| `products.archived` | Product Management lists Archive as distinct from Delete and Mark Sold |
| `products.slug` | SEO URLs and canonical links (M11) |
| `products.colours` | Product Details: "Available colours (optional)" |

---

## Security model

Row-level security is the security boundary for the whole platform —
[ADR-0004](../adr/0004-authentication-and-roles.md). Five rules, matching the
`CatalogueSource` contract in `web/lib/data/source.ts` so both implementations
behave identically:

1. Never expose an archived product.
2. Never expose a product whose category is hidden, or the category.
3. **DO expose sold products** — they stay visible with a badge.
4. Writes require `role = 'admin'`.
5. `role` is not self-assignable.

Two implementation details that are easy to get wrong:

**`is_admin()` must be `SECURITY DEFINER`.** It reads `public.users`, whose own
policies call it — without `DEFINER` the evaluation recurses infinitely. Its
`search_path` is pinned so a caller cannot shadow `users` with their own table
and grant themselves admin.

**`product_images` repeats the parent's visibility test.** It does not assume
nobody queries it directly. Without that repetition, an anonymous client could
read an archived product's photographs straight from the images table.

---

## Verification

Two suites, both run against real systems rather than asserted:

```bash
npm run db:check-contract   # TypeScript and Kotlin describe the same tables
npm run db:test-rls         # 24 adversarial checks vs the live database
cd web && npm run test:data # 38 contract checks vs the data boundary
```

`db:check-contract` compares the generated TypeScript types — which come
from the live database, so they are the authority — against the hand-written
Kotlin models, which are what drifts. It checks table sets, column sets,
nullability and enum values. It deliberately does not check types, because
the mapping is intentional and documented in `SchemaContract.kt`
(`uuid`→`String`, `timestamptz`→`String`, `numeric`→`Double`).

Added in M6.6 because **nothing enforced §3.3 of CLAUDE.md before it**: a
column added to Postgres and to the website but forgotten in Kotlin
produced no error on either side until a row failed to deserialise on a
phone. It found three genuine omissions in this document on its first run —
`purities` and `users` timestamps, and `product_images.created_at`.

`db:test-rls` uses **only the anon key** and attacks the policies: it tries to
read archived products, reach hidden products' images directly, un-hide a
category, un-archive a product, and grant itself a role — then confirms the
target rows are unchanged afterwards.

### One thing to know before reading its output

**RLS denial does not always surface as an HTTP error.**

| Operation | What denial looks like |
|---|---|
| `INSERT` | `WITH CHECK` rejects the row → **401/403** |
| `UPDATE` / `DELETE` | No policy matches, so zero rows are visible to the statement → **204 No Content**, a *success* status |

A 204 on an UPDATE means "your write matched nothing" — correct behaviour that
looks like success. Any test asserting only on status codes will either raise a
false alarm or, worse, give false confidence. The suite requests affected rows
back with `Prefer: return=representation` and asserts the set is empty.

---

## Changing this schema

1. Write a new migration in `supabase/migrations/`. Never edit an applied one.
2. `npm run db:push`
3. `npm run db:types` — regenerates `web/lib/data/database.types.ts`
4. Update the Kotlin data classes in
   [`android/.../data/models/SchemaContract.kt`](../../android/app/src/main/java/com/snjewellery/admin/data/models/SchemaContract.kt)
5. Update this document
6. `npm run db:check-contract` — fails if the two clients disagree
7. `npm run db:test-rls` and `cd web && npm run test:data`

All in one commit. See [CLAUDE.md](../../CLAUDE.md) §3.3.

---

## Open

- **Storage and egress projection** (question 17) — needed before M5 to choose a
  Supabase tier. High-resolution jewellery photography is heavy on both.
- **Full-text search** — M10.1 adds a `tsvector` column and GIN index. Not
  present yet; `tags` is already GIN indexed.
- **pgvector** — M13.4's visual similarity search is the one Phase 3 feature
  needing a migration beyond this contract.
