# Typography

Part of the [design system](README.md). Produced by M1.4.

Constraint from the owner: **open-source typefaces only.** The licence must
permit both web self-hosting and Android app bundling.

---

## 1 · The pairing

| Role | Typeface | Licence | Why |
|---|---|---|---|
| **Display** | **Cormorant Garamond** | SIL OFL 1.1 | A high-contrast Garamond revival. Elegant, unmistakably premium, and the register luxury jewellery retail actually uses. Carries the brand at large sizes without any decoration. |
| **UI / body** | **Inter** | SIL OFL 1.1 | Designed for screen UI at small sizes: tall x-height, open apertures, unambiguous digits. Neutral enough to disappear behind the photography, which is exactly the job. |

**SIL OFL 1.1 permits both web self-hosting and app bundling**, including
embedding in a distributed APK, with no fee and no per-domain restriction. That
satisfies M1.4's licensing requirement for both platforms. Attribution is not
required in the UI; the licence text ships alongside the font files.

### Why not the more obvious choices

| Considered | Why not |
|---|---|
| **Playfair Display** | The default "premium" serif, and legibly so — it appears on enough sites to read as a template choice rather than an identity. |
| **Bodoni Moda** | Beautiful, and its hairline strokes disintegrate on low-DPI Android screens, which is a large share of this audience. |
| **Marcellus** | A genuinely good alternative — inscriptional, solid, holds up better at small sizes than Cormorant. Worth reconsidering if Cormorant proves too delicate in M2.6. |
| **Geist** (the scaffold default) | Removed. A developer-tool typeface with no relationship to this brand. |

### The one real risk

**Cormorant Garamond is delicate.** It is high-contrast with genuinely thin
hairlines, and it weakens below roughly 28 px, especially on low-DPI screens —
which describes a meaningful share of the phones this site will be read on in
Markapur.

The mitigation is a hard rule, not a hope:

> **Cormorant Garamond is display-only, minimum 28 px, weight 500 or above.
> It is never used for body copy, never for UI labels, never below 28 px.**

Everything below 28 px is Inter. If M2.6 shows Cormorant still looking weak at
28 px on a real device, switch to **Marcellus** — a swap of one token, since
nothing references the family name directly.

### Multi-language

The PRD lists multi-language as a future enhancement, and the local audience is
largely Telugu-speaking. Neither typeface covers Telugu.

Nothing to do now, but recorded so it is not a surprise: adding Telugu means
adding **Noto Serif Telugu** and **Noto Sans Telugu** as script-specific
companions and setting them in the font stack by `unicode-range`. Both are OFL,
so the licence position does not change. Deciding this now would be premature;
knowing it costs nothing.

---

## 2 · Type scale

Base is **16 px = 1rem**. Never set a root font size below 16 px — it breaks
browser zoom expectations and reads as small on mobile.

The scale is roughly a 1.25 ratio at the top, tightening toward the body sizes
where fine control matters more than mathematical purity.

| Token | Size | Line height | Weight | Tracking | Family | Used for |
|---|---|---|---|---|---|---|
| `display-xl` | `clamp(2.25rem, 5vw, 3.5rem)` 36→56 px | 1.1 | 500 | −0.02em | Cormorant | Home hero only |
| `display-l` | `clamp(1.875rem, 4vw, 2.75rem)` 30→44 px | 1.15 | 500 | −0.015em | Cormorant | Page titles |
| `heading-l` | `1.75rem` 28 px | 1.25 | 500 | −0.01em | Cormorant | Section headings — the floor for Cormorant |
| `heading-m` | `1.375rem` 22 px | 1.35 | 600 | −0.01em | Inter | Card and subsection headings |
| `heading-s` | `1.125rem` 18 px | 1.4 | 600 | 0 | Inter | Product names in a grid |
| `body-l` | `1.125rem` 18 px | 1.6 | 400 | 0 | Inter | Product descriptions, About prose |
| `body-m` | `1rem` 16 px | 1.6 | 400 | 0 | Inter | **Default body** |
| `body-s` | `0.875rem` 14 px | 1.5 | 400 | 0 | Inter | Metadata, supporting detail |
| `caption` | `0.8125rem` 13 px | 1.45 | 400 | 0 | Inter | Image captions, fine print |
| `label` | `0.75rem` 12 px | 1.3 | 600 | 0.06em | Inter | Uppercase eyebrow labels, badges |
| `spec` | `1rem` 16 px | 1.5 | 500 | 0 | Inter, `tnum` | **Purity and weight** |

### Notes on specific tokens

- **`display-xl` and `display-l` are fluid** via `clamp()`. Jewellery pages are
  photograph-led, and a fixed 56 px title that works on desktop is oppressive at
  375 px. Fluid type solves this without a breakpoint cascade.
- **Negative tracking on Cormorant.** Garamond revivals set loose at large
  sizes; tightening pulls display text into a deliberate-looking block.
- **`label` carries positive tracking** because uppercase text at 12 px needs
  letterspacing to stay readable.
- **`spec` uses tabular numbers** (`font-variant-numeric: tabular-nums`). Weights
  and purities sit in lists and grids where proportional digits cause visible
  jitter between rows. Inter supports `tnum` properly.

### Minimum sizes

- **Body text is never below 16 px** on mobile. 14 px is for metadata, not prose.
- **12 px is the absolute floor**, and only for `label`, uppercase, weight 600.
- Product names in the catalogue grid use `heading-s` (18 px) — a product name
  is the primary thing a customer reads on a card and should not be metadata-sized.

---

## 3 · Weights to load

Loading every weight of both families would cost several hundred kilobytes
against a sub-two-second budget. Load only these:

| Family | Weights | Styles |
|---|---|---|
| Cormorant Garamond | 500 | normal |
| Inter | 400, 500, 600 | normal |

No italics at launch — nothing in the design calls for them. No 700: Inter 600
is sufficient for emphasis at these sizes, and 700 alongside the delicate serif
looks heavy-handed.

**Total four font resources.** If Inter is loaded as a variable font, that
collapses to two.

### Loading rules

- **`next/font`**, self-hosted. No runtime request to Google Fonts — it is a
  third-party connection, a privacy consideration, and a render-blocking
  dependency.
- **Subset to `latin`** at launch. Add `latin-ext` only if a real need appears.
- **`font-display: swap`**, with a metric-compatible fallback so the swap does
  not shift layout. M12.3 verifies no layout shift is attributable to fonts.
- **Preload Inter only.** It renders first paint. Cormorant appears in headings
  and can arrive a moment later without a visible penalty.

---

## 4 · Rules

1. **Cormorant never below 28 px.** The one rule in this document most likely to
   be broken by accident, and the one that most damages the brand when it is.
2. **Two families, no more.** A third typeface anywhere is a defect.
3. **Never use font size alone to convey hierarchy** — weight and colour carry
   it too, so the hierarchy survives a user's browser zoom or minimum-font-size
   setting.
4. **One `<h1>` per page**, and heading levels never skip. Enforced in M11.6.
5. **Sentence case** for all headings and buttons. No Title Case, no ALL CAPS
   except `label`.
6. **Line length caps at ~70 characters** for prose. Full-width paragraphs on a
   desktop catalogue page are unreadable; constrain the measure, not the page.
7. **Never letterspace lowercase body text.** Tracking adjustments apply only to
   display sizes and to uppercase labels.
8. **Product specs use `spec`**, not `body-m`, so digits align in lists.

---

## 5 · Android

The Compose theme in M6.2 mirrors this scale — same names, same sizes in `sp`
rather than `rem`, generated from the same token source ([ADR-0008](../adr/0008-design-tokens-single-source.md)).

Two Android-specific points:

- **`sp`, not `dp`, for all text**, so the user's system font-size preference is
  respected. Using `dp` for text is an accessibility defect.
- **The admin app barely needs Cormorant.** It is a tool, not a storefront —
  Inter carries essentially all of it. Bundling Cormorant for a handful of screen
  titles costs APK size for little gain, so M6.2 should default to Inter
  throughout and use Cormorant only if a screen genuinely warrants it.

---

## 6 · Verification

- Licence position: **SIL OFL 1.1 for both families**, permitting web
  self-hosting and APK bundling. Ship the licence files with the fonts.
- Cormorant's floor of 28 px is checked on a real low-DPI Android device in
  M2.6 — not in a desktop browser, where the hairlines look fine and the problem
  is invisible.
- M12.3 verifies fonts cause no layout shift and no render blocking.
- The fallback to **Marcellus** is a single token change if Cormorant proves too
  delicate.
