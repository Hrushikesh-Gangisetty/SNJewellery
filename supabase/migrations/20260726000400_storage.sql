-- ═══════════════════════════════════════════════════════════════════════
-- Storage bucket for product photographs.
--
-- Created here rather than in the dashboard so the whole backend stays
-- reproducible from migrations — the owner's explicit requirement.
--
-- One canonical image per photograph; thumbnail, mobile and optimised
-- renditions are DERIVED by Supabase's image transformation rather than
-- stored separately. See docs/adr/0005.
-- ═══════════════════════════════════════════════════════════════════════

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
  'product-images',
  'product-images',
  -- Public read: these are catalogue photographs meant to be seen, and
  -- public URLs let the CDN cache them, which the performance target
  -- depends on. Write is still admin-only, below.
  true,
  -- 5 MB. The Android app compresses before upload (M7.6), so anything
  -- larger means compression did not run — better to fail the upload than
  -- to silently blow through the storage budget.
  5242880,
  array['image/webp', 'image/jpeg', 'image/png']
)
on conflict (id) do update
  set public             = excluded.public,
      file_size_limit    = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;


-- ── Policies ──────────────────────────────────────────────────────────
-- Public read, admin-only write. Same boundary as the tables: RLS, not
-- client trust.

create policy product_images_storage_public_read
  on storage.objects
  for select
  to anon, authenticated
  using (bucket_id = 'product-images');

create policy product_images_storage_admin_insert
  on storage.objects
  for insert
  to authenticated
  with check (bucket_id = 'product-images' and public.is_admin());

create policy product_images_storage_admin_update
  on storage.objects
  for update
  to authenticated
  using (bucket_id = 'product-images' and public.is_admin())
  with check (bucket_id = 'product-images' and public.is_admin());

-- Needed by M8.4: deleting a product must remove its storage objects too.
-- The table cascade does not touch storage, so orphans accumulate — and
-- cost money — unless the app deletes them explicitly.
create policy product_images_storage_admin_delete
  on storage.objects
  for delete
  to authenticated
  using (bucket_id = 'product-images' and public.is_admin());
