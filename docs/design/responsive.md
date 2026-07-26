# Responsive Behaviour

Part of the [design system](README.md). Produced by M1.8.

**Breakpoint values, the spacing scale, container widths, grid column counts, and
touch-target minimums all live in [layout.md](layout.md)** and are not repeated
here. This document covers how components *behave* across those breakpoints, and
the image aspect ratios that keep layout stable.

---

## 1 · Mobile-first, and why it is literal here

The PRD requires mobile-first. For this shop it is not a methodology preference:

- Customers browse on phones, on Indian mobile networks, often minutes before
  walking into the shop in Markapur.
- The shop owner uploads from a phone, one-handed, between customers.

So **375 px is the design target and the primary case**, not a degraded fallback.
Desktop is the adaptation.

Practically: every style targets the smallest screen first, and breakpoints only
ever *add*. No `max-width` queries — a `min-width` cascade is easier to reason
about and cannot leave a gap between ranges.

---

## 2 · Image aspect ratios

Fixed ratios, declared as tokens, so **layout shift is structurally impossible
rather than merely avoided**. Every image sits inside an `AspectBox` that reserves
its space before the image loads.

| Token | Ratio | Used for |
|---|---|---|
| `aspect-product` | 1 / 1 | **Default** product images — catalogue cards, gallery |
| `aspect-product-portrait` | 4 / 5 | Necklaces, long chains, bridal sets |
| `aspect-hero` | 16 / 9 | Desktop hero |
| `aspect-hero-mobile` | 4 / 5 | Mobile hero — a 16/9 hero on a phone is a letterbox strip |

**Square is the default** because it is the most forgiving ratio across rings,
earrings, pendants, and bangles, and because a uniform grid reads as considered
while a ragged one reads as neglected.

**The consequence for photography** is real and belongs in the shoot standard
([ux.md](ux.md)): pieces must be framed so a square crop does not cut them. A
long necklace shot to fill a landscape frame will be ruined by a square crop.

Product images are never `object-fit: cover` in a way that crops the piece itself
— `contain` on a `surface-sunken` background is better than amputating a chain.

---

## 3 · Component reflow

### Header
| Range | Behaviour |
|---|---|
| base–`md` | Logo/short wordmark left, hamburger right. Nav in `MobileDrawer` |
| `lg`+ | Full name, horizontal nav, quick contact inline. No hamburger |

Sticky at all sizes, but **minimal height on mobile** — vertical space is the
scarcest resource on a phone showing photographs.

### Product grid
Column counts are in [layout.md](layout.md) §5. Reflow rules:

- **Gaps tighten before gutters do.** At 375 px, reduce card spacing rather than
  letting text approach the screen edge.
- **Card content order never changes** across breakpoints — image, name, category,
  purity, weight. Reordering by breakpoint breaks screen-reader order.
- **Two columns at 375 px yields ≈168 px cards.** At that size the product name
  may wrap to two lines; the card reserves space for two so a one-line and a
  two-line card in the same row stay aligned.

### Product detail
| Range | Behaviour |
|---|---|
| base–`md` | Single column: gallery, then name, then specs, then conversion buttons, then description, then related |
| `lg`+ | Two columns: gallery left (sticky), details right |

**Conversion buttons appear above the description on mobile**, deliberately. A
customer who has seen the piece and the price-relevant specs should not scroll
past prose to find the WhatsApp button.

### Gallery
| Range | Behaviour |
|---|---|
| base–`md` | Swipeable, dot indicators, thumbnails below |
| `lg`+ | Main image with a vertical thumbnail strip; arrow keys navigate |

### Filters (M10)
| Range | Behaviour |
|---|---|
| base–`md` | Bottom sheet, triggered by a Filters button. Apply/Clear pinned to the bottom, within thumb reach |
| `lg`+ | Persistent sidebar, filters apply immediately |

### Prose (About)
Capped at `container-prose` (680 px ≈ 70 characters) at every size. A full-width
paragraph on a 1440 px display is unreadable.

### Tables and specs
No horizontal scroll on mobile. Specs become stacked label/value pairs below
`md`, never a scrolling table.

---

## 4 · Rules

1. **Never hide content to make a layout fit.** Reflow it. If something is not
   worth showing on mobile it is probably not worth showing at all — and hiding it
   with `display: none` still ships the weight.
2. **DOM order equals visual order.** Do not use `order` or `grid-row` to reorder
   content between breakpoints; it desynchronises tab order and screen-reader
   order from what is seen.
3. **Never let text touch a screen edge.** Gutters are not optional.
4. **Fluid type over breakpoint-stepped type** for display sizes — see the
   `clamp()` entries in [typography.md](typography.md).
5. **Test at 375, 768, and 1440 px**, and at 320 px confirm nothing overflows even
   though it is not the target.
6. **Test on a real device**, not device emulation. Touch accuracy, scroll
   momentum, and font rendering all differ — and it is where
   [typography.md](typography.md)'s 28 px serif floor gets validated.
7. **Landscape phones exist.** A 375 × 667 device rotated is 667 × 375: check the
   gallery and drawer do not assume height.

---

## 5 · Android

The admin app targets a modern phone and does not need tablet layouts at launch.

- **`WindowSizeClass`**, not raw pixel checks.
- **Compact** is the only class that must be right. Medium/expanded should not
  break, but need no bespoke layout.
- **48 dp minimum touch targets** (Material 3), against the web's 44 px.
- The upload form must remain usable **with the keyboard open**, which halves
  available height — the most commonly missed responsive case in a form-heavy app.
