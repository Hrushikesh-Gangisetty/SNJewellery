# Jewellery Catalogue Platform

A jewellery catalogue platform: a customer-facing website that showcases collections, an Android app the shop owner uses to manage them, and a Supabase backend keeping both in step.

**This is not e-commerce.** Nothing is sold online. The platform exists to present jewellery professionally and encourage customers to visit the store or make contact.

## Status

**Planning complete. No application code written yet.**

The next milestone is **M1 · Design System**, which is blocked on Open Question 9 — the shop's exact name and any existing brand assets. See [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md#risks--open-questions).

## Start here

| Document | Role |
|---|---|
| [prd.md](prd.md) | **Source of truth for requirements** — what the product must do |
| [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) | Milestones, tasks, dependencies, acceptance criteria |
| [CLAUDE.md](CLAUDE.md) | Repository instructions and working rules for Claude Code |
| [docs/](docs/) | Documentation index — architecture, ADRs, design, database, deployment |

Architectural decisions are recorded as ADRs in [docs/adr/](docs/adr/). Three are currently **Proposed** and need a decision before their milestones can proceed.

## Layout

```
├── web/          Next.js 15 + TypeScript customer website
├── android/      Kotlin + Jetpack Compose admin application
├── supabase/     Migrations, RLS policies, seed data
└── docs/         All other documentation
```

One repository, deliberately — both clients code against one database schema, and a monorepo makes a schema change a single atomic commit. See [ADR-0001](docs/adr/0001-monorepo.md).

## Stack

| Part | Technology |
|---|---|
| Website | Next.js 15 (App Router), TypeScript, Tailwind, shadcn/ui, Framer Motion, Vercel |
| Android | Kotlin, Jetpack Compose, Material 3, MVVM + Repository, Coil |
| Backend | Supabase — PostgreSQL, Auth, Storage, row-level security |

## Prerequisites

Not yet pinned — versions are set in M0.3 and M6.1.

- Node.js and npm — for `web/`
- JDK and Android Studio — for `android/`
- Supabase CLI — for `supabase/`

## Setup

Setup steps arrive with the code they set up:

- **Website** — M2.1. Will be `cd web && npm install && npm run dev`, with `web/.env.example` copied to `web/.env.local`.
- **Backend** — M3.1, via the Supabase CLI.
- **Android** — M6.1, opened in Android Studio.

Until then there is nothing to install.

## Working on this project

Read [CLAUDE.md](CLAUDE.md) before making changes. In short:

- **One task at a time.** Tasks (`M4.3`) are the unit of work; milestones are the unit of review.
- **Verify the build before calling anything done.** `npm run verify` and `npm run build` for web; Gradle for Android.
- **No secrets in git, ever.** Row-level security is the security boundary, and the service-role key never reaches a client.
- **Documentation ships with the change it describes**, in the same commit.
- **Nothing half-finished.** No `TODO`, no stub returning fake data. A task is complete or explicitly reported incomplete.

## Design system

[docs/design/](docs/design/) is the visual source of truth for **both** clients. Every colour, type step, spacing value, radius, and duration used anywhere traces to a token defined there — no hard-coded visual values in component code, on either platform.

It is produced by M1, before any application code, so the visual language is decided once rather than invented twice and reconciled later.
