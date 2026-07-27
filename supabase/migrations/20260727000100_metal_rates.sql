-- ═══════════════════════════════════════════════════════════════════════
-- Today's gold and silver rates.
--
-- Owner's decision, 2026-07-27: per-piece purity and weight leave the
-- customer-facing site, and a daily metal rate takes their place. The
-- owner updates it from the Android app each morning.
--
-- ── Why exactly two rows, fixed ───────────────────────────────────────
-- `purities` is a lookup table precisely so new purities are rows rather
-- than migrations, and the obvious symmetry would be to let the owner
-- add named rates freely. That was offered and declined: the decision is
-- gold and silver, two numbers, because the daily update is done on a
-- phone between customers and every extra field is a cost paid by
-- someone photographing rings (CLAUDE.md §2).
--
-- So `metal` is an enum of exactly two values and the primary key IS the
-- metal. There can never be a third row, a duplicate, or a missing one —
-- the two rows are seeded here and only ever updated. If a third rate is
-- ever wanted, that is a deliberate migration, which is the correct cost
-- for changing what the shop publishes.
--
-- ── Why no history table ──────────────────────────────────────────────
-- Nothing in the PRD or the plan asks to show a rate trend, and an
-- append-only table would need its own read path, retention rule and
-- index for a feature nobody has requested. `updated_at` answers the one
-- question a customer actually has: is this today's number?
-- ═══════════════════════════════════════════════════════════════════════

create type public.metal as enum ('gold', 'silver');

comment on type public.metal is
  'The two metals whose daily rate is published. Adding a third is a deliberate migration — see 20260727000100_metal_rates.sql.';

create table public.metal_rates (
  metal          public.metal primary key,
  -- Rupees per gram. numeric, never float: money must not carry binary
  -- rounding error, and 8,2 comfortably holds a gold rate per gram.
  rate_per_gram  numeric(8, 2) not null,
  updated_at     timestamptz not null default now(),

  -- A zero or negative rate is always a data-entry slip, and it would be
  -- published to customers instantly. Rejected at the boundary rather
  -- than validated in a client that could be bypassed (CLAUDE.md §9.8).
  constraint metal_rates_positive check (rate_per_gram > 0)
);

comment on table public.metal_rates is
  'Today''s gold and silver rate per gram. Exactly two rows, updated daily by the owner from the Android app. Never inserted or deleted by a client.';
comment on column public.metal_rates.rate_per_gram is
  'Rupees per gram. The website displays this with the updated_at timestamp so a customer can see whether it is current.';
comment on column public.metal_rates.updated_at is
  'Set by trigger on every update. This is the freshness signal shown to customers, so it must never be written by client code.';

create trigger metal_rates_set_updated_at
  before update on public.metal_rates
  for each row execute function public.set_updated_at();


-- ── Seed the two rows ─────────────────────────────────────────────────
-- The table is meaningless empty, and the website must never render a
-- half-populated rate panel. Seeded at a placeholder the owner overwrites
-- on first use; `updated_at` makes it obvious the number is stale.
insert into public.metal_rates (metal, rate_per_gram)
values ('gold', 1.00), ('silver', 1.00)
on conflict (metal) do nothing;


-- ═══════════════════════════════════════════════════════════════════════
-- RLS
--
-- Enabled in the same migration as the table, per CLAUDE.md §9.3. A table
-- with RLS and no policy denies everything; a table without RLS exposes
-- everything, and that failure is silent.
--
-- Note the shape of the write policy: UPDATE only. There is no INSERT and
-- no DELETE policy for anyone, admins included, so the two-row invariant
-- is enforced by the policy set rather than by client discipline. An
-- admin can change a rate and cannot destroy the table's shape.
-- ═══════════════════════════════════════════════════════════════════════

alter table public.metal_rates enable row level security;

create policy metal_rates_public_read
  on public.metal_rates
  for select
  to anon, authenticated
  using (true);

create policy metal_rates_admin_update
  on public.metal_rates
  for update
  to authenticated
  using (public.is_admin())
  with check (public.is_admin());
