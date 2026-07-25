# ADR-0001: Single monorepo for web, Android, and backend

- **Status:** ✅ Accepted
- **Date:** 2026-07-25
- **Deciders:** Hrushikesh Gangisetty
- **Affects:** M0, and the layout of every milestone thereafter

## Context

The platform has three deliverables: a Next.js website, a Kotlin/Compose Android app, and a Supabase backend (migrations, RLS policies, seed data). They could live in three repositories or one.

The decisive constraint is that **both clients code against one database schema**. The website reads it through generated TypeScript types; the Android app reads it through hand-written Kotlin data classes. A schema change is only correct when the migration, the TypeScript types, and the Kotlin models all change together — see [docs/database/](../database/).

The PRD does not specify repository layout. It describes a single shop owner and a solo build, which makes cross-repository coordination overhead pure cost with no offsetting benefit.

## Decision

We will use **one repository** containing `web/`, `android/`, and `supabase/`, plus shared documentation in `docs/`.

A schema change is expected to be a single commit touching the migration, the generated TypeScript types, and the Kotlin models together. That is only possible in one repository, and it is what makes the both-clients rule enforceable rather than aspirational.

## Consequences

### What this makes easier

- A schema change is one atomic, reviewable commit across all three surfaces.
- The design system in `docs/design/` is genuinely shared rather than copied into two repositories and drifting.
- One issue tracker, one branch history, one place to look.
- Cross-cutting documentation — the PRD, this plan, the ADRs — has an unambiguous home.

### What this makes harder

- CI must scope itself by path, or every push runs an Android build for a CSS change.
- Vercel needs explicit monorepo build configuration to find `web/` (M5.2).
- The repository holds two unrelated toolchains — Node and Gradle — so a clone is heavier and `.gitignore` covers both ecosystems.
- Tooling that assumes a project at the repository root sometimes needs pointing.

### What this commits us to

Splitting later is mechanical for the code but loses the shared history of exactly the cross-cutting schema changes that motivated this choice. Not expensive, but not free.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Three separate repositories | Schema drift between clients becomes the default rather than something you have to work at. A three-repository change is three PRs that can each merge without the others. |
| Two repositories (clients together, backend separate) | Splits exactly the wrong seam — the schema is what the clients share most tightly. |
| Monorepo with a tool (Nx, Turborepo) | Real value for many JS packages sharing code. Here there is one JS package and one Gradle project, which do not share code. Configuration cost without payoff. |

## Open sub-questions

- Whether path-scoped CI is needed at all, given a solo build. Revisit if CI time becomes a nuisance.
- Whether `prd.md` and `DEVELOPMENT_PLAN.md` should move into `docs/`. Currently at the root deliberately — see [docs/README.md](../README.md).

## References

- [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — Planning Decisions, and the repository layout
- [docs/database/](../database/) — the schema contract and the both-clients rule
