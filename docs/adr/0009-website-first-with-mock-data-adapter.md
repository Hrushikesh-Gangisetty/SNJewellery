# ADR-0009: Website-first build order with a fixture-backed data layer

- **Status:** ✅ Accepted
- **Date:** 2026-07-25
- **Deciders:** Hrushikesh Gangisetty
- **Affects:** M2, M3, M4

## Context

The milestone order places the website foundation (M2) **before** the Supabase backend (M3), and the design system (M1) before both. The motivation is visible progress early: a working, styled catalogue is something you can look at, react to, and show people, whereas migrations and RLS policies are not.

This creates an obvious problem. **A website built before its database has nothing to read.** Handled badly — hard-coded arrays scattered through components, or components shaped around whatever fake data was convenient — it guarantees rework at M4 and quietly destroys the benefit the ordering was meant to buy.

Building backend-first avoids the problem entirely, which is the conventional choice. The question is whether website-first can be made safe.

## Decision

We will build website-first, and make it safe by **defining the data-access layer as an interface with a fixture implementation in M2.5**, then swapping in the Supabase implementation in M4.1 behind the same interface.

The sequence:

1. **M2.5** — hand-write the domain types (product, category, product image) as a **draft schema contract**, define the data-access interface, and implement it against realistic fixtures.
2. **M2.6–M2.10** — build the shell, primitives, and conventions against that interface. No component knows where data comes from.
3. **M3.2–M3.5** — write migrations matching the draft types.
4. **M3.10** — generate types from the real schema and reconcile them against the draft, resolving every difference deliberately and recording why.
5. **M4.1** — implement the interface against Supabase. **No component changes.**

Two acceptance criteria enforce this rather than leaving it to good intentions:

- **M2:** substituting an empty-data implementation must produce empty states, not crashes — proving nothing bypassed the interface.
- **M4:** no component may require modification when the fixture layer is replaced.

There is a second benefit beyond safety. Hand-writing the types in M2.5 and then building real UI against them **exercises the schema before any migration is written**. Missing fields and awkward shapes surface while they cost nothing to change. M3 then codifies a contract that has already been used in anger, which is a better contract than one designed in the abstract.

## Consequences

### What this makes easier

- A styled, navigable catalogue exists early, when feedback on the design system is still cheap to act on.
- The schema is validated by real UI before being frozen — M3's freeze is more trustworthy for it.
- The interface boundary keeps paying off: it is where query cost is measured (M10.7) and where cache tags attach (M4.7).
- Fixtures remain useful afterwards for tests, empty-state work, and offline development.

### What this makes harder

- **Two type definitions exist temporarily**, and reconciling them at M3.10 is real work that must not be rubber-stamped. A large gap at reconciliation is a genuine finding about the schema.
- Discipline is required: the temptation to query Supabase directly from a component in M4 must be resisted, or the layer's value is lost.
- The fixtures must be realistic. Fixtures with tidy short names and exactly three images per product will hide the layout problems real data causes — long product names, missing weights, single-image products.

### What this commits us to

The interface from M2.5 becomes the website's permanent data boundary. This is not a temporary scaffold to be removed after M4 — it is where all reads live for the life of the project.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Backend first, then website (the conventional order) | Safest and simplest. Rejected because nothing visible exists until quite late, and design-system feedback arrives after the design is already implemented rather than while it is cheap to change. |
| Website first with hard-coded data in components | The failure mode this ADR exists to prevent. Guarantees rework and shapes components around fake data. |
| Website first against a throwaway local schema | Better than hard-coding, but means standing up a database anyway — most of M3's cost without its rigour, and then discarding it. |
| Generate fixtures from the eventual schema | Circular: the schema does not exist yet. This is what M3.10's reconciliation does, in the correct direction. |

## Open sub-questions

- How realistic the M2.5 fixtures need to be. They should include the awkward cases — very long names, missing optional fields, single-image products, an empty category — because those are what break layouts.
- Whether fixtures live in `web/` or in a shared location. They are website test data, so `web/` is the default.

## References

- [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — Planning Decisions, "The website-first problem, and how it is solved"
- [docs/api/](../api/) — the data-access interface and the draft-contract sequence
- [ADR-0001](0001-monorepo.md) — the monorepo that makes the reconciliation a single commit
