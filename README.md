# Jewellery Catalogue Platform

A jewellery catalogue platform: a customer-facing website that showcases collections, an Android app the shop owner uses to manage them, and a Supabase backend keeping both in step.

**This is not e-commerce.** Nothing is sold online. The platform exists to present jewellery professionally and encourage customers to visit the store or make contact.

## Status

| Milestone | State |
|---|---|
| M0 Repository setup | ✅ complete |
| M1 Design system | ✅ complete — [docs/design/](docs/design/) |
| M2 Website foundation | ✅ complete — shell, primitives, data boundary |
| M3 Supabase backend | ◐ SQL written, **not yet applied** (needs the project linked) |
| M4 onward | not started |

The website currently renders a fixture catalogue. Swapping in Supabase is
one line in `web/lib/data/index.ts` — see [ADR-0009](docs/adr/0009-website-first-with-mock-data-adapter.md).

## Start here

| Document | Role |
|---|---|
| [prd.md](prd.md) | **Source of truth for requirements** — what the product must do |
| [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) | Milestones, tasks, dependencies, acceptance criteria |
| [CLAUDE.md](CLAUDE.md) | Repository instructions and working rules for Claude Code |
| [docs/](docs/) | Documentation index — architecture, ADRs, design, database, deployment |

Architectural decisions are recorded as ADRs in [docs/adr/](docs/adr/). All ten are **Accepted**.

## Layout

```
├── web/          Next.js 15 + TypeScript customer website
├── android/      Kotlin + Jetpack Compose admin application
├── supabase/     Migrations (schema + RLS), config, seed data
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

| Tool | Needed for | Verified with |
|---|---|---|
| Node.js ≥ 20 (built on 22.20) | `web/` | ✅ installed |
| npm ≥ 10 (built on 11.6) | `web/` | ✅ installed |
| Supabase CLI | `supabase/` | ✅ pinned as a repo devDependency — use `npx supabase` |
| Docker Desktop | Optional — only for a *local* database | ⬜ not installed |
| JDK 17+ and Android Studio | `android/` — from M6.1 | ⬜ not yet installed |

The Supabase CLI is pinned in the root `package.json` rather than installed
globally, so the schema stays reproducible from a clone. Docker is only needed
if you want a local database; applying migrations to the hosted project needs
no Docker.

## Setup

### Website

```bash
cd web
npm install
cp .env.example .env.local     # then fill in the values
npm run dev                    # http://localhost:3000
```

The site runs without Supabase credentials for now — environment variables are
validated on first use, not at startup, precisely so the shell and component
work can proceed before the database exists (M3.1).

**Scripts:**

| Command | Does |
|---|---|
| `npm run dev` | Development server |
| `npm run build` | Production build |
| `npm run verify` | **Typecheck + lint + format check** — run before every commit |
| `npm run format` | Apply Prettier |
| `npm run lint:fix` | Apply ESLint fixes |

### Backend

From the repository root:

```bash
npm install                    # installs the pinned Supabase CLI
npx supabase login             # opens a browser; one-time
npx supabase link --project-ref <your-project-ref>
npm run db:push                # applies every migration
npm run db:types               # regenerates web/lib/data/database.types.ts
```

The project ref is the subdomain of your Supabase URL — for
`https://abcdefgh.supabase.co` it is `abcdefgh`. Find it under
**Project Settings → General**.

| Command | Does |
|---|---|
| `npm run db:push` | Apply pending migrations to the linked project |
| `npm run db:diff` | Show what differs between local migrations and the remote schema |
| `npm run db:types` | Regenerate the TypeScript types from the live schema |
| `npm run db:reset` | **Local only** — drop, re-migrate, re-seed. Needs Docker |

`npm run db:reset` runs `supabase/seed.sql`. **Never run it against
production** — M5.6 enters the real catalogue there.

The seed is safe to run against the *development* project and is idempotent.

### Android

Setup arrives with the code, in M6.1.

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
