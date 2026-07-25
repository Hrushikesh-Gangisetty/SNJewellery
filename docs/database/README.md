# Database

The schema contract, the migration workflow, and the row-level security model.

Produced by **M3** in [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md). The SQL itself will live in `supabase/` at the repository root (created in M0.2) — this directory documents it.

## The schema contract

M3 freezes a schema that **two clients in two languages** code against. Changing it after M4 and M7 begin means coordinated rework in TypeScript and Kotlin, so it is designed once, deliberately, and treated as a contract afterwards.

The rule that follows: **a migration is never complete until the generated TypeScript types and the Kotlin data classes both match it, in the same change.** One-sided schema changes are how the two clients silently diverge.

## Documents

| Document | Contents | Task |
|---|---|---|
| `schema.md` | The frozen contract: every table, column, constraint, and index, with the reasoning | M3.11 |
| `migrations.md` | How to write, apply, and review a migration; the both-clients rule | M3.11 |
| `rls.md` | The policy model, what each role may do, and the adversarial checks that prove it | M3.7, M3.11 |

## Tables

Four tables, per the PRD's Database Design section:

| Table | Purpose |
|---|---|
| `categories` | The eleven catalogue categories, with display order and visibility |
| `products` | The catalogue itself |
| `product_images` | One row per photograph, ordered within a product |
| `users` | Admin accounts, linked to `auth.users`, carrying `role` |

## Fields the PRD's schema section omits

The PRD's Database Design section and its feature list disagree — four fields are required by the features but absent from the schema. M3.4 adds them, each with a migration comment citing the requirement that drives it:

| Field | Required by |
|---|---|
| `products.tags` | "Search by: Name, Category, Tags" and the Add Product form's Tags field |
| `products.archived` | Product Management lists **Archive** as distinct from **Delete** and from **Mark Sold** |
| `products.slug` | SEO-friendly URLs and canonical links (M11) |
| `products.colours` | Product Details lists "Available colours (optional)" |

This is worth flagging as a PRD gap rather than silently patching: if any of these four is not actually wanted, say so before M3.4.

## Security model

Row-level security is the security boundary for the whole platform — see [ADR-0004](../adr/0004-authentication-and-roles.md). In summary:

- **Anonymous** — `SELECT` only, and only products that are not archived and whose category is visible.
- **Admin** — full write on products, images, and categories.
- **Storage** — public read of the product bucket, admin-only write.
- **`users`** — self-read only. `role` is not self-assignable.

M3.7's acceptance criteria include adversarial checks: that a signed-out client cannot reach a hidden category's products by querying `product_images` directly, and that a non-admin cannot escalate their own role. Policies that have not been attacked have not been tested.

## Open questions affecting this schema

- **Open Question 4** — single admin or multiple users with different permissions. Decides how much `users.role` must carry. Far cheaper to design for now than to retrofit.
- **Open Question 6** — whether `sold` products stay visible with a badge or disappear. Changes both the queries in M4.1 and the policies here.
- **Open Question 3** — real initial catalogue size, which decides whether M10's indexing is needed on day one.
- **M13.4** — visual similarity search is the one Phase 3 feature requiring a migration beyond this frozen contract: pgvector plus an embedding column, plus a backfill.
