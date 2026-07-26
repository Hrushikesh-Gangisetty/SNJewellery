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

Seeded with the launch set: 22K Gold, 18K Gold, Silver.

### `products`

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid` | PK |
| `slug` | `text` | Unique, kebab-case. SEO URLs; generated at upload (M7.11) |
| `name` | `text` | Non-blank |
| `summary` | `text?` | Short form for cards |
| `description` | `text?` | Full form for the product page |
| `category_id` | `uuid` | FK → `categories`, **ON DELETE RESTRICT** |
| `purity_id` | `uuid?` | FK → `purities`, ON DELETE SET NULL |
| `weight_grams` | `numeric(10,2)?` | Grams, shown to customers. CHECK > 0 when present |
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

**The cascade covers rows, not storage objects.** Deleting a product removes its
image rows automatically but leaves the files in the bucket. M8.4 must delete
both explicitly — orphaned objects accumulate silently and cost money.

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

---

## Enums, and why only these two

| Enum | Values |
|---|---|
| `product_image_aspect` | `product`, `product-portrait` |
| `user_role` | `admin`, `staff` |

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
npm run db:test-rls        # 24 adversarial checks vs the live database
cd web && npm run test:data # 38 contract checks vs the data boundary
```

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
4. Update the Kotlin data classes (from M6.6)
5. Update this document
6. `npm run db:test-rls` and `cd web && npm run test:data`

All in one commit. See [CLAUDE.md](../../CLAUDE.md) §3.3.

---

## Open

- **Storage and egress projection** (question 17) — needed before M5 to choose a
  Supabase tier. High-resolution jewellery photography is heavy on both.
- **Full-text search** — M10.1 adds a `tsvector` column and GIN index. Not
  present yet; `tags` is already GIN indexed.
- **pgvector** — M13.4's visual similarity search is the one Phase 3 feature
  needing a migration beyond this contract.
