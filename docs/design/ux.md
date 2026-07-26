# UX Guidelines

Part of the [design system](README.md). Produced by M1.10.

§1 is written for the shop owner holding a phone, not for a designer. It is the
one part of this design system used directly by a person rather than by code.

---

## 1 · Product photography standard

Photograph quality determines how this catalogue looks more than any design
decision. A well-shot piece on a plain background looks premium in almost any
layout; a cluttered, yellow-lit photo cannot be rescued by one.

### The rules

| | |
|---|---|
| **Background** | Plain and light. White or very light warm grey. A sheet of white paper or card works. **Never** a patterned cloth, newspaper, a counter with other items visible, or a hand. |
| **Lighting** | Bright, indirect daylight. Near a window, not in direct sun. **Turn off the phone flash** — it creates a hard white blowout on gold and flattens all the detail. |
| **Framing** | Piece centred, filling roughly 80% of the frame. Leave a small even margin. **Shoot so a square crop still contains the whole piece** — the catalogue crops to square by default. |
| **Angle** | Straight on, camera parallel to the piece. Flat on the surface for chains and necklaces; upright for rings on a small stand. |
| **Focus** | Tap the screen on the piece before shooting. Check the photo is sharp before moving on — engraving and stone facets are the detail customers zoom into. |
| **Resolution** | Highest your camera offers. The app compresses on upload; it cannot add detail back. |
| **Consistency** | **Same background and same lighting for every piece.** A consistent catalogue looks professional; a mixed one looks improvised, however good the individual photos. |
| **How many** | 1 to 4 per piece. First image is the main one and should be the clearest straight-on shot. Add a close-up of detail and an alternate angle if useful. |
| **Cleanliness** | Wipe the piece first. Fingerprints and dust are very visible at catalogue size. |

### Long pieces

Necklaces, long chains, and bridal sets do not fit a square crop well. Either:
- lay the piece in a **compact arrangement** so it fits a square, or
- shoot it upright and it will be published using the **4:5 portrait ratio**.

Tell whoever uploads which of the two applies — the app will offer the choice.

### Quick checklist

Before uploading, look at the photo and ask:
1. Is the background plain and light?
2. Is the flash off?
3. Is the whole piece inside the frame with a margin?
4. Is it sharp when zoomed in?
5. Does it match the last photos taken?

---

## 2 · Call-to-action hierarchy

Nothing is sold online. **Every page exists to produce an enquiry or a visit**, so
the conversion actions are the primary interface, not a footer afterthought.

### Priority, product detail page

1. **WhatsApp enquiry** — primary button, accent fill. The dominant channel here,
   and the message arrives pre-filled with the product name and link so the owner
   knows immediately which piece is being asked about.
2. **Call** — secondary button, equal size, outline style.
3. **Get directions** — tertiary, text-and-icon. Hidden entirely until a Maps
   location is supplied.

All three sit **above the description** on mobile ([responsive.md](responsive.md)
§3). Three actions maximum — a fourth dilutes all of them.

### Elsewhere

| Surface | Primary action |
|---|---|
| Home hero | Browse the catalogue |
| Category shortcut | Enter that category |
| Product card | Open the product (the whole card is the target) |
| Contact page | WhatsApp, then Call |
| Header / footer | Persistent WhatsApp + Call |

### Rules

- **One primary action per screen.** Two primaries means neither is.
- **Buttons state what happens**: "Ask about this on WhatsApp", not "Enquire" or
  "Click here".
- **Never a fake action.** No wishlist, no cart, no "add to bag" — the site cannot
  do those things, and offering them then failing is worse than not offering.
- No urgency, no scarcity, no popups. See [brand.md](brand.md) §6.

---

## 3 · Loading, empty, and error patterns

Canonical treatments. Every surface uses these rather than inventing its own —
`components.md` lists which states each component must implement.

### Loading

| Situation | Treatment |
|---|---|
| Catalogue grid, first load | **Skeleton cards** matching the real card's dimensions exactly, so nothing shifts when content arrives |
| Product detail | Skeleton for image (correct aspect ratio) and text lines |
| Pagination / filter | Keep existing results visible, dim slightly, spinner on the button. **Never blank the page** |
| Image within a loaded page | `surface-sunken` block at the correct aspect ratio |
| Form submission (app) | Button shows progress, form disables, nothing else moves |

**Skeletons must match real dimensions.** A skeleton of the wrong size causes the
exact layout shift it exists to prevent.

### Empty

Every empty state has: a plain statement of what is empty, a reason if known, and
**a way forward**.

| Situation | Message | Action |
|---|---|---|
| Category with no products | "No pieces in this category yet." | Browse all categories |
| Search, no results | "No pieces match \"<query>\"." | Clear search · browse categories · **WhatsApp us — we may have it in store** |
| Filters exclude everything | "No pieces match these filters." | Clear filters |
| Product with no images | Placeholder block at product aspect ratio | Page still renders every spec |
| App: no products yet | "No products yet." | Add your first product |
| App: no drafts | Nothing shown — the section hides | — |

The search empty state is worth emphasising: **"we may have it in store, ask us"
turns a dead end into an enquiry.** A catalogue that cannot show a piece can still
produce a customer.

### Error

| Situation | Treatment |
|---|---|
| Failed data load | Inline error where the content belongs + Retry. Never a blank page |
| Unknown product URL | 404 page with catalogue and home links |
| Image fails to load | `surface-sunken` block with alt text visible. Never a broken-image icon |
| App: no network | Explicit "No internet connection" + Retry. Distinct from a server error |
| App: upload failed | Snackbar with **Retry**, and the draft is preserved. Never silently discarded |
| App: not an admin | Clear explanation, not a blank screen or a permission error dump |

### Rules

1. **Never a dead end.** Every empty and error state offers a next step.
2. **Say what happened in plain words.** No status codes, no "something went wrong"
   where the cause is known.
3. **Distinguish "nothing here" from "something broke".** They need different
   messages and different actions.
4. **Never lose the owner's work.** A failed upload keeps its draft and stays
   retryable. This is the single most important error rule in the app.
5. **No error blames the user.**

---

## 4 · Browsing behaviour

- **Category-led, not search-led.** Most customers browse by category; search is
  for people who know what they want. The home page leads with categories.
- **The whole product card is the target**, not just the name.
- **Sold pieces stay visible** with a Sold badge — they are portfolio evidence of
  what the shop makes and sells. Confirmed by the owner.
- **Back must return to the same scroll position** in the catalogue. Losing place
  after viewing one product is the most irritating catalogue defect there is.
- **Shareable URLs.** Filters and search live in the query string (M10.5) so a
  customer can send a filtered view over WhatsApp.

---

## 5 · Still needed

The owner will supply **5–10 unedited product photographs**. When they arrive:

1. Check §1's rules against what is actually being shot, and adjust the standard to
   what is achievable rather than what is ideal.
2. Set the M7.6 compression target against a real photograph — the trade-off
   between upload time and visible detail on chains and stone facets cannot be
   judged from stock imagery.
3. Confirm whether square or 4:5 is the more common natural framing.
