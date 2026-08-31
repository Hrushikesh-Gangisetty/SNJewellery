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
-- ── Where the URL and the secret come from ────────────────────────────
-- Both are read from database settings at trigger time, NOT written
-- here. A secret in a migration is a secret in git, which CLAUDE.md §9
-- forbids outright. Set them once, per project:
--
--   alter database postgres
--     set app.settings.site_url = 'https://your-domain';
--   alter database postgres
--     set app.settings.revalidation_secret = 'the-same-value-as-vercel';
--
-- The secret must equal REVALIDATION_SECRET in Vercel's environment, or
-- the endpoint answers 401 and every delivery is refused.
--
-- ── Failure is loud in the logs, never in a write ─────────────────────
-- `pg_net` queues the request and returns immediately, so a webhook that
-- cannot be delivered does not slow, block or fail the app's insert. A
-- customer-facing page then stays stale until the ISR interval catches
-- it, which is the documented fallback rather than a broken write.
-- Deliveries are inspectable in `net._http_response`.
-- ═══════════════════════════════════════════════════════════════════════

-- Supabase provisions both in a managed project. Stated explicitly so a
-- fresh database fails here, with a clear cause, rather than at the first
-- trigger fire.
create extension if not exists pg_net with schema extensions;


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
  site_url text := current_setting('app.settings.site_url', true);
  secret   text := current_setting('app.settings.revalidation_secret', true);
begin
  -- Unconfigured is not an error the shop should feel. Skipping leaves
  -- the site on its 10-minute fallback; raising here would make every
  -- product upload fail because a website setting is missing.
  if site_url is null or secret is null then
    raise warning 'revalidate_website: app.settings.site_url or .revalidation_secret unset; skipping';
    return coalesce(new, old);
  end if;

  perform supabase_functions.http_request(
    site_url || '/api/revalidate',
    'POST',
    jsonb_build_object(
      'Content-Type', 'application/json',
      'x-revalidation-secret', secret
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
  'Posts a row change to the website''s /api/revalidate so it can clear the affected cache tags. URL and secret come from database settings — see 20260831000100_revalidation_webhooks.sql.';


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
