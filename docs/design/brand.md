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
| **Logo** | Not yet supplied — see §4 |

### The name is doing two jobs

"**SN Jewellery** & **Silver Palace**" names two halves of one business: gold
jewellery and silver. That is a fact about the merchandise, not decoration, and
the design should let both read rather than treating "& Silver Palace" as a
subtitle to be shrunk away.

It is also **28 characters**, which is long for a mobile header at 375 px. So:

| Context | Treatment |
|---|---|
| Page metadata, footer, About, structured data | Full name, always |
| Desktop header | Full name (logo lockup when supplied) |
| Mobile header | **Short form**, or logo mark alone once a logo exists |
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

## 4 · Logo rules

The logo has not been supplied. These rules apply when it is, and the layout
must work **before** it exists — the header falls back to the wordmark.

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
| Logo (SVG, plus reversed version) | Nothing hard | Header and footer render the wordmark |
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
