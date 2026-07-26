-- ═══════════════════════════════════════════════════════════════════════
-- Core catalogue tables.
--
-- This is the FROZEN SCHEMA CONTRACT that both clients code against —
-- the website via generated TypeScript types, the Android app via
-- hand-written Kotlin data classes. A change here is only complete when
-- both are updated in the same commit. See docs/database/.
--
-- The shape mirrors web/lib/data/types.ts, which was written first and
-- exercised by real UI before these migrations existed (ADR-0009). That
-- is why the columns below already account for fields the PRD's Database
-- Design section omitted.
-- ═══════════════════════════════════════════════════════════════════════

-- gen_random_uuid()
create extension if not exists pgcrypto;


-- ── Shared trigger ────────────────────────────────────────────────────
-- updated_at is maintained by the database, never by client code, so it
-- cannot be forgotten or spoofed.
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;


-- ── Categories ────────────────────────────────────────────────────────
-- Owner-managed from the Android app (M8.6). Deliberately data, not an
-- enum: the final category list is still being decided and must be
-- changeable without a migration (ADR-0010).
create table public.categories (
  id            uuid primary key default gen_random_uuid(),
  slug          text not null unique,
  name          text not null,
  display_order integer not null default 0,
  is_visible    boolean not null default true,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),

  constraint categories_slug_format
    check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  constraint categories_name_not_blank
    check (length(btrim(name)) > 0)
);

comment on table public.categories is
  'Catalogue categories. Hidden categories and all their products are invisible to customers — enforced by RLS, not by client filtering.';
comment on column public.categories.is_visible is
  'False hides the category AND every product in it from the public site.';


-- ── Purities ──────────────────────────────────────────────────────────
-- A lookup table rather than an enum or a CHECK constraint, because the
-- owner asked for new purities to be addable without a schema change.
-- Adding one is an INSERT; a CHECK constraint would need a migration.
create table public.purities (
  id            uuid primary key default gen_random_uuid(),
  code          text not null unique,
  label         text not null,
  display_order integer not null default 0,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),

  constraint purities_code_not_blank check (length(btrim(code)) > 0)
);

comment on table public.purities is
  'Gold and silver purities. A lookup table so a new purity is a row, not a migration. At launch: 22K Gold, 18K Gold, Silver.';
comment on column public.purities.code is
  'Short form shown on cards and used in image alt text: 22K, 18K, Silver.';
comment on column public.purities.label is
  'Full form shown on the product page: "22K Gold".';


-- ── Products ──────────────────────────────────────────────────────────
create table public.products (
  id           uuid primary key default gen_random_uuid(),
  slug         text not null unique,
  name         text not null,
  summary      text,
  description  text,

  -- RESTRICT, not CASCADE: deleting a category that still holds products
  -- must fail loudly rather than silently destroying the catalogue.
  -- M8.8 handles this case in the admin UI.
  category_id  uuid not null,
  purity_id    uuid,

  weight_grams numeric(10, 2),

  colours      text[] not null default '{}',
  tags         text[] not null default '{}',

  featured     boolean not null default false,
  sold         boolean not null default false,
  archived     boolean not null default false,

  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),

  constraint products_slug_format
    check (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
  constraint products_name_not_blank
    check (length(btrim(name)) > 0),
  -- Weight is optional, but a recorded weight must be positive.
  constraint products_weight_positive
    check (weight_grams is null or weight_grams > 0),

  constraint products_category_fk
    foreign key (category_id) references public.categories (id)
    on delete restrict,
  constraint products_purity_fk
    foreign key (purity_id) references public.purities (id)
    on delete set null
);

comment on table public.products is
  'The catalogue. Nothing is sold online; these exist to bring a customer into the shop.';

-- Fields the PRD's feature list requires but its Database Design section
-- omitted. Each is commented with the requirement that drives it, so a
-- future reader does not mistake them for accidents.
comment on column public.products.tags is
  'PRD: "Search by: Name, Category, Tags" and the Add Product form Tags field. Searchable in M10.';
comment on column public.products.archived is
  'PRD Product Management lists Archive as distinct from Delete and from Mark Sold. Archived products are hidden from customers but remain in the admin app.';
comment on column public.products.slug is
  'SEO-friendly URLs and canonical links (M11). Generated at upload with uniqueness guaranteed (M7.11).';
comment on column public.products.colours is
  'PRD Product Details: "Available colours (optional)".';

comment on column public.products.sold is
  'Sold products STAY VISIBLE to customers with a "Sold" badge — the owner''s decision. They are portfolio evidence of what the shop makes. Distinct from archived.';
comment on column public.products.weight_grams is
  'Grams. Shown to customers — confirmed by the owner. Nullable because not every piece is sold by weight.';

comment on constraint products_category_fk on public.products is
  'ON DELETE RESTRICT — a non-empty category cannot be deleted. See M8.8.';


-- ── Product images ────────────────────────────────────────────────────
create table public.product_images (
  id            uuid primary key default gen_random_uuid(),
  product_id    uuid not null
                  references public.products (id) on delete cascade,
  url           text not null,
  storage_path  text not null,
  display_order integer not null default 0,
  -- Long pieces are ruined by a square crop, so the owner picks portrait
  -- at upload. See docs/design/responsive.md §2.
  aspect        text not null default 'product',
  created_at    timestamptz not null default now(),

  constraint product_images_aspect_valid
    check (aspect in ('product', 'product-portrait')),
  -- One image per position per product, so gallery order is unambiguous.
  constraint product_images_unique_order
    unique (product_id, display_order)
);

comment on table public.product_images is
  'One row per photograph. Deleting a product cascades here, but storage objects must be removed explicitly — see M8.4 and ADR-0005.';
comment on column public.product_images.storage_path is
  'Path within the product-images bucket, kept so thumbnail and mobile renditions can be derived. Never constructed ad hoc (ADR-0005).';
comment on column public.product_images.display_order is
  'Zero-based. Position 0 is the primary image used on cards and social previews.';


-- ── Users ─────────────────────────────────────────────────────────────
-- Admin accounts, mirroring auth.users. The owner expects 3–4
-- administrators, all with full permissions in v1, and asked that
-- role-based restrictions be addable later without major change.
--
-- 'staff' is included in the constraint now so introducing a narrower
-- role later needs only new policies, not a constraint migration. It is
-- granted nothing today.
create table public.users (
  id         uuid primary key references auth.users (id) on delete cascade,
  name       text,
  email      text,
  role       text not null default 'admin',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),

  constraint users_role_valid check (role in ('admin', 'staff'))
);

comment on table public.users is
  'Admin accounts. Row-level security is the security boundary for the whole platform — see docs/adr/0004-authentication-and-roles.md.';
comment on column public.users.role is
  'admin has full write access. staff is reserved for a future narrower role and is granted nothing yet; it exists in the constraint so adding it later needs no constraint migration.';


-- ── updated_at triggers ───────────────────────────────────────────────
create trigger categories_set_updated_at
  before update on public.categories
  for each row execute function public.set_updated_at();

create trigger purities_set_updated_at
  before update on public.purities
  for each row execute function public.set_updated_at();

create trigger products_set_updated_at
  before update on public.products
  for each row execute function public.set_updated_at();

create trigger users_set_updated_at
  before update on public.users
  for each row execute function public.set_updated_at();
