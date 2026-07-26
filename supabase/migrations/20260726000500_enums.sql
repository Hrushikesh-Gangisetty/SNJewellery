-- ═══════════════════════════════════════════════════════════════════════
-- Convert two constrained text columns to Postgres enums.
--
-- Found during M3.10's reconciliation of the generated TypeScript types
-- against the hand-written draft contract in web/lib/data/types.ts.
--
-- The draft typed these as unions:
--     aspect: "product" | "product-portrait"
--     role:   "admin" | "staff"
--
-- but a `text` column with a CHECK constraint generates as plain `string`.
-- The constraint was enforced in the database and invisible to the type
-- system, so a typo would compile fine on both clients and fail at
-- runtime — and on Android it would fail on the shop owner's phone,
-- mid-upload.
--
-- Enums put the constraint in both places at once.
--
-- ── Why an enum here and NOT for purity or category ──────────────────
-- Purities and categories are DATA: the owner adds and renames them from
-- the app, and ADR-0010 requires that to need no migration. They are
-- lookup tables for exactly that reason.
--
-- These two are STRUCTURAL. `aspect` is tied to the fixed image ratios in
-- docs/design/responsive.md — adding a third means new CSS, a new token,
-- and new layout rules, so a migration is appropriate and even desirable.
-- `role` gates the security model; adding one requires new RLS policies
-- regardless.
-- ═══════════════════════════════════════════════════════════════════════

-- ── product_images.aspect ─────────────────────────────────────────────
create type public.product_image_aspect as enum ('product', 'product-portrait');

comment on type public.product_image_aspect is
  'Fixed image ratios from docs/design/responsive.md §2. Adding a value requires new CSS, a new token and new layout rules, so a migration is the right gate.';

alter table public.product_images
  drop constraint product_images_aspect_valid;

alter table public.product_images
  alter column aspect drop default;

alter table public.product_images
  alter column aspect type public.product_image_aspect
  using aspect::public.product_image_aspect;

alter table public.product_images
  alter column aspect set default 'product';


-- ── users.role ────────────────────────────────────────────────────────
create type public.user_role as enum ('admin', 'staff');

comment on type public.user_role is
  'admin has full write access. staff is reserved for a future narrower role and is granted nothing yet — see ADR-0004. Adding a role needs new RLS policies anyway, so a migration is the right gate.';

alter table public.users
  drop constraint users_role_valid;

alter table public.users
  alter column role drop default;

alter table public.users
  alter column role type public.user_role
  using role::public.user_role;

alter table public.users
  alter column role set default 'admin';

-- is_admin() and handle_new_user() compare against the literal 'admin',
-- which Postgres coerces to the enum, so neither function needs changing.
-- Verified by the RLS suite in supabase/tests/rls.mjs after this migration.
