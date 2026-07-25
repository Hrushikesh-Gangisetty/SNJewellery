# Colour

Part of the [design system](README.md). Produced by M1.3.

Direction from the owner: **white, gold, black, and subtle neutral tones.** Avoid
bright or flashy colours that distract from the jewellery.

**Every ratio below is measured**, not asserted — computed with the WCAG 2.1
relative-luminance formula. M12.8 re-verifies against these numbers in the
shipped site, so they must stay accurate. If a value changes, recompute.

---

## 1 · The gold problem

The single most important finding in this milestone, and worth understanding
before using any gold anywhere.

**Gold cannot be used for text on white.** Measured:

| Gold | On white | Verdict |
|---|---:|---|
| `#D4AF37` classic gold | **2.10:1** | Fails everything |
| `#C9A227` | **2.42:1** | Fails everything |
| `#B8860B` dark goldenrod | **3.25:1** | Large text / UI only |
| `#A67C00` | **3.82:1** | Large text / UI only |
| `#8B6914` | **5.09:1** | Passes AA body |
| `#856404` | **5.49:1** | Passes AA body |

Anything dark enough for body text on white has stopped looking like gold and
started looking like olive-brown. Anything that looks properly golden fails
contrast by a wide margin.

**So there are two gold tokens, with different jobs, and they are not
interchangeable:**

| Token | Value | Job |
|---|---|---|
`accent` | `#C9A227` | **Decorative only** on light surfaces. Fills, rules, hover washes, and as a background *behind* dark text. Never text, never an icon that carries meaning, never the sole indicator of an interactive control. |
| `accent-text` | `#856404` | Anything semantic — gold-coloured text, meaningful icons, links.

`accent` at 2.42:1 on white is below the 3:1 that WCAG 1.4.11 requires for
non-text contrast. **That is acceptable precisely because it is decorative** —
1.4.11 applies to UI component boundaries and to graphics needed to understand
content, neither of which `accent` may be used for. This is a rule, not a
loophole: if gold ever becomes the only thing distinguishing a button from its
background, the design is wrong.

Gold behaves much better in dark mode — `#D4AF37` on `#0F0F0F` measures
**9.12:1** — so dark mode may use gold far more freely.

---

## 2 · Light palette

| Token | Value | Role |
|---|---|---|
| `surface` | `#FFFFFF` | Page background |
| `surface-raised` | `#FAF9F7` | Cards, panels — warm, not blue-grey |
| `surface-sunken` | `#F5F3F0` | Recessed areas, image placeholders, table stripes |
| `text-primary` | `#1C1917` | Headings, product names, body |
| `text-secondary` | `#57534E` | Supporting copy, metadata |
| `text-muted` | `#6B645F` | Captions, least-emphasis labels |
| `border` | `#E7E5E4` | Decorative dividers, card edges |
| `border-strong` | `#D6D3D1` | Emphasised separation |
| `border-interactive` | `#96908B` | **Form fields, control outlines** — the only border token that meets 3:1 |
| `accent` | `#C9A227` | Brand gold — decorative only (§1) |
| `accent-text` | `#856404` | Gold for text and meaningful icons |
| `on-accent` | `#1C1917` | Text placed on a gold fill |
| `focus` | `#1C1917` | Focus ring |
| `success` | `#15803D` | Confirmation |
| `danger` | `#B91C1C` | Destructive actions, errors |

### Measured — light mode, text on surfaces

AA body text requires **4.5:1**. All pass.

| Text token | on `surface` | on `surface-raised` | on `surface-sunken` |
|---|---:|---:|---:|
| `text-primary` | 17.49 | 16.62 | 15.79 |
| `text-secondary` | 7.63 | 7.25 | 6.89 |
| `text-muted` | 5.81 | 5.52 | **5.25** |
| `accent-text` | 5.49 | 5.22 | **4.96** |
| `success` | 5.02 | 4.77 | **4.53** |
| `danger` | 6.47 | 6.15 | 5.84 |

The `surface-sunken` column is why these values are what they are. An earlier
draft used `#78716C` for `text-muted`, which passes on white at 4.80 but drops
to **4.33 on the sunken surface** — a fail. Darkening to `#6B645F` fixes it.
**Always check the worst surface, not white.**

`success` on `surface-sunken` is 4.53 — passing with almost no margin. Do not
place success text on the sunken surface without re-measuring if either value
moves.

### Measured — light mode, non-text

Non-text contrast (WCAG 1.4.11) requires **3:1** for UI component boundaries.

| Pair | Ratio | Verdict |
|---|---:|---|
| `border-interactive` on `surface` | **3.15** | Passes — use for form fields |
| `border-strong` on `surface` | 1.49 | Decorative only |
| `border` on `surface` | 1.26 | Decorative only |
| `accent` on `surface` | 2.42 | Decorative only (§1) |
| `on-accent` on `accent` | **7.23** | Dark text on gold is safe |
| `focus` on `surface` | 17.49 | Focus ring is unmissable |

---

## 3 · Dark palette

Required by the PRD for the Android app, and honoured on the website.

| Token | Value | Role |
|---|---|---|
| `surface` | `#0F0F0F` | Not pure black — pure black against OLED causes harsh edges and smearing |
| `surface-raised` | `#1A1917` | Cards, panels |
| `surface-sunken` | `#080808` | Recessed areas |
| `text-primary` | `#FAF9F7` | Not pure white — reduces halation on dark |
| `text-secondary` | `#A8A29E` | Supporting copy |
| `text-muted` | `#8A8580` | Captions |
| `border` | `#292524` | Dividers |
| `border-strong` | `#3F3B39` | Emphasised separation |
| `border-interactive` | `#6B6560` | Form fields — meets 3:1 |
| `accent` | `#D4AF37` | Brand gold — usable far more freely here (9.12:1) |
| `accent-text` | `#E0BC4A` | Gold text |
| `on-accent` | `#0F0F0F` | Text on a gold fill |
| `focus` | `#FAF9F7` | Focus ring |
| `success` | `#4ADE80` | Confirmation |
| `danger` | `#F87171` | Errors |

### Measured — dark mode, text on surfaces

All pass 4.5:1.

| Text token | on `surface` | on `surface-raised` | on `surface-sunken` |
|---|---:|---:|---:|
| `text-primary` | 18.22 | 16.70 | 19.03 |
| `text-secondary` | 7.60 | 6.97 | 7.94 |
| `text-muted` | 5.25 | **4.81** | 5.48 |
| `accent-text` | 10.46 | 9.59 | 10.93 |
| `success` | 11.00 | 10.08 | 11.49 |
| `danger` | 6.93 | 6.35 | 7.24 |

### Measured — dark mode, non-text

| Pair | Ratio | Verdict |
|---|---:|---|
| `border-interactive` on `surface` | **3.34** | Passes |
| `accent` on `surface` | **9.12** | Passes — gold is safe in dark mode |
| `on-accent` on `accent` | 9.12 | Safe |
| `focus` on `surface` | 18.22 | Unmissable |

---

## 4 · Rules

1. **Neutrals are warm, not grey.** Every neutral carries a slight warm cast
   (`#FAF9F7`, `#57534E`, `#E7E5E4`) rather than a blue-grey one. Cool greys make
   gold look green and make jewellery photography look clinical.

2. **The palette is deliberately small.** Three surfaces, three text weights,
   three borders, two golds, three status colours. If a design seems to need a
   fourth surface, the problem is usually the layout, not the palette.

3. **Colour never carries meaning alone.** The "Sold" state is a badge with the
   word *Sold* in it, not a coloured tint. Required for accessibility, and it
   also survives being screenshotted and forwarded on WhatsApp.

4. **No gradients on brand surfaces.** Especially not gold gradients — see the
   anti-patterns in [brand.md](brand.md). The photography supplies richness.

5. **Product photography is never tinted, overlaid, or filtered.** No colour wash
   over an image, no dark scrim except where text must sit over an image, and
   then only as a measured, minimum-necessary gradient.

6. **Status colours are for status.** `success` and `danger` never appear as
   decoration, and `danger` is reserved for destructive or genuinely erroneous
   states — not for "Sold", which is neither.

7. **Check the worst surface.** A text colour is only safe if it passes on
   `surface-sunken`, not just on white. §2 records exactly why.

---

## 5 · Reproducing these numbers

WCAG 2.1 relative luminance, then
`(lighter + 0.05) / (darker + 0.05)`:

```js
const lin = (c) => {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
};
const lum = (hex) => {
  const n = parseInt(hex.slice(1), 16);
  return 0.2126 * lin((n >> 16) & 255)
       + 0.7152 * lin((n >> 8) & 255)
       + 0.0722 * lin(n & 255);
};
const ratio = (a, b) => {
  const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};
```

A checked-in script that validates the whole palette is a candidate for M1.6,
alongside the token generator — so a token change that breaks contrast fails
loudly rather than silently.

---

## 6 · Open

**These values were derived from the owner's stated preference, not sampled from
real brand assets**, because no logo, signage, or packaging has been supplied
yet ([brand.md](brand.md) §7).

If existing material uses a specific gold, reconcile `accent` against it **before
M2.4 consumes the tokens** — and recompute every ratio in this document, since a
different gold changes all of them. That is the one avoidable rework in this
milestone.
