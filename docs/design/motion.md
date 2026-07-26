# Motion

Part of the [design system](README.md). Produced by M1.9.

**Duration and easing values are tokens** in [tokens.json](tokens.json), generated
into both platforms. They are not written by hand in components.

---

## 1 · The principle

**Motion supports the photography. It never performs.**

The jewellery is the content. Every animation either helps a customer understand
what changed, or it competes with the product for attention. There is no third
category. [brand.md](brand.md) rules out sparkle, shine, and shimmer effects
explicitly — this document is where that becomes concrete.

The test for any proposed animation: *if this were removed, would the customer be
confused?* If no, remove it.

---

## 2 · Duration scale

| Token | Value | Use |
|---|---:|---|
| `instant` | 0 ms | Immediate state change — checkbox, toggle knob |
| `fast` | 120 ms | Hover, focus ring, small colour change |
| `base` | 200 ms | **Default.** Most transitions |
| `slow` | 320 ms | Drawer, bottom sheet, modal |
| `deliberate` | 480 ms | Rare. Only where a long distance is travelled |

**These are short on purpose.** Long, showy transitions are the single clearest
signal of a site trying to look expensive rather than being well made — and they
cost real time on every interaction. Nothing exceeds 480 ms.

Anything above `slow` needs a stated reason.

## 3 · Easing

| Token | Curve | Use |
|---|---|---|
| `standard` | `cubic-bezier(0.2, 0, 0, 1)` | **Default.** Anything moving within the viewport |
| `decelerate` | `cubic-bezier(0, 0, 0, 1)` | Elements entering — drawer opening, image appearing |
| `accelerate` | `cubic-bezier(0.3, 0, 1, 1)` | Elements leaving — drawer closing, dismissal |

Never `linear` for anything spatial; it reads mechanical. Never `ease-in-out` for
entrances; it makes them feel sluggish at the start.

---

## 4 · What may animate

| Interaction | Property | Duration | Easing |
|---|---|---|---|
| Hover / focus on a card or button | `background-color`, `border-color`, `box-shadow` | `fast` | `standard` |
| Focus ring appearing | `outline` | `instant` | — |
| Product card entering the grid | `opacity`, `transform: translateY(8px)` | `base` | `decelerate` |
| Gallery image change | `opacity` | `base` | `standard` |
| Mobile drawer / filter sheet open | `transform` | `slow` | `decelerate` |
| …close | `transform` | `base` | `accelerate` |
| Skeleton shimmer | `opacity` pulse | `deliberate`, looped | `standard` |
| Accordion / disclosure | `height`, `opacity` | `base` | `standard` |
| Snackbar / toast in | `transform`, `opacity` | `base` | `decelerate` |
| Upload progress bar | `width` | `fast` | `linear` (the one valid use — it tracks real progress) |

### Entrance animation is restrained on purpose

Product cards fade in with an **8 px** rise — not 40 px, not staggered across the
grid in a cascade. A visible stagger on a catalogue of 500 products is a delay
imposed on someone trying to browse.

**Above-the-fold content does not animate in at all.** Animating the hero or the
first row of products delays the customer's first sight of the merchandise and
harms LCP. Entrance animation applies only to content arriving after
interaction — pagination, filtering, lazy-loaded rows.

---

## 5 · What must never animate

- **Product photographs themselves** — no Ken Burns drift, no zoom-on-hover that
  crops the piece, no parallax.
- **Anything sparkly.** No glints, shimmer overlays, animated gradients, or lens
  flares on gold.
- **Page-level transitions between routes.** They delay content for decoration.
- **Autoplaying carousels.** Ruled out in [brand.md](brand.md).
- **Scroll-jacking or smooth-scroll hijacking.**
- **Layout-shifting properties** — never animate `width`, `height`, `top`, `left`,
  or `margin` on anything in flow. Use `transform` and `opacity`, which are
  compositor-only. The accordion `height` case above is the exception and must be
  contained so it cannot shift surrounding content.
- **Number counters** ticking up on dashboard tiles. The owner wants the count,
  not a performance.

---

## 6 · Reduced motion

`prefers-reduced-motion: reduce` is honoured **globally**, in generated CSS, not
per component:

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

A global rule means a component that forgets to check cannot reintroduce motion.
Emitted by the token generator, so it cannot be removed by accident.

**Reduced motion means reduced, not broken.** Every state change must still be
perceivable — a drawer that no longer slides must still appear, and a skeleton
that no longer shimmers must still read as a placeholder. Verified in M12.8.

On **Android**, the equivalent is `Settings.Global.ANIMATOR_DURATION_SCALE`; when
it is 0, skip animations rather than running them at zero duration.

---

## 7 · Rules

1. **Durations and easings come from tokens.** A raw `300ms` in a component is a
   defect. M2's acceptance criteria search for raw millisecond values.
2. **`transform` and `opacity` only**, for anything performance-sensitive.
3. **Nothing above the fold animates on load.**
4. **No animation exceeds 480 ms.**
5. **Motion never conveys information alone** — if an animation communicates
   something, a static state must communicate it too.
6. **Framer Motion is not shipped where it is not used.** M12.2 verifies this.
   Most of the above is achievable with CSS transitions and needs no library at
   all; reach for Framer Motion only for orchestration CSS cannot express.
