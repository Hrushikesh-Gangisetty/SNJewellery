# Brand Identity

Part of the [design system](README.md). Produced by M1.1 and M1.2.

> **Configurability.** Per [ADR-0010](../adr/0010-configurable-site-content.md),
> the brand *values* here (name, logo path, contact details) are runtime
> configuration, not constants in code. This document defines the *rules* those
> values must follow. Changing the shop's phone number is a data change;
> changing the type scale is a change to this document.

---

## 1 · Identity

| | |
|---|---|
| **Trading name** | SN Jewellery & Silver Palace |
| **Short form** | SN Jewellery |
| **Location** | 4-394/A, Temple Street, Markapur – 523316 |
| **Tagline** | None. Do not invent one. |
| **Established** | Not yet supplied |
| **Logo** | Transparent PNG supplied 2026-07-27 and now in use — see §4 |

### The name is doing two jobs

"**SN Jewellery** & **Silver Palace**" names two halves of one business: gold
jewellery and silver. That is a fact about the merchandise, not decoration, and
the design should let both read rather than treating "& Silver Palace" as a
subtitle to be shrunk away.

It is also **28 characters**, which is long for a mobile header at 375 px. So:

| Context | Treatment |
|---|---|
| Page metadata, footer, About, structured data | Full name, always |
| Desktop header | Full logo lockup |
| Mobile header | **Winged SN monogram** — see §4 |
| Social previews (M11.2) | Full name |

Never abbreviate to "SNJ" or "SN J&SP". Never render the ampersand as "and".

---

## 2 · Brand attributes

Five words that decide arguments. When a design choice is unclear, the option
that better serves these wins.

1. **Trustworthy** — a jewellery purchase is a large, infrequent, often
   emotional decision made in person. Everything should feel settled and
   credible. Nothing should feel urgent or salesy.
2. **Premium, not loud** — restraint signals quality. Value is communicated by
   space, quiet typography, and photography given room, never by gold gradients
   or sparkle effects.
3. **Traditional in substance, modern in presentation** — the owner's own
   framing. The merchandise and the shop are traditional; the website should be
   as clean and current as any modern retail site. These are not in tension:
   present traditional pieces with modern restraint.
4. **Photography first** — the jewellery is the content. Every other element is
   scaffolding around an image, and should recede.
5. **Fast, on a phone, in Markapur** — customers browse on mobile, on Indian
   mobile networks, often before walking into the shop. This is the primary
   case, not the fallback. Performance is a brand attribute here, not just an
   engineering target.

---

## 3 · Reference analysis

The owner named four references. What to take from each — and what not to:

| Reference | Take | Leave |
|---|---|---|
| **Tanishq** | Category-led browsing that scales to a large catalogue; product photography on clean, consistent backgrounds; a warm, trustworthy register | The promotional density — banners, offer strips, campaign overlays. That is a national retailer's machinery and would read as clutter here. |
| **Candere** | Clean grid, disciplined product cards, filtering that stays usable on mobile | The e-commerce apparatus — cart, wishlist, price-led hierarchy. Nothing is sold online here. |
| **Palmonas** | Modern, minimal, confident use of whitespace; strong mobile experience | The youth-fashion tone. This shop is a traditional jeweller. |
| **Apple** | The real lesson: **one thing per screen, enormous whitespace, product photography carrying the entire page, and near-zero chrome.** This is the structural model. | Do not copy Apple's palette or typography. Borrow the restraint, not the identity. |

### The synthesis

**Apple's structural restraint, applied to Tanishq's merchandise.** Large
photography, generous space, minimal chrome, and a single clear action per
screen — applied to a traditional Indian jewellery catalogue.

---

## 4 · Logo

**Supplied 2026-07-26** as a raster mockup: the mark on a deep teal wall, gold
lettering rendered as brushed metal.

**Superseded 2026-07-27** by a background-free PNG — 768 × 768, RGBA, genuinely
transparent — which is what the site now ships. SVG was not available. See
[§4.1](#41--the-assets-in-the-repository) for what was derived from it.

### What the mark contains

Three parts, and the separation matters:

1. **A winged "SN" monogram** — the letters interlocked, with a stylised pair of
   wings above.
2. **"Jewellery"** — a high-contrast classical serif, set large.
3. **"& Silver Palace"** — the same serif, much smaller, beneath.

### The monogram solves the mobile-header problem

§1 flagged that the 28-character trading name is unusable in a mobile header.
**The winged SN monogram is the answer.** It is compact, distinctive, and legible
at small sizes in a way the full lockup is not.

Revised from §1:

| Context | Treatment |
|---|---|
| Mobile header | **Winged SN monogram alone** |
| Desktop header | Full logo lockup |
| Favicon / app icon | **Winged SN monogram** |
| Footer, About, social previews | Full logo lockup |
| Page metadata, structured data | Full name as text |

This needs the monogram **exported as its own asset**, separate from the lockup.
That was written when the only asset was a teal-backed mockup. With a clean
transparent PNG it became possible to crop one — see §4.1 — and that crop is
what ships. It is a stopgap, not a replacement for a purpose-drawn mark.

### 4.1 · The assets in the repository

Everything below is **derived from the one supplied PNG**. Nothing was redrawn,
recoloured, or restyled.

| File | What it is | How it was produced |
|---|---|---|
| `web/public/site.logo.png` | Full lockup, 725 × 451 | Supplied 768 × 768 PNG cropped to its content box (x 17–741, y 100–550). The canvas padding was asymmetric — 100 px above, 218 px below — which would have made every rendered height wrong. |
| `web/public/site.monogram.png` | Winged SN, 409 × 409 | Cropped from the same source at x 250–614, y 100–376, then centred on a square canvas with 6 % padding. The cut at y 376 is the narrowest row between the monogram and the "Jewellery" wordmark, where only the N's stem remains. |
| `web/app/icon.png`, `web/app/apple-icon.png` | 192 px and 180 px | Downscaled from the monogram. |
| `web/app/favicon.ico` | 48 px | The same, wrapped in an ICO container. |

Rendered heights are set in `components/layout/wordmark.tsx`: 44 px for the
lockup, 36 px for the monogram. Both clear the 24 px floor below.

**What a purpose-drawn asset would still improve.** The crop is honest but it is
a crop: the monogram's descender is cut at the baseline rather than terminating
by design, and at 32 px and below the wings lose definition. An SVG, or a
monogram exported on its own artboard, fixes both. Neither blocks anything.

### The serif validates the typeface choice

The wordmark is a high-contrast classical serif with pronounced thick/thin
stroke modulation — the same family of letterform as **Cormorant Garamond**,
chosen independently in [typography.md](typography.md). The site's headings will
sit naturally beside the logo rather than fighting it.

It also confirms the 28 px floor for Cormorant: the logo's own serif has exactly
the delicate hairlines that disappear at small sizes, which is why the wordmark
in the mark is set large and "& Silver Palace" is set in a heavier optical weight.

### The gold

The lettering is a **metallic gradient**, not a flat colour — pale highlight
through mid gold to dark shadow, because it is rendered as brushed metal. No
single hex is "the" brand gold.

`accent` was resampled from its mid-tone: `#C9A227` → **`#C2A55C`**, a warmer and
less saturated gold. See [colour.md](colour.md) §6 for all four revised values
and the revalidated ratios.

### The teal is not adopted

The mockup's deep teal background reads as a **presentation backdrop** chosen by
whoever produced the mockup, not a brand colour. It has deliberately not been
added to the palette: it would contradict the brief — white, gold, black, subtle
neutrals, nothing competing with the jewellery.

**If teal is genuinely a brand colour** — on signage, packaging, or bill books —
that is a real palette decision and needs saying, because it cannot be inferred
from a mockup.

### Still needed

| Asset | Why | Status |
|---|---|---|
| **A version that works on white** | Logos are exempt from WCAG contrast rules, so gold on white is permitted. | ✅ **Resolved** — the supplied PNG is transparent and sits on white. If it ever reads washed out, the fix is a dark header band, not darkening the logo. |
| **SVG of the full lockup** | Raster will not scale past its native size, and an SVG would give the gradient stops exactly. | Still wanted. Not blocking: 725 × 451 is far above any rendered size. |
| **SVG of the monogram alone** | Mobile header and favicon | Still wanted. §4.1's crop covers it for now. |
| **A reversed / single-colour version** | Print, and any dark surface where the metallic gradient reads muddy | Still wanted. The gold reads acceptably on near-black, so nothing is blocked. |

### Usage rules

These apply now that the asset exists, and the layout must still work **without**
it — the header falls back to the wordmark while only a raster is available.

- **Clear space:** minimum equal to the cap height of the logo on all sides.
  Nothing intrudes.
- **Minimum size:** legible at 24 px height on mobile. If the supplied logo is
  not legible at that size, request a simplified mark for small use rather than
  scaling it down and hoping.
- **Placement:** top-left in the header, and once in the footer. Never repeated
  within page content, never watermarked over product photography.
- **Backgrounds:** must work on white and on near-black. If the supplied asset
  is single-colour, request a reversed version rather than applying a filter.
- **Format:** SVG preferred. If only raster is available, request the largest
  original; do not upscale.
- **Never:** stretch, rotate, recolour, add effects, or place on a busy
  background.

Stored in Supabase Storage with its path in `site_settings`, per ADR-0010 — so
supplying it later is a data change, not a code change.

---

## 5 · Tone of voice

**Plain, warm, and specific. Never salesy.**

The register is a knowledgeable jeweller describing a piece, not a
retailer pushing one. No urgency, no superlatives, no invented scarcity.

| Do | Don't |
|---|---|
| "22K gold necklace, 34.2 g. Traditional temple design." | "STUNNING must-have necklace — enquire NOW!" |
| "Visit our showroom in Markapur to see this piece." | "Limited stock! Don't miss out!" |
| "Sold" | "SOLD OUT — but check these alternatives!!" |
| "Ask about this piece on WhatsApp" | "Get the BEST price today!" |

**Rules:**

- **State facts.** Purity, weight, and design tradition are what a customer
  wants. They are more persuasive than adjectives.
- **No exclamation marks** in product or interface copy.
- **No fabricated urgency or scarcity.** The shop is open every day.
- **Sentence case** for headings and buttons, not Title Case or ALL CAPS.
- **Invite, don't pressure.** Every call to action is an invitation to visit or
  ask — because that is literally all the site can do.
- **English at launch.** The PRD lists multi-language as a future enhancement;
  the audience is largely Telugu-speaking, so avoid idiom that would not survive
  translation, and keep sentences short.

---

## 6 · Anti-patterns

The owner named these explicitly. They are requirements, not preferences.

**Never:**

- **Popups or interstitials.** No newsletter modal, no offer overlay, no
  "wait — before you go". Nothing covers the jewellery.
- **Banner stacking.** No carousel of promotional strips, no sale bars.
- **Clutter.** If a page element does not help a customer see a piece, judge it,
  or contact the shop, it does not belong.
- **Sparkle, shine, or shimmer effects.** Gold gradients, glints, animated
  sparkles, and lens flares read as cheap and date immediately. The photography
  supplies the shine.
- **Autoplaying anything.**
- **Fake social proof** — invented review counts, "23 people viewing".
- **Countdown timers.**
- **Dense borders and heavy drop shadows.** Separation comes from space.

If a future request conflicts with this list, raise it rather than
implementing it quietly.

---

## 7 · Still needed

| Item | Blocks | Workaround until supplied |
|---|---|---|
| Logo **SVG** + monogram SVG + reversed version | Nothing hard | The supplied PNG and the crops derived from it are in use; see §4.1 |
| Existing signage / packaging / print photos | Nothing hard | Palette derived from the owner's stated preference (white, gold, black, neutrals) rather than sampled from real assets — see [colour.md](colour.md) |
| Year established | About page, trust signals | Section hidden |
| Business history, certifications | About page (M4.11) | Section hidden, per ADR-0010 |
| Social media handles | Footer, Contact (M4.10) | Links hidden |
| Google Maps location | Contact (M4.10), `LocalBusiness` (M11.3) | Address shown as text; map hidden |

None of these block M1's remaining tasks or M2. All are configuration, so
supplying them later requires no code change.

**One thing genuinely worth checking early:** if existing signage or packaging
already uses a specific gold or a specific typeface, the palette in
[colour.md](colour.md) should be reconciled against it before M2.4 consumes the
tokens. Discovering an established brand colour after the site is built is the
one avoidable rework here.
