# Accessibility

Part of the [design system](README.md). Produced by M1.11.

**Target: WCAG 2.1 Level AA**, on both the website and the Android app.

Every rule below is written to be *checkable*. M12.6–M12.8 verify against this
document rather than restating it, so a rule that cannot be checked does not
belong here.

---

## 1 · Colour and contrast

| Rule | Threshold | Verified |
|---|---|---|
| Body text on any surface | **≥ 4.5:1** | Token generator, 32 pairs, blocks generation on failure |
| Large text (≥ 24 px, or ≥ 18.66 px bold) | ≥ 3:1 | Same |
| UI component boundaries and meaningful icons | **≥ 3:1** | Same |
| Focus indicator against adjacent colour | ≥ 3:1 | `focus` measures 17.49:1 light, 18.22:1 dark |

Measured values are recorded in [colour.md](colour.md) and enforced by
`tools/generate-tokens.mjs`, which **refuses to emit a palette that fails**.

Two consequences already baked into the tokens:

- **`accent` gold is decorative only** on light surfaces (2.42:1). It may never be
  text, a meaningful icon, or the sole boundary of a control. `accent-text`
  (5.49:1) exists for anything semantic.
- **`border` and `border-strong` are decorative** (1.26:1, 1.49:1).
  `border-interactive` (3.15:1) is the only border token permitted on a form field
  or control outline.

### Colour is never the only signal

- "Sold" is **the word Sold** in a badge, not a tint.
- Featured is a label, not a gold border alone.
- Form errors carry **text**, not just a red outline.
- Active filters are chips with text, not colour-only highlights.

Check: view any screen in greyscale. If information disappears, it violated this.

---

## 2 · Keyboard

| Rule | Detail |
|---|---|
| Everything interactive is reachable by Tab | No `tabindex` above 0, ever |
| Tab order follows visual order | Guaranteed by the DOM-order rule in [responsive.md](responsive.md) §4 |
| Visible focus on every focusable element | 2 px `focus` outline, 2 px offset, emitted globally in generated CSS |
| `:focus-visible`, not `:focus` | So mouse users do not see rings, keyboard users always do |
| Focus is never removed | `outline: none` without a replacement is a defect |
| Skip link is the first focusable element | Jumps to `<main>` |
| Esc closes drawer, sheet, modal | |
| Focus is trapped in modals and returns to the trigger on close | |
| Gallery is arrow-key navigable | |
| No keyboard trap anywhere | |

**The journey that must work end to end by keyboard alone:**
home → catalogue → filter → product → enquiry. Verified in M12.6.

---

## 3 · Screen readers

| Rule | Detail |
|---|---|
| **Every product image has alt text derived from real product data** | Pattern: `"{name} — {purity} {category}"`. Never empty, never the filename, never "image" |
| Decorative images have `alt=""` | So they are skipped, not announced |
| One `<h1>` per page, no skipped levels | Enforced in M11.6 |
| Landmarks present | `<header>`, `<nav>`, `<main>`, `<footer>` |
| Buttons say what they do | "Ask about Temple Necklace on WhatsApp", not "Enquire" |
| Icon-only buttons have `aria-label` | |
| Form inputs have real `<label>` | Placeholder is not a label |
| Errors linked via `aria-describedby` | And announced |
| Loading announced via `aria-live="polite"` | |
| Result counts announced when filters change | "12 pieces found" |
| Gallery announces position | "Image 2 of 4" |
| `aria-current` on the active nav item and page | |

A screen-reader walkthrough of the primary journey is documented in M12.7 — the
deliverable is the recorded walkthrough, not an assertion that it works.

---

## 4 · Touch and pointer

| Rule | Value |
|---|---|
| Minimum touch target, web | **44 × 44 px** |
| Minimum touch target, Android | **48 × 48 dp** |
| Minimum gap between adjacent targets | 8 px |
| Visual size may be smaller than hit area | Expand with padding, not by shrinking the target |
| No hover-only functionality | Anything reachable by hover must be reachable by tap and by keyboard |
| No gesture-only functionality | A swipeable gallery also needs buttons or keys |

The conversion buttons matter most here — they are the site's purpose and are
pressed one-handed, sometimes while walking.

---

## 5 · Motion

- `prefers-reduced-motion: reduce` honoured **globally** in generated CSS, so no
  component can reintroduce motion by forgetting to check.
- Reduced motion means **reduced, not broken** — every state change stays
  perceivable.
- Nothing flashes more than three times per second.
- No autoplaying motion. No parallax.

See [motion.md](motion.md).

---

## 6 · Text and zoom

- Root font size is never below 16 px.
- **Nothing breaks at 200% browser zoom** — no clipping, no horizontal scroll, no
  overlap.
- Text resizing to 200% via user settings does not break layouts: use `rem`, never
  fixed `px` heights on text containers.
- Line length capped at ~70 characters for prose.
- Android text uses `sp`, never `dp` — otherwise the user's font-size preference is
  ignored, which is an accessibility defect.
- No text baked into images. Product photographs contain no text.

---

## 7 · Forms — the Android app

The owner uses this app daily; it deserves the same standard as the public site.

- Every field has a visible label and, where the format is not obvious, hint text.
- Errors are specific: "Weight must be a number in grams", not "Invalid input".
- Errors appear next to the field, and the first error receives focus.
- Required versus optional is stated, not implied.
- **Content descriptions on every image tile and icon button.**
- The form is fully usable with the keyboard open, which halves available height.
- Destructive actions (Delete) always confirm.
- Nothing depends on colour alone to show state.

---

## 8 · What gets verified, and where

| Check | Milestone |
|---|---|
| Contrast on every token pair | **Continuous** — token generator, blocks on failure |
| Keyboard journey end to end | M12.6 |
| Focus visible at every step | M12.6 |
| ARIA correctness, screen-reader walkthrough documented | M12.7 |
| Contrast and reduced motion in the shipped site | M12.8 |
| Lighthouse Accessibility > 95, axe scan with no critical issues | M12 |
| Heading hierarchy, alt text | M11.6, M11.7 |
| Touch targets | M2, M4, M6 component reviews |

---

## 9 · Non-negotiables

Five rules that, if broken, mean the work is not done:

1. **Focus is always visible.** Never remove an outline without replacing it.
2. **Colour is never the only signal.**
3. **Every product image has meaningful alt text from real data.**
4. **The full enquiry journey works by keyboard alone.**
5. **Contrast thresholds are enforced by tooling**, not by good intentions.

The last is why the token generator validates rather than the design system merely
recommending: accessibility that depends on remembering does not survive contact
with a deadline.
