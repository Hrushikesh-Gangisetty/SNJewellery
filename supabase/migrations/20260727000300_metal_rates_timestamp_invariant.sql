-- ═══════════════════════════════════════════════════════════════════════
-- Makes "updated_at is set if and only if a rate is published" a property
-- of the table rather than something callers have to get right.
--
-- Found immediately after 20260727000200: clearing the placeholder ran an
-- UPDATE, which fired the shared set_updated_at trigger, which stamped
-- updated_at with now() — on rows whose rate had just been set to NULL.
-- The database then claimed a rate had been published today when none
-- ever had.
--
-- Nothing user-visible went wrong: the website requires both a rate and a
-- timestamp before it renders the panel, so it stayed hidden. But the row
-- was lying, and the next person to read it would have believed it.
--
-- The shared trigger is right for every other table and stays as it is.
-- This one table gets a variant that skips the stamp when there is no
-- rate to timestamp, and the CHECK constraint below makes the pairing
-- impossible to break by any other route — including a direct SQL edit.
-- ═══════════════════════════════════════════════════════════════════════

create or replace function public.set_metal_rate_updated_at()
returns trigger
language plpgsql
as $$
begin
  -- An unpublished rate has no update to record. Without this, unsetting
  -- a rate stamps it as though it had just been set.
  if new.rate_per_gram is null then
    new.updated_at = null;
  else
    new.updated_at = now();
  end if;
  return new;
end;
$$;

comment on function public.set_metal_rate_updated_at() is
  'Maintains metal_rates.updated_at. Nulls it when the rate is unpublished, so the timestamp can never outlive the number it describes.';

drop trigger if exists metal_rates_set_updated_at on public.metal_rates;

create trigger metal_rates_set_updated_at
  before update on public.metal_rates
  for each row execute function public.set_metal_rate_updated_at();

-- Repair the rows the previous migration mis-stamped.
update public.metal_rates
   set updated_at = null
 where rate_per_gram is null
   and updated_at is not null;

-- Belt and braces: the trigger maintains this, the constraint guarantees
-- it. A rate with no timestamp is as misleading as a timestamp with no
-- rate, so both halves are rejected.
alter table public.metal_rates
  add constraint metal_rates_timestamp_matches_rate
  check (
    (rate_per_gram is null     and updated_at is null)
    or
    (rate_per_gram is not null and updated_at is not null)
  );
