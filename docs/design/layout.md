# Spacing, Shape, Elevation & Layout Grid

Part of the [design system](README.md). Produced by M1.5.

This document is the canonical home for **breakpoint values**, because the layout
grid is defined against them. [responsive.md](responsive.md) (M1.8) owns how
components *behave* at each breakpoint; it does not redefine the numbers.

---

## 1 · Spacing scale

A 4 px base. Every margin, padding, and gap resolves to one of these — there are
no arbitrary values.

| Token | px | rem | Typical use |
|---|---:|---|---|
| `space-0` | 0 | 0 | Reset |
| `space-1` | 4 | 0.25 | Icon-to-label gap |
| `space-2` | 8 | 0.5 | Tight internal padding, badge padding |
| `space-3` | 12 | 0.75 | Compact gaps |
| `space-4` | 16 | 1 | **Default gap.** Card padding on mobile |
| `space-5` | 20 | 1.25 | |
| `space-6` | 24 | 1.5 | Card padding on desktop, grid gutters |
| `space-8` | 32 | 2 | Between related blocks |
| `space-10` | 40 | 2.5 | |
| `space-12` | 48 | 3 | Section padding on mobile |
| `space-16` | 64 | 4 | Section padding on desktop |
| `space-20` | 80 | 5 | Between major page sections |
| `space-24` | 96 | 6 | Hero padding, generous section breaks |
| `space-32` | 128 | 8 | Editorial breathing room on desktop |

**The large end of this scale is the point.** "Premium, minimal, Apple-like
restraint" is delivered mostly by whitespace. `space-20` and above are not
excessive — they are the mechanism. When a page feels cheap, the first thing to
check is whether section spacing has been quietly reduced.

Note the gaps: no `space-7`, `space-9`, `space-11`. Fewer steps means fewer
decisions and more consistency.

---

## 2 · Corner radius

| Token | px | Use |
|---|---:|---|
| `radius-none` | 0 | Full-bleed imagery, table edges |
| `radius-sm` | 2 | Badges, tags, small inputs |
| `radius-md` | 4 | **Default.** Buttons, inputs, cards |
| `radius-lg` | 8 | Modals, sheets, large panels |
| `radius-full` | 9999 | Circular avatars, icon buttons |

**Radii are deliberately small.** Heavily rounded corners read as friendly and
consumer-app; restrained corners read as considered and premium. Compare
Tanishq's soft cards against Apple's near-square ones — the second is the
register this brand wants.

**Product images use `radius-none` or `radius-sm`.** A jewellery photograph
should be presented like a print, not like an app icon.

---

## 3 · Elevation

| Token | Shadow (web) | dp (Compose) | Use |
|---|---|---:|---|
| `elevation-0` | none | 0 | **Default for nearly everything** |
| `elevation-1` | `0 1px 2px rgb(0 0 0 / 0.04)` | 1 | Barely-there card lift, hover state |
| `elevation-2` | `0 2px 8px rgb(0 0 0 / 0.06)` | 3 | Dropdowns, popovers |
| `elevation-3` | `0 8px 24px rgb(0 0 0 / 0.10)` | 8 | Modals, mobile drawer |

Each step carries **two expressions of one idea**, because the platforms do not
describe elevation the same way: CSS takes a shadow, and Android takes a
distance. The dp values are the Compose equivalents of those shadows, not a
second scale — a component at `elevation-2` must look the same on both clients.

**Separation comes from space and hairline borders, not shadows.** The
anti-pattern list in [brand.md](brand.md) names heavy drop shadows explicitly.
Card grids should use `elevation-0` with a `border` hairline, or nothing at all —
let the whitespace do it.

In **dark mode, shadows are nearly invisible**, so elevation is expressed with
`surface-raised` instead of shadow. A component that relies on shadow alone to
read as raised will look flat in dark mode; use the surface token.

---

## 4 · Breakpoints

Mobile-first. Every style targets the smallest screen, and breakpoints only ever
add.

| Token | Min width | Represents |
|---|---:|---|
| *(base)* | 0 | **Small phones — 360–414 px is the primary case** |
| `sm` | 640 px | Large phones landscape, small tablets |
| `md` | 768 px | Tablets portrait |
| `lg` | 1024 px | Tablets landscape, small laptops |
| `xl` | 1280 px | Desktop |
| `2xl` | 1536 px | Large desktop |

These match Tailwind's defaults deliberately. They are sensible, and matching
them means no mental translation between the design system and the utility
classes — a real source of error otherwise.

**375 px is the design target**, not 320 px. Sub-360 px devices are now rare
enough that designing for them constrains the majority case; the layout should
degrade gracefully rather than being optimised for them.

---

## 5 · Layout grid

### Content width

| Token | Value | Use |
|---|---|---|
| `container-prose` | 680 px | About page, long text — caps the measure at ~70 characters |
| `container-content` | 1280 px | **Default.** Catalogue, product detail, most pages |
| `container-wide` | 1536 px | Full-width photography, hero sections |
| `container-full` | 100% | Edge-to-edge imagery |

Content is centred with `margin-inline: auto` and never exceeds
`container-content` unless it is deliberately full-bleed imagery.

### Gutters

Horizontal page padding, which must never be zero — text touching a screen edge
is the most common mobile layout defect.

| Breakpoint | Gutter |
|---|---|
| base | `space-4` (16 px) |
| `md` | `space-6` (24 px) |
| `lg` | `space-8` (32 px) |
| `xl` | `space-12` (48 px) |

### Catalogue grid

Column counts for the product grid. [responsive.md](responsive.md) covers reflow
behaviour and image aspect ratios.

| Breakpoint | Columns | Gap |
|---|---:|---|
| base (375 px) | **2** | `space-3` (12 px) |
| `sm` | 2 | `space-4` |
| `md` | 3 | `space-4` |
| `lg` | 3 | `space-6` |
| `xl` | 4 | `space-6` |
| `2xl` | 4 | `space-6` |

**Two columns on mobile, not one.** A single-column catalogue on a phone means
endless scrolling to see a handful of pieces, and jewellery is well suited to
comparison at a glance. Two columns at 375 px gives roughly a 168 px card — ample
for a ring or pendant.

**Four columns is the ceiling.** Five or more shrinks each photograph below the
size at which jewellery detail is visible, which defeats the point.

---

## 6 · Touch targets

| Rule | Value |
|---|---|
| Minimum interactive target | **44 × 44 px** |
| Minimum spacing between adjacent targets | `space-2` (8 px) |
| Android minimum | 48 × 48 dp (Material 3) — use this on Android |

44 px is the WCAG 2.5.5 / iOS guideline; Material 3 specifies 48 dp. The Android
app uses 48 dp, the website 44 px.

A target may be **visually** smaller than 44 px provided its hit area is not —
padding or a pseudo-element expands it. An icon button drawn at 24 px still needs
a 44 px touch area. This matters most for the conversion buttons in M4.12, which
are the site's entire purpose and are pressed one-handed.

---

## 7 · Rules

1. **Every spacing value comes from the scale.** No `padding: 13px`. M2's
   acceptance criteria test for this by searching component code.
2. **Prefer fewer, larger gaps** over many small ones. Whitespace is the design.
3. **`elevation-0` is the default.** Reach for shadow only when something genuinely
   floats above the page.
4. **Never let text touch a screen edge.** Gutters are not optional.
5. **Vertical rhythm follows the spacing scale too** — section padding uses
   `space-12` on mobile and `space-16`–`space-24` on desktop, not arbitrary values.
6. **Grid gaps shrink on mobile, not content padding.** At 375 px, tighten the
   gap between cards before reducing the page gutter.
7. **Don't nest containers.** One container per page section; nesting produces
   compounding padding that is hard to reason about.
