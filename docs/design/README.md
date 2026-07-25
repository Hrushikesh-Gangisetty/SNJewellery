# Design System

**The single visual and interaction reference for both the website and the Android app.**

Produced by **M1** in [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — the milestone that runs before any application code is written. Every colour, type step, spacing value, radius, elevation, and animation duration used anywhere in the project traces back to a token defined here.

## Why this exists as its own milestone

The PRD asks for a "clean and premium browsing experience" for a luxury product, and then specifies no visual direction at all. Two things follow if that gap is left open:

1. The visual language gets decided inside component code, incrementally, by whoever writes the first component — which is how a jewellery catalogue ends up looking like a generic admin template.
2. It gets decided **twice** — once in Tailwind, once in Compose — and the two drift apart immediately.

Defining it once, first, as documentation, costs one milestone and removes both problems. It also front-loads a decision the shop owner has a genuine stake in, at the point where changing it is free.

## Documents

| Document | Contents | Task |
|---|---|---|
| `brand.md` | Shop identity, brand attributes, logo usage, tone of voice | M1.1, M1.2 |
| `colour.md` | Palette with semantic roles, light and dark, measured contrast ratios | M1.3 |
| `typography.md` | Typefaces, type scale, weights, roles, licensing confirmation | M1.4 |
| `tokens.*` | Platform-neutral token definition plus per-platform consumption path | M1.5, M1.6 |
| `components.md` | Full component inventory with variants and every state | M1.7 |
| `responsive.md` | Breakpoints, grid, image aspect ratios, touch targets | M1.8 |
| `motion.md` | Duration scale, easings, permitted animations, reduced-motion rule | M1.9 |
| `ux.md` | Product photography standards, CTA hierarchy, loading/empty/error patterns | M1.10 |
| `accessibility.md` | WCAG 2.1 AA target and the concrete rules that follow from it | M1.11 |

## How each platform consumes this

- **Web** (`web/`) — tokens become Tailwind theme extensions in M2.4. No component may use a colour, size, or duration literal.
- **Android** (`android/`) — tokens become a Compose Material 3 theme in M6.2, with light and dark schemes. Same rule.

The mechanism for keeping both in step — hand-mirrored, or generated from one machine-readable source — is [ADR-0008](../adr/0008-design-tokens-single-source.md) and is currently **Proposed**. It needs a decision before M1.6.

## Blocked on

**Open Question 9** in [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md#risks--open-questions) — the shop's exact registered name and any existing brand assets (logo, signage, print material, existing social presence). M1.1 cannot start without it. The repository directory is named `SNJewellery`, which is not a confirmed brand name.

Designing an identity from scratch and then discovering an established one exists means discarding this milestone's output, so this is the earliest blocking question in the project.

## Notes for whoever fills this in

- Jewellery photography is the content. The design system's job is to get out of its way — restrained neutrals, generous whitespace, and motion that supports the imagery rather than competing with it.
- The photography standard in `ux.md` is the one document the shop owner will use directly. Write it for someone holding a phone, not for a designer.
- Record contrast ratios as measured numbers, not as assertions. M12.8 verifies against them.
