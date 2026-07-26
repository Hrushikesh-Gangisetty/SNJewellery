# Design System

**The single visual and interaction reference for both the website and the Android app.**

Produced by **M1** in [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — the milestone that runs before any application code is written. Every colour, type step, spacing value, radius, elevation, and animation duration used anywhere in the project traces back to a token defined here.

## Why this exists as its own milestone

The PRD asks for a "clean and premium browsing experience" for a luxury product, and then specifies no visual direction at all. Two things follow if that gap is left open:

1. The visual language gets decided inside component code, incrementally, by whoever writes the first component — which is how a jewellery catalogue ends up looking like a generic admin template.
2. It gets decided **twice** — once in Tailwind, once in Compose — and the two drift apart immediately.

Defining it once, first, as documentation, costs one milestone and removes both problems. It also front-loads a decision the shop owner has a genuine stake in, at the point where changing it is free.

## Documents

| Document | Contents | Task | Status |
|---|---|---|---|
| [brand.md](brand.md) | Shop identity, brand attributes, logo usage, tone of voice, anti-patterns | M1.1, M1.2 | ✅ |
| [colour.md](colour.md) | Palette with semantic roles, light and dark, **measured** contrast ratios | M1.3 | ✅ |
| [typography.md](typography.md) | Typefaces, type scale, weights, roles, licensing confirmation | M1.4 | ✅ |
| [layout.md](layout.md) | Spacing, radii, elevation, **breakpoints**, layout grid, touch targets | M1.5 | ✅ |
| [tokens.json](tokens.json) | Platform-neutral token definition plus the generator | M1.6 | ✅ |
| [components.md](components.md) | Full component inventory with variants and every state | M1.7 | ✅ |
| [responsive.md](responsive.md) | Component reflow behaviour, image aspect ratios | M1.8 | ✅ |
| [motion.md](motion.md) | Duration scale, easings, permitted animations, reduced-motion rule | M1.9 | ✅ |
| [ux.md](ux.md) | Product photography standards, CTA hierarchy, loading/empty/error patterns | M1.10 | ✅ |
| [accessibility.md](accessibility.md) | WCAG 2.1 AA target and the concrete rules that follow from it | M1.11 | ✅ |

**M1 is complete.** All eleven documents plus the token pipeline exist, and
`web/` and `android/` both have generated artefacts to build against.

**Note on `layout.md` vs `responsive.md`:** breakpoint *values*, the spacing
scale, container widths, grid columns, and touch-target minimums live in
[layout.md](layout.md), because the layout grid is defined against them.
[responsive.md](responsive.md) covers how components *behave* at each breakpoint
without redefining the numbers.

## How each platform consumes this

- **Web** (`web/`) — tokens become Tailwind theme extensions in M2.4. No component may use a colour, size, or duration literal.
- **Android** (`android/`) — tokens become a Compose Material 3 theme in M6.2, with light and dark schemes. Same rule.

Both are kept in step by generation from one source — [ADR-0008](../adr/0008-design-tokens-single-source.md), **Accepted**. See the token pipeline section below.

## Still awaited

The shop name and design direction have been supplied. Outstanding, none of it blocking:

| Item | Affects | Until then |
|---|---|---|
| **Logo SVG**, monogram SVG, reversed version | Header, footer, favicon, social previews | Raster mockup received 2026-07-26; wordmark renders until vectors arrive — see [brand.md](brand.md) §4 |
| **Signage / packaging photos** | Confirms whether the mockup's teal is a brand colour | Gold now sampled from the logo and all 32 ratios revalidated. Teal deliberately not adopted — [brand.md](brand.md) §4 |
| **5–10 unedited product photographs** | `ux.md` (M1.10) photography standard, and the M7.6 compression target | M1.10 writes the standard against configurable guidelines |
| Year established, business history, certifications | About page (M4.11) | Sections hide cleanly, per [ADR-0010](../adr/0010-configurable-site-content.md) |

The gold reconciliation that carried rework risk is **done** — `accent` came from
the logo before any component consumed it, which is exactly the window ADR-0009
and M1 were sequenced to protect.

## Notes for whoever fills this in

- Jewellery photography is the content. The design system's job is to get out of its way — restrained neutrals, generous whitespace, and motion that supports the imagery rather than competing with it.
- The photography standard in `ux.md` is the one document the shop owner will use directly. Write it for someone holding a phone, not for a designer.
- Record contrast ratios as measured numbers, not as assertions. M12.8 verifies against them.

## Token pipeline (M1.6)

[tokens.json](tokens.json) is the **single source of truth** for every design
value. Both platform artefacts are generated from it:

```bash
node tools/generate-tokens.mjs           # write the artefacts
node tools/generate-tokens.mjs --check   # verify they are current
```

| Generated file | Consumed by |
|---|---|
| `web/app/globals.css` | Tailwind v4 `@theme` block — M2.4 |
| `android/design-tokens/Tokens.kt` | Compose theme — M6.2 |

**Never edit a generated file by hand.** Both carry a banner saying so. Edit
`tokens.json` and re-run. `npm run verify` in `web/` runs `--check`, so a
hand-edit or a stale artefact fails the build.

### The contrast guard

Before emitting anything, the generator validates every contrast assertion in
`tokens.json` against the WCAG 2.1 formula — 32 pairs across light and dark. **A
palette change that breaks accessibility fails the generator and writes
nothing.** This is the concrete payoff of generating rather than hand-mirroring,
and it is verified: reintroducing the two defects found during M1.3 (a muted grey
that fails on the sunken surface, and gold used as body text) both correctly
fail with a non-zero exit.

`accent` is deliberately excluded from the light-mode non-text checks. At 2.38:1
on white it is decorative-only by design — see [colour.md](colour.md) §1.
