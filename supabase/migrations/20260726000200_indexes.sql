-- ═══════════════════════════════════════════════════════════════════════
-- Indexes.
--
-- Sized for the real catalogue: ~500 products at launch, ~1,000 within a
-- year (resolved question 3), NOT the PRD's aspirational 100,000+. That
-- means no speculative indexing — each of these backs a query the website
-- actually makes.
--
-- Full-text search indexing arrives in M10.1, not here.
-- ═══════════════════════════════════════════════════════════════════════

-- Category listing pages, and the FK join used by every public product
-- read to check category visibility.
create index products_category_id_idx
  on public.products (category_id);

-- Home page "new arrivals", and the primary sort for every listing.
-- Descending because nothing ever asks for oldest-first.
create index products_created_at_idx
  on public.products (created_at desc, id desc);

-- Home page "featured collection". Partial: only a handful of products
-- are featured, so indexing the false rows would be waste.
create index products_featured_idx
  on public.products (created_at desc)
  where featured = true and archived = false;

-- The public read path filters on archived first. Partial index keeps it
-- to live rows only.
create index products_live_idx
  on public.products (category_id, created_at desc)
  where archived = false;

-- Gallery ordering. Covered by the unique constraint on
-- (product_id, display_order), so no separate index is needed — recorded
-- here so nobody adds a redundant one.

-- Category shortcuts on the home page and in the footer.
create index categories_visible_order_idx
  on public.categories (display_order)
  where is_visible = true;

-- Tag filtering (M10). GIN because tags is an array.
create index products_tags_idx
  on public.products using gin (tags);

comment on index public.products_featured_idx is
  'Partial index: featured and not archived. Backs the home page featured section.';
comment on index public.products_live_idx is
  'Partial index on live rows only — the public read path always filters archived = false.';
