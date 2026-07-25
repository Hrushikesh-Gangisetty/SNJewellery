# Architecture Decision Records

Each ADR records one architectural decision: the context that forced it, the decision, its consequences, and the alternatives rejected. They exist so that a decision is made once and can be re-examined deliberately rather than re-litigated by accident.

Use [0000-adr-template.md](0000-adr-template.md) for new records.

## Records

| # | Title | Status | Affects |
|:---:|---|---|---|
| [0001](0001-monorepo.md) | Single monorepo for web, Android, and backend | ✅ Accepted | M0, all |
| [0002](0002-nextjs-app-router.md) | Next.js 15 App Router for the customer website | ✅ Accepted | M2, M4, M11, M12 |
| [0003](0003-supabase-backend.md) | Supabase as the backend platform | ✅ Accepted | M3, all |
| [0004](0004-authentication-and-roles.md) | RLS as the security boundary; email/password admin auth | ✅ Accepted | M3, M6 |
| [0005](0005-image-storage-and-renditions.md) | Supabase Storage with transform-derived renditions | ✅ Accepted | M3.6, M7, M12 |
| [0006](0006-cache-revalidation-strategy.md) | Cache revalidation strategy | 🟡 **Proposed** | M4.7, M9 |
| [0007](0007-android-architecture.md) | Android architecture, DI, and HTTP client | 🟡 **Proposed** | M6, M7, M8 |
| [0008](0008-design-tokens-single-source.md) | Design tokens as a shared single source of truth | 🟡 **Proposed** | M1.6, M2.4, M6.2 |
| [0009](0009-website-first-with-mock-data-adapter.md) | Website-first build order with a fixture-backed data layer | ✅ Accepted | M2, M3, M4 |

## Decisions still needing input

Three ADRs are **Proposed** — each states a recommendation but needs the project owner's call before the milestone that depends on it:

| ADR | Needs deciding before | Question |
|---|---|---|
| [0006](0006-cache-revalidation-strategy.md) | M9.1 | Webhook + `revalidateTag`, or Supabase Realtime? |
| [0007](0007-android-architecture.md) | M6.4, M6.5 | Hilt or Koin? Ktor or Retrofit? The PRD offers both of each. |
| [0008](0008-design-tokens-single-source.md) | M1.6 | Hand-mirror tokens into each platform, or generate from one source? |

These correspond to Open Questions 10–13 in [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md#risks--open-questions).

## Rules

1. **Never rewrite an Accepted ADR's decision.** Supersede it with a new record and update the old status to point at it. An edited ADR destroys the very history it exists to preserve.
2. **Number sequentially, never reuse.** Gaps are fine; collisions are not.
3. **An ADR ships with the change it governs**, in the same commit — not retroactively.
4. **Proposed means blocked.** If a task depends on a Proposed ADR, that task cannot be called complete until the ADR is Accepted.
5. **Record rejected alternatives.** That table is most of an ADR's long-term value: it tells a future reader whether their idea was already considered and why it lost.

## When something is *not* an ADR

Coding standards, naming conventions, and formatting live in [CLAUDE.md](../../CLAUDE.md). Requirements live in [prd.md](../../prd.md). Sequencing lives in [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md). ADRs are only for structural decisions with lasting consequences.
