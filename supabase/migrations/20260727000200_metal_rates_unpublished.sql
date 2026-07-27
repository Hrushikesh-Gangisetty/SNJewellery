-- ═══════════════════════════════════════════════════════════════════════
-- Correction to 20260727000100_metal_rates.sql.
--
-- That migration seeded both rows at a placeholder 1.00 with rate_per_gram
-- NOT NULL, reasoning that the table is meaningless empty. That was wrong,
-- and wrong in the way this project cares about: the Android app does not
-- exist yet, so nobody can update the number, and the site would publish
-- "₹1.00 per gram" to customers until M7 ships. A placeholder rendered to
-- a customer is exactly what ADR-0010 forbids.
--
-- So an unset rate is now NULL, meaning "not published yet", and the
-- website hides the panel entirely until both metals have a real number —
-- the same "null means hide the section cleanly" rule the rest of the
-- configuration follows.
--
-- updated_at goes nullable for the same reason: it is the freshness
-- signal shown to customers, and an insert-time default would claim the
-- rate was set today when nobody has ever set it.
--
-- Forward-only rather than an edit to the previous file, which has
-- already been applied.
-- ═══════════════════════════════════════════════════════════════════════

alter table public.metal_rates
  alter column rate_per_gram drop not null;

alter table public.metal_rates
  alter column updated_at drop not null,
  alter column updated_at drop default;

-- The old constraint rejected NULL only incidentally; state the intent.
alter table public.metal_rates
  drop constraint if exists metal_rates_positive;

alter table public.metal_rates
  add constraint metal_rates_positive
  check (rate_per_gram is null or rate_per_gram > 0);

-- Clear the placeholder. Only rows still sitting at the seeded value are
-- touched, so a rate the owner has genuinely set is never destroyed.
update public.metal_rates
   set rate_per_gram = null,
       updated_at    = null
 where rate_per_gram = 1.00;

comment on column public.metal_rates.rate_per_gram is
  'Rupees per gram. NULL means not published yet — the website hides the rates panel entirely rather than showing a placeholder.';
comment on column public.metal_rates.updated_at is
  'Set by trigger on every update, NULL until the rate has been set once. This is the freshness signal shown to customers, so it must never be written by client code.';
