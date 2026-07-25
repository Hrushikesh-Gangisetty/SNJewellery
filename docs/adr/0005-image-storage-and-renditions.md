# ADR-0005: Supabase Storage with transform-derived renditions

- **Status:** ✅ Accepted
- **Date:** 2026-07-25
- **Deciders:** Hrushikesh Gangisetty
- **Affects:** M3.6, M7.6, M7.7, M12.1

## Context

Photographs are the product. A jewellery catalogue is essentially an image delivery system with some metadata attached, so how images are stored and served determines whether the PRD's performance targets are reachable at all.

The constraints pull against each other:

- The owner shoots on a phone. Source images will be several megabytes each.
- The PRD requires thumbnail, mobile-friendly, and optimised renditions, CDN delivery, and lazy loading.
- The website must load in under two seconds on mobile with Lighthouse Performance above 90 — on pages that are mostly images.
- Upload must complete in under thirty seconds on mobile data (M7.13), which bounds how much can be uploaded.
- Storage and egress cost money, and jewellery photography is heavy on both (Open Question 2).

Two independent decisions are needed: what the app uploads, and how renditions are produced.

## Decision

**Compress on the device, then derive renditions on demand.**

1. **The Android app compresses and resizes before upload** (M7.6), to a documented maximum dimension and file size. Uploading a full-resolution phone photograph over mobile data cannot meet the thirty-second target, and no rendition of a catalogue image needs that resolution.
2. **One canonical image is stored per photograph**, at a documented path: `products/{product_id}/{image_id}.webp`.
3. **Renditions are derived by Supabase's image transformation**, not stored as separate uploads. Thumbnail, mobile, and optimised are transformation parameters on the canonical object.
4. **`product_images` stores both `image_url` and `storage_path`** — the path so renditions can be constructed, the URL so consumers need not.
5. **The path convention is the only way image locations are constructed.** No ad-hoc string building anywhere in either client.
6. **`next/image` consumes the renditions** with accurate `sizes` and fixed aspect ratios (M2.9), so layout shift is structurally impossible rather than merely avoided.

## Consequences

### What this makes easier

- Upload time is bounded by device-side compression, which is what makes M7.13's target achievable.
- No image pipeline to build or operate — no queue, no worker, no Edge Function for resizing.
- Adding a rendition later is a parameter change, not a re-upload of the whole catalogue.
- Storage cost stays close to one object per photograph.
- CDN delivery comes with Storage, satisfying the PRD's CDN requirement directly.

### What this makes harder

- **Compression is lossy and irreversible.** Compress too aggressively and fine detail — chain links, gemstone facets, engraving — is gone with no recovery short of re-shooting. M7.6's acceptance criterion is therefore a visual check at full-screen size on the website, not just a file-size number.
- The original photograph is not archived. If a higher resolution is ever wanted, it must be re-shot.
- Transformation quality and cost are Supabase's, not ours.
- A cascade delete on `products` removes the image rows but **not** the storage objects. M8.4 must delete both explicitly, and its acceptance criterion is inspecting the bucket afterwards — orphaned objects accumulate silently and cost money.

### What this commits us to

The compression target set in M7.6 effectively defines the catalogue's maximum image quality forever. It deserves a deliberate decision with the owner looking at real jewellery photographs on a real screen, not a default value.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Upload the original, generate renditions server-side | Best quality and archives the original — but fails the thirty-second upload target on mobile data, and multiplies storage and egress cost against Open Question 2. Worth revisiting if upload happens over Wi-Fi in practice. |
| Upload all three renditions from the device | Triples upload time and payload for something the platform derives for free. |
| Cloudinary or imgix | Excellent transformation and optimisation, but a second vendor, a second bill, and a second set of credentials for a capability Storage already provides. |
| Store renditions as separate objects | Adding or changing a rendition later means reprocessing the whole catalogue. |

## Open sub-questions

- **The exact compression target** — maximum dimension and file size. Set in M7.6, with the owner reviewing real photographs. This is the highest-stakes open detail in this ADR.
- **Open Question 2** — projected storage and egress against Supabase tier limits. Needs an estimate before M5.
- Whether to archive originals separately at lower storage cost, if the shop later wants print-quality assets.
- Whether WebP is right for every case, or whether some images warrant a different format.

## References

- [prd.md](../../prd.md) — Storage; Website Requirements; Android Requirements; Non-Functional Requirements
- [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — M3.6, M7.6, M7.7, M8.4, M12.1
