-- ═══════════════════════════════════════════════════════════════════════
-- Row-level security — THE security boundary for this platform.
--
-- There is no application server between the clients and the database, so
-- these policies are the architecture, not a hardening pass. A mistake
-- here is a data breach, not a bug. See docs/adr/0004.
--
-- The rules, matching the CatalogueSource contract in
-- web/lib/data/source.ts so both implementations behave identically:
--
--   1. Never expose an archived product.
--   2. Never expose a product whose category is hidden, or the category.
--   3. DO expose sold products — they stay visible with a badge.
--   4. Writes require role = 'admin'.
--   5. role is not self-assignable.
--
-- Enabling RLS with no policy denies everything. NOT enabling it exposes
-- everything, and that failure is silent — which is why every table below
-- is enabled explicitly.
-- ═══════════════════════════════════════════════════════════════════════

alter table public.categories     enable row level security;
alter table public.purities       enable row level security;
alter table public.products       enable row level security;
alter table public.product_images enable row level security;
alter table public.users          enable row level security;


-- ── Admin check ───────────────────────────────────────────────────────
-- SECURITY DEFINER is required, not optional: this function reads
-- public.users, and public.users itself has RLS whose policies call this
-- function. Without DEFINER the evaluation recurses infinitely.
--
-- search_path is pinned so a caller cannot shadow `users` with a table of
-- their own and grant themselves admin.
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public, pg_catalog
as $$
  select exists (
    select 1
    from public.users u
    where u.id = auth.uid()
      and u.role = 'admin'
  );
$$;

comment on function public.is_admin() is
  'True when the current session belongs to an admin. SECURITY DEFINER to avoid RLS recursion on public.users; search_path pinned so `users` cannot be shadowed.';

revoke execute on function public.is_admin() from public;
grant execute on function public.is_admin() to anon, authenticated;


-- ═══════════════════════════════════════════════════════════════════════
-- CATEGORIES
-- ═══════════════════════════════════════════════════════════════════════

create policy categories_public_read
  on public.categories
  for select
  to anon, authenticated
  using (is_visible = true);

create policy categories_admin_all
  on public.categories
  for all
  to authenticated
  using (public.is_admin())
  with check (public.is_admin());


-- ═══════════════════════════════════════════════════════════════════════
-- PURITIES
-- Readable by everyone: the public site needs the labels to render specs
-- and filter controls. There is nothing sensitive in "22K Gold".
-- ═══════════════════════════════════════════════════════════════════════

create policy purities_public_read
  on public.purities
  for select
  to anon, authenticated
  using (true);

create policy purities_admin_all
  on public.purities
  for all
  to authenticated
  using (public.is_admin())
  with check (public.is_admin());


-- ═══════════════════════════════════════════════════════════════════════
-- PRODUCTS
-- ═══════════════════════════════════════════════════════════════════════

-- Note what is NOT filtered here: `sold`. Sold pieces stay visible with a
-- badge. Excluding them would be a bug, not caution.
create policy products_public_read
  on public.products
  for select
  to anon, authenticated
  using (
    archived = false
    and exists (
      select 1
      from public.categories c
      where c.id = products.category_id
        and c.is_visible = true
    )
  );

create policy products_admin_all
  on public.products
  for all
  to authenticated
  using (public.is_admin())
  with check (public.is_admin());


-- ═══════════════════════════════════════════════════════════════════════
-- PRODUCT IMAGES
--
-- These must repeat the parent's visibility test rather than assuming
-- nobody will query them directly. A signed-out client CAN select from
-- product_images, so if this policy only checked existence of the parent,
-- an archived or hidden product's photographs would leak — which is
-- exactly the attack M3.7's acceptance criteria require testing.
-- ═══════════════════════════════════════════════════════════════════════

create policy product_images_public_read
  on public.product_images
  for select
  to anon, authenticated
  using (
    exists (
      select 1
      from public.products p
      join public.categories c on c.id = p.category_id
      where p.id = product_images.product_id
        and p.archived = false
        and c.is_visible = true
    )
  );

create policy product_images_admin_all
  on public.product_images
  for all
  to authenticated
  using (public.is_admin())
  with check (public.is_admin());


-- ═══════════════════════════════════════════════════════════════════════
-- USERS
--
-- No policy grants a non-admin any write, so role is not self-assignable:
-- absence of a policy is the denial. Admins may manage accounts because
-- the owner confirmed 3–4 administrators all with full permissions in v1.
-- ═══════════════════════════════════════════════════════════════════════

create policy users_read_self
  on public.users
  for select
  to authenticated
  using (id = auth.uid());

create policy users_admin_read_all
  on public.users
  for select
  to authenticated
  using (public.is_admin());

create policy users_admin_write
  on public.users
  for insert
  to authenticated
  with check (public.is_admin());

create policy users_admin_update
  on public.users
  for update
  to authenticated
  using (public.is_admin())
  with check (public.is_admin());

-- Deliberately no DELETE policy. Removing an account goes through
-- auth.users, which cascades here — deleting the profile row alone would
-- leave an authenticatable user with no role.


-- ═══════════════════════════════════════════════════════════════════════
-- New signups get a profile row automatically.
--
-- Public signup is disabled (config.toml + dashboard), so this fires only
-- for accounts created deliberately by an admin or via the dashboard.
-- Without it, a new admin could authenticate but have no role and would
-- be locked out of every write — a confusing failure to diagnose.
-- ═══════════════════════════════════════════════════════════════════════

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public, pg_catalog
as $$
begin
  insert into public.users (id, email, name, role)
  values (
    new.id,
    new.email,
    coalesce(new.raw_user_meta_data ->> 'name', split_part(new.email, '@', 1)),
    'admin'
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

comment on function public.handle_new_user() is
  'Creates a public.users profile for each new auth user. Defaults to admin because public signup is disabled and every account is created deliberately — see ADR-0004. Revisit when a narrower role is introduced.';

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();
