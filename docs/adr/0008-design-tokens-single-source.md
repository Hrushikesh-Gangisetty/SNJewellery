# ADR-0008: Design tokens as a shared single source of truth

- **Status:** ✅ **Accepted** — 2026-07-25
- **Date:** 2026-07-25
- **Deciders:** Hrushikesh Gangisetty
- **Affects:** M1.6, M2.4, M6.2

## Context

Two clients on two platforms must look like one brand. The website consumes design values through Tailwind; the Android app consumes them through a Compose Material 3 theme. The same colour, type step, spacing value, radius, and animation duration has to exist in both, and stay the same in both.

Left alone, this decays predictably. Someone adjusts a hover colour in Tailwind, the Compose theme keeps the old value, and six months later the two surfaces are visibly different in ways nobody decided. Preventing that drift is most of the reason a design system exists at all.

M1 produces the token definitions as documentation. This ADR is about the **mechanism** by which two platforms consume them.

There is a real asymmetry worth noting: the two platforms share very little UI. The website is a public catalogue; the app is an admin tool. They share brand values — colour, type, spacing, motion — but almost no components. So the tokens are the entire shared surface.

## Decision

**Accepted:** define tokens once in a **platform-neutral machine-readable file** (JSON) under `docs/design/`, and generate the platform artefacts from it — a CSS `@theme` block for `web/`, and a Kotlin theme file for `android/`.

The generation step would be a small script committed to the repository and run when tokens change, with its output committed so neither build depends on the generator at build time.

The argument for generation over hand-mirroring is narrow but decisive: **hand-mirroring has no failure signal.** A hand-mirrored token that drifts produces no error, no failed build, and no visible symptom until someone notices two screens disagree. Generation makes drift structurally impossible for the values it covers.

The counter-argument is real: this is a solo project, tokens change rarely after M1, and a generator is machinery to maintain for a problem that might not materialise. **A defensible alternative is to hand-mirror with a documented rule that token changes update both platforms in the same commit** — the same rule already applied to schema changes ([docs/database/](../database/)). That relies on discipline where the other relies on tooling.

**This needs your decision** — it is Open Question 10.

## Consequences

### If generated (recommended)

**Easier:** drift is impossible for generated values; a token change is one edit; the token file is reviewable as a single artefact; adding a third consumer later is free.

**Harder:** a generator to write and maintain; a step someone can forget to run, so a check that generated output is current is worth having; token names must survive translation into both Tailwind's and Compose's naming conventions, which constrains naming.

### If hand-mirrored

**Easier:** nothing to build; each platform uses its idiomatic form directly; no generation step in any workflow.

**Harder:** drift is a matter of discipline, and discipline fails silently; every token change is two edits in two languages; reviewing whether they match means reading both.

### What this commits us to

Either way, the rule stands: **no component on either platform may use a raw colour, size, radius, or duration.** M2's and M6's acceptance criteria both test this by searching component code for literals. That rule is what gives the tokens their value; the mechanism only determines how the tokens stay in step.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Hand-mirror into each platform | Genuinely viable for a solo project — the recommendation is not overwhelming. Rejected because silent drift is the specific failure a design system exists to prevent. |
| A full design-token toolchain (Style Dictionary) | Purpose-built and battle-tested, but heavyweight for one palette, one type scale, and two consumers that share no components. |
| Figma as the source of truth with token export | Correct at team scale with a designer in the loop. Here it adds a tool dependency and a manual export step to a solo build. |
| Tailwind config as the source, Compose reads it | Makes the website's tooling authoritative over the Android app for no principled reason, and Compose cannot consume a JS config without generation anyway — so this is generation with a worse source format. |

## Implementation note (added M2.1)

**Tailwind v4 is installed**, and it configures its theme from CSS rather than
from `tailwind.config.ts`. Tokens on the web side therefore live in an `@theme`
block in `web/app/globals.css` as CSS custom properties.

This shifts the recommendation's cost slightly in favour of generation. CSS
custom properties and Compose theme values are both simple key/value forms, so
a generator emitting the two from one JSON source is a smaller job than it
would have been against a JS config object. It also means the web side needs no
build step of its own — the CSS *is* the config.

`web/app/globals.css` currently contains only the Tailwind import and a comment
recording that tokens arrive in M2.4. The scaffold's default palette and Geist
fonts were deliberately removed rather than kept, so that M1 is not designing
against values it would have to undo.

## Open sub-questions

- ~~The mechanism itself~~ — **decided 2026-07-25: single source of truth, generated.**
- Token naming convention. Must read naturally as both a Tailwind utility and a Compose property. Decided in M1.6.
- Whether motion durations and easings are tokens too, or documentation only. They should be tokens — M12.8 and both platforms' reduced-motion rules depend on them being consistent.
- Whether Material 3's dynamic colour is honoured on Android or overridden by the brand palette. M6.2 decides; a luxury identity usually overrides.

## References

- [docs/design/](../design/) — the design system this governs
- [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — M1.6, M2.4, M6.2, and Open Question 10
- [prd.md](../../prd.md) — Website Requirements; Android Requirements (Material Design 3, dark mode)
