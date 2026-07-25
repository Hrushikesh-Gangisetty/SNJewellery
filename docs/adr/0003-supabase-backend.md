# ADR-0003: Supabase as the backend platform

- **Status:** ✅ Accepted
- **Date:** 2026-07-25
- **Deciders:** Hrushikesh Gangisetty — specified in the PRD
- **Affects:** M3, and every milestone that reads or writes data

## Context

The PRD specifies Supabase, with PostgreSQL, Supabase Auth, Supabase Storage, Supabase Realtime, and Edge Functions if required. This ADR records why that fits, and — more usefully — what it commits the project to.

The backend has four jobs, and no more: authenticate one admin, store a catalogue, store and serve photographs, and enforce who can read and write what. There is no order processing, no payment, no inventory reconciliation, no customer accounts. The PRD is explicit that this is not e-commerce.

Two properties of the situation matter:

1. **The security model is the whole backend.** With no application server between the clients and the data, whatever enforces access control *is* the architecture. Postgres row-level security can do this job at the database level, which is stronger than doing it in application code.
2. **There is one developer and no operations team.** Anything requiring a server to be maintained, patched, and monitored is a recurring cost with no one assigned to it.

## Decision

We will use **Supabase** as the entire backend: PostgreSQL for data, Supabase Auth for admin authentication, Supabase Storage for photographs and their renditions, and row-level security as the security boundary.

Notably, **we will not build an application server or a bespoke API.** Both clients talk to Supabase directly, and RLS — not client code — decides what each may do. See [ADR-0004](0004-authentication-and-roles.md).

Edge Functions will be used only where something genuinely cannot live in the database or the clients. The current candidate is the revalidation trigger in M9.

## Consequences

### What this makes easier

- Postgres brings full-text search (M10.1), proper indexing, and pgvector should M13.4 go ahead — the PRD's "scalable foundation for future AI features" is a real property of this choice, not a hope.
- RLS enforces access control at the database, so a bug in a client cannot leak data the policy forbids.
- Storage with transform-derived renditions removes the need to build an image pipeline (M3.6).
- Generated TypeScript types keep the website honest against the schema.
- No server to operate, patch, or monitor.
- Realtime is available if M9 wants it — see [ADR-0006](0006-cache-revalidation-strategy.md).

### What this makes harder

- **RLS becomes load-bearing.** A policy mistake is a data breach, not a bug. This is why M3.7's acceptance criteria are adversarial: they require attempting the attacks, not just confirming the happy path works.
- Business logic has fewer natural homes — it lands in database functions, Edge Functions, or the clients, and it takes discipline to keep it from being duplicated in both clients.
- **Tier limits are a real operational risk.** High-resolution jewellery photography is storage- and egress-heavy. This is Open Question 2 and needs a projected estimate before launch.
- Vendor coupling: Auth, Storage, RLS, and the generated types are all Supabase-shaped. The Postgres data would move; the surrounding platform would not.

### What this commits us to

The security model. Every feature must be expressible as an RLS policy. If a feature appears to need the service-role key from a client, the policy is wrong — that is a rule, not a guideline, and it is what keeps the boundary intact.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Firebase | Firestore's query model is a poor fit for the filtering and full-text search in M10, and there is no path to pgvector for M13.4. |
| Custom backend (Node/Nest + Postgres + S3) | Full control, but it means building auth, storage renditions, and an API, and then operating a server — for a catalogue with one writer. |
| Postgres on a VPS, direct | Cheapest at scale, most expensive in attention. No managed auth, no storage renditions, and backups become someone's job. |
| A headless CMS | The Android app is the admin interface. A CMS would duplicate the schema and the admin surface the PRD already specifies. |

## Open sub-questions

- **Open Question 2** — which tier, based on projected storage and egress. Must be settled before M5.
- **Open Question 4** — single admin or multiple roles, which determines how much the policy model must carry.
- Whether Edge Functions are needed beyond M9's revalidation trigger.

## References

- [prd.md](../../prd.md) — Tech Stack (Backend), Security, Storage
- [ADR-0004](0004-authentication-and-roles.md) — the security boundary in detail
- [ADR-0005](0005-image-storage-and-renditions.md) — storage and renditions
- [docs/database/](../database/) — schema contract and RLS model
