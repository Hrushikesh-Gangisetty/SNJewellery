-- ═══════════════════════════════════════════════════════════════════════
-- Seed data.
--
-- Run by `supabase db reset` against a LOCAL database. Never run against
-- production — M5.6 enters the real catalogue there.
--
-- Categories and purities are REAL and intended to persist. The owner will
-- supply a final category list before launch and can reorder, rename, hide
-- or add from the Android app without a migration (ADR-0010), so these are
-- a working starting point taken from the PRD's eleven.
--
-- Products here are PLACEHOLDERS for developing M4, mirroring the fixture
-- set in web/lib/data/fixtures.ts — including its deliberately awkward
-- cases, so the same layout edges get exercised against real SQL:
--   a name long enough to wrap, missing weight/summary, a single-image
--   product, a product with no images, a sold product, an archived
--   product, an empty-but-visible category, and a hidden category
--   containing a featured product.
-- ═══════════════════════════════════════════════════════════════════════

-- Idempotent: safe to re-run.
truncate table public.product_images, public.products cascade;
delete from public.categories;
delete from public.purities;


-- ── Purities ──────────────────────────────────────────────────────────
-- The owner's launch set. Adding platinum or 14K later is one INSERT.
insert into public.purities (code, label, display_order) values
  ('22K',    '22K Gold', 1),
  ('18K',    '18K Gold', 2),
  ('Silver', 'Silver',   3);


-- ── Categories ────────────────────────────────────────────────────────
-- The PRD's eleven, plus one hidden category used to prove the RLS
-- visibility rule actually holds.
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
  ('kids-collection',    'Kids Collection',    11, true),
  -- Hidden. Its products must never reach a customer.
  ('unreleased-collection', 'Unreleased Collection', 12, false);


-- ── Products ──────────────────────────────────────────────────────────
-- Placeholder photographs point at the local SVG in web/public/, so
-- nothing depends on the network and no storage object is implied.

insert into public.products
  (slug, name, summary, description, category_id, purity_id,
   weight_grams, colours, tags, featured, sold, archived, created_at)
values
  ('temple-design-bridal-necklace',
   'Temple Design Bridal Necklace',
   'Traditional temple work with intricate detailing.',
   'A traditional temple-design bridal necklace, handcrafted with detailed motifs drawn from South Indian temple architecture. Suited to bridal wear and worn with matching earrings.',
   (select id from public.categories where slug = 'bridal-jewellery'),
   (select id from public.purities where code = '22K'),
   48.60, array['Yellow'], array['bridal','temple','traditional','necklace'],
   true, false, false, now() - interval '1 day'),

  -- Long name: wraps to two lines on a 168px card.
  ('antique-finish-lakshmi-haram-long-chain',
   'Antique Finish Lakshmi Haram with Long Chain',
   'Antique-finish haram with Lakshmi motif.',
   'An antique-finish haram featuring a central Lakshmi motif, strung on a long chain. Traditionally worn for weddings and festival occasions.',
   (select id from public.categories where slug = 'necklaces'),
   (select id from public.purities where code = '22K'),
   62.15, array['Yellow','Antique'], array['haram','antique','lakshmi','long chain','festival'],
   true, false, false, now() - interval '2 days'),

  -- SOLD: must stay visible, with a badge.
  ('diamond-cut-jhumka-earrings',
   'Diamond Cut Jhumka Earrings',
   'Classic jhumkas with a diamond-cut finish.',
   'Classic jhumka earrings with a diamond-cut finish that catches light from every angle.',
   (select id from public.categories where slug = 'earrings'),
   (select id from public.purities where code = '22K'),
   12.40, array['Yellow'], array['jhumka','earrings','diamond cut'],
   true, true, false, now() - interval '4 days'),

  -- No weight, no colours.
  ('mens-signet-ring',
   'Men''s Signet Ring',
   'Plain signet ring with a brushed face.',
   'A plain signet ring with a brushed face and rounded shank.',
   (select id from public.categories where slug = 'gold-rings'),
   (select id from public.purities where code = '18K'),
   null, array[]::text[], array['ring','mens','signet'],
   false, false, false, now() - interval '5 days'),

  ('silver-anklet-pair',
   'Silver Anklet Pair',
   'Hallmarked silver anklets with ghungroo detail.',
   'A pair of hallmarked silver anklets with fine ghungroo bells along the length.',
   (select id from public.categories where slug = 'silver-jewellery'),
   (select id from public.purities where code = 'Silver'),
   84.00, array[]::text[], array['anklet','silver','payal'],
   false, false, false, now() - interval '7 days'),

  -- No summary, no description, and no images at all.
  ('plain-gold-bangle-set',
   'Plain Gold Bangle Set',
   null, null,
   (select id from public.categories where slug = 'bangles'),
   (select id from public.purities where code = '22K'),
   31.75, array['Yellow'], array['bangle','plain','daily wear'],
   false, false, false, now() - interval '9 days'),

  ('kundan-choker-set',
   'Kundan Choker Set',
   'Kundan choker with matching earrings.',
   'A kundan choker set with matching earrings, finished with pearl drops along the lower edge.',
   (select id from public.categories where slug = 'bridal-jewellery'),
   (select id from public.purities where code = '22K'),
   55.30, array['Yellow','White'], array['kundan','choker','bridal','set'],
   false, false, false, now() - interval '11 days'),

  ('rose-gold-stud-earrings',
   'Rose Gold Stud Earrings',
   'Small everyday studs in rose gold.',
   'Small everyday studs in an 18K rose gold finish.',
   (select id from public.categories where slug = 'earrings'),
   (select id from public.purities where code = '18K'),
   3.20, array['Rose'], array['stud','earrings','rose gold','daily wear'],
   false, false, false, now() - interval '13 days'),

  ('diamond-solitaire-pendant',
   'Diamond Solitaire Pendant',
   'Single-stone pendant on a fine chain.',
   'A single-stone diamond solitaire pendant on a fine chain.',
   (select id from public.categories where slug = 'diamond-jewellery'),
   (select id from public.purities where code = '18K'),
   2.85, array['White'], array['diamond','pendant','solitaire'],
   true, false, false, now() - interval '15 days'),

  ('gold-mangalsutra-black-beads',
   'Gold Mangalsutra with Black Beads',
   'Traditional mangalsutra with a gold pendant.',
   'A traditional mangalsutra strung with black beads and finished with a gold pendant.',
   (select id from public.categories where slug = 'necklaces'),
   (select id from public.purities where code = '22K'),
   22.80, array['Yellow','Black'], array['mangalsutra','traditional','necklace'],
   false, true, false, now() - interval '19 days'),

  ('broad-kada-bangle',
   'Broad Kada Bangle',
   'Broad kada with a hand-engraved surface.',
   'A broad kada bangle with a hand-engraved surface pattern.',
   (select id from public.categories where slug = 'bangles'),
   (select id from public.purities where code = '22K'),
   44.20, array['Yellow'], array['kada','bangle','engraved'],
   false, false, false, now() - interval '26 days'),

  -- ARCHIVED: must never reach a customer.
  ('discontinued-pendant-design',
   'Discontinued Pendant Design',
   'Withdrawn from the catalogue.',
   'Archived. Should never appear on the public site.',
   (select id from public.categories where slug = 'pendants'),
   (select id from public.purities where code = '22K'),
   8.10, array[]::text[], array['pendant'],
   false, false, true, now() - interval '30 days'),

  -- FEATURED and in a HIDDEN category. This combination is what leaks
  -- through a home-page query that forgets to check visibility, so it
  -- exists specifically to prove the RLS policy holds.
  ('unreleased-festival-collection-piece',
   'Unreleased Festival Collection Piece',
   'Not yet launched.',
   'In a hidden category. Should never appear on the public site.',
   (select id from public.categories where slug = 'unreleased-collection'),
   (select id from public.purities where code = '22K'),
   18.00, array[]::text[], array['unreleased'],
   true, false, false, now() - interval '33 days');


-- ── Product images ────────────────────────────────────────────────────
-- Deliberate distribution: several multi-image products, one with exactly
-- one image, and 'plain-gold-bangle-set' with none.
insert into public.product_images (product_id, url, storage_path, display_order, aspect)
select
  pr.id,
  '/placeholder-product.svg',
  'products/seed/' || pr.slug || '-' || gs.n || '.svg',
  gs.n - 1,
  case
    when pr.slug in ('temple-design-bridal-necklace',
                     'antique-finish-lakshmi-haram-long-chain',
                     'kundan-choker-set',
                     'gold-mangalsutra-black-beads')
    then 'product-portrait'
    else 'product'
  end
from public.products pr
cross join lateral (
  select generate_series(
    1,
    case pr.slug
      when 'plain-gold-bangle-set' then 0   -- no images
      when 'silver-anklet-pair'    then 1   -- single image
      when 'temple-design-bridal-necklace' then 4
      else 2
    end
  ) as n
) gs;
