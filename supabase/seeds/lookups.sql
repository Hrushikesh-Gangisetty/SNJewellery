-- ═══════════════════════════════════════════════════════════════════════
-- The two lookup tables, with the shop's real values.
--
-- Split out of seed.sql in M5.1, because the two halves have opposite
-- lifetimes and only one of them may ever touch production:
--
--   • THIS FILE is real data. The purities the shop quotes and the
--     categories the PRD names. It runs against local development AND
--     against a fresh production project.
--   • seed.sql is placeholder jewellery for developing M4. It must never
--     reach production, and says so at the top of itself.
--
-- Applied locally by `supabase db reset` (config.toml lists it first) and
-- to production once, by hand:
--
--   npx supabase db query --linked -f supabase/seeds/lookups.sql
--
-- ── Idempotent, and that is the point ────────────────────────────────
-- `on conflict do nothing`, keyed on the unique columns. Re-running this
-- against a project that already has these rows changes nothing — it will
-- not reset a category the owner has since renamed, reordered or hidden
-- from the app, which after launch is the only way this file could do
-- damage. It adds what is missing and leaves everything else alone.
-- ═══════════════════════════════════════════════════════════════════════


-- ── Purities ──────────────────────────────────────────────────────────
-- The owner's launch set. Adding platinum or 14K later is one INSERT.
--
-- **Nothing in either client can add one.** `purities` is owner-managed
-- data by ADR-0010's reasoning and by schema.md's, but no screen was ever
-- built to manage it — the Add Product form only reads the list. So this
-- file is currently the only way a purity comes to exist, which is why it
-- carries the real values rather than development ones. Recorded as Open
-- Question 23.
insert into public.purities (code, label, display_order) values
  ('22K',    '22K Gold', 1),
  ('18K',    '18K Gold', 2),
  ('Silver', 'Silver',   3)
on conflict (code) do nothing;


-- ── Categories ────────────────────────────────────────────────────────
-- The PRD's eleven, in the PRD's order.
--
-- A starting point, not a decision: the owner renames, reorders, hides
-- and adds from the Android app (M8.6, M8.7) without a migration, which
-- is exactly what ADR-0010 chose a table for. Production needs at least
-- one category to exist before the first piece can be filed, and these
-- are the eleven the PRD names.
--
-- The hidden `unreleased-collection` is deliberately NOT here. It exists
-- in seed.sql to prove the RLS visibility rule holds, and a fake
-- collection has no business in the shop's real catalogue.
insert into public.categories (slug, name, display_order, is_visible) values
  ('gold-rings',         'Gold Rings',         1,  true),
  ('earrings',           'Earrings',           2,  true),
  ('chains',             'Chains',             3,  true),
  ('necklaces',          'Necklaces',          4,  true),
  ('pendants',           'Pendants',           5,  true),
  ('bangles',            'Bangles',            6,  true),
  ('bracelets',          'Bracelets',          7,  true),
  ('bridal-jewellery',   'Bridal Jewellery',   8,  true),
  ('diamond-jewellery',  'Diamond Jewellery',  9,  true),
  ('silver-jewellery',   'Silver Jewellery',   10, true),
  ('kids-collection',    'Kids Collection',    11, true)
on conflict (slug) do nothing;
