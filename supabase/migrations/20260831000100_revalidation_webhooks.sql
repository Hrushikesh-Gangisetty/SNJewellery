-- ═══════════════════════════════════════════════════════════════════════
-- Database webhooks that tell the website a row changed.
--
-- Implements the configuration half of ADR-0006 and
-- docs/architecture/sync.md §5. Until this ran, every mutation reached
-- the site only through the 10-minute ISR fallback — which is what made
-- three uploaded products invisible on a site that went on serving two
-- deleted ones.
--
-- ── Why a migration rather than the dashboard ─────────────────────────
-- The dashboard's "Database Webhooks" screen creates exactly these
-- triggers; it is a form over `supabase_functions.http_request`. Three
-- tables x three events clicked by hand is three chances to miss one,
-- and a missed table fails SILENTLY: `product_images` in particular
-- would leave photo reorders never reaching the site, with nothing to
-- show for it. Written here, the set is reviewable, reproducible, and
-- recreated with the rest of the schema. Same reason the storage bucket
-- is a migration (20260726000400_storage.sql) rather than a dashboard
-- click.
--
-- ── Why a table and not `app.settings.*` ──────────────────────────────
-- The obvious home for two config values is a database parameter set
-- with `alter database … set`. A managed Supabase project refuses it:
-- the SQL Editor's role is not superuser, so that statement fails with
-- 42501 `permission denied to set parameter`. A table is the portable
-- alternative, and it brings something the parameter did not — the
-- secret can be put behind RLS, which is the boundary this project
-- already trusts everywhere else (CLAUDE.md §3.2).
-- ═══════════════════════════════════════════════════════════════════════

-- Supabase provisions this in a managed project. Stated explicitly so a
-- fresh database fails here, with a clear cause, rather than at the first
-- trigger fire.
create extension if not exists pg_net with schema extensions;


-- ── Where the URL and the secret live ─────────────────────────────────
-- One row, enforced by a primary key over a constant. Two columns rather
-- than a key/value bag: there are exactly two settings, they are both
-- required, and `not null` then says so for free.
create table if not exists public.website_config (
  id                  boolean primary key default true,
  -- Origin, no trailing slash. `/api/revalidate` is appended.
  site_url            text not null,
  -- Must equal REVALIDATION_SECRET in Vercel, or the endpoint answers
  -- 401 and every delivery is refused.
  revalidation_secret text not null,
  updated_at          timestamptz not null default now(),

  constraint website_config_single_row check (id),
  constraint website_config_site_url_absolute
    check (site_url ~ '^https?://' and site_url !~ '/$')
);

comment on table public.website_config is
  'Single row. Where the revalidation webhook posts, and the secret it presents. Not readable by any client — see the RLS note in 20260831000100_revalidation_webhooks.sql.';


-- ── RLS: enabled, with NO policy, deliberately ────────────────────────
-- This table holds a shared secret, so the correct number of policies is
-- zero. RLS on with no policy denies every anon and authenticated
-- request — the pattern 20260726000300_rls_policies.sql already relies on
-- for `users` deletes. The trigger function still reads it because it is
-- SECURITY DEFINER and RLS does not apply to the definer's own access.
--
-- The website never reads this table either; it gets its copy of the
-- secret from Vercel's environment.
alter table public.website_config enable row level security;

revoke all on public.website_config from anon, authenticated;


-- ── The trigger function ──────────────────────────────────────────────
-- `supabase_functions.http_request` is what the dashboard's own webhooks
-- call. Reusing it rather than hand-rolling a `net.http_post` keeps the
-- payload shape identical to the one the dashboard produces — which is
-- the shape `parseWebhookPayload` in web/lib/data/revalidate.ts already
-- parses and has tests for.
--
-- The arguments are positional and fixed by that function's signature:
--   url, method, headers, params, timeout_ms
create or replace function public.revalidate_website()
returns trigger
language plpgsql
security definer
set search_path = public, extensions, pg_catalog
as $$
declare
  config public.website_config%rowtype;
begin
  select * into config from public.website_config where id;

  -- Unconfigured is not an error the shop should feel. Skipping leaves
  -- the site on its 10-minute fallback; raising here would make every
  -- product upload fail because a website setting is missing.
  if not found then
    raise warning 'revalidate_website: website_config is empty; skipping';
    return coalesce(new, old);
  end if;

  perform supabase_functions.http_request(
    config.site_url || '/api/revalidate',
    'POST',
    jsonb_build_object(
      'Content-Type', 'application/json',
      'x-revalidation-secret', config.revalidation_secret
    )::text,
    '{}',
    '5000'
  );

  -- AFTER trigger: the value is ignored, but a plpgsql trigger must
  -- return. `old` for DELETE, `new` otherwise.
  return coalesce(new, old);
end;
$$;

comment on function public.revalidate_website() is
  'Posts a row change to the website''s /api/revalidate so it can clear the affected cache tags. Reads its URL and secret from public.website_config.';

-- Nothing calls this directly; it runs as a trigger, under the definer's
-- rights. Leaving it executable would be a way to make the database emit
-- an authenticated request on demand.
revoke execute on function public.revalidate_website() from public, anon, authenticated;


-- ── The three tables ──────────────────────────────────────────────────
-- All three, and all three events on each. The mapping from a changed
-- row to the tags it clears is the website's job (lib/data/revalidate.ts);
-- the database's job is only to say that something changed.
--
--   products        — a piece added, edited, deleted, or a flag toggled
--   categories      — renamed, reordered, or hidden
--   product_images  — photographs added, removed or REORDERED, which is
--                     the one a hand-clicked setup usually forgets

drop trigger if exists revalidate_on_products on public.products;
create trigger revalidate_on_products
  after insert or update or delete on public.products
  for each row execute function public.revalidate_website();

drop trigger if exists revalidate_on_categories on public.categories;
create trigger revalidate_on_categories
  after insert or update or delete on public.categories
  for each row execute function public.revalidate_website();

drop trigger if exists revalidate_on_product_images on public.product_images;
create trigger revalidate_on_product_images
  after insert or update or delete on public.product_images
  for each row execute function public.revalidate_website();


-- ── Configuring it ────────────────────────────────────────────────────
-- Run once per project, in the SQL Editor, with the real values. The
-- secret is NOT in this migration and must never be committed here
-- (CLAUDE.md §9) — it comes from Vercel's REVALIDATION_SECRET.
--
--   insert into public.website_config (id, site_url, revalidation_secret)
--   values (true, 'https://your-domain', 'the-value-from-vercel')
--   on conflict (id) do update
--     set site_url            = excluded.site_url,
--         revalidation_secret = excluded.revalidation_secret,
--         updated_at          = now();
