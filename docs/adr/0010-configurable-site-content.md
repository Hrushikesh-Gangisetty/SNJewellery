# ADR-0010: Where configurable brand and site content lives

- **Status:** 🟡 **Proposed** — one scope question for the owner (see Open sub-questions)
- **Date:** 2026-07-25
- **Deciders:** Hrushikesh Gangisetty
- **Affects:** M3.2, M4.9–M4.11, M8, M1

## Context

The project owner stated a requirement the PRD does not: **brand assets, the
About page, and image guidelines must be updatable without code changes.** Real
values for most of them — logo, business history, certifications, social links,
Google Maps location, launch catalogue — arrive later, after the milestones that
consume them are built.

So the site has to be built now against content that does not exist yet, and it
must not require a developer to fill in later. Those two constraints together
rule out the obvious approach of hard-coding values once they arrive.

There are three candidate homes for a given piece of content, and the design
question is which rule assigns content to which:

1. **A typed config module in the repository** — simple, type-checked, but
   editing it is a commit and a redeploy, i.e. a developer.
2. **The database** — editable at runtime, no redeploy, but needs an admin
   surface to edit it, and that surface is not in the PRD.
3. **Documentation** — for guidance aimed at humans, never read at runtime.

"Without code changes" only genuinely holds for option 2.

## Decision

Content is assigned by **who changes it and how often**:

| Content | Home | Why |
|---|---|---|
| Categories | `categories` table | Already decided; owner manages them in the app (M8.6) |
| Products, images | `products`, `product_images` | The catalogue |
| Shop name, address, phone, WhatsApp, hours, social links, Maps location | **`site_settings` table**, seeded by migration | Owner-editable without a developer. Values arrive later. |
| About page prose, business history, certifications | **`site_settings`** (long-text values) | Owner-authored, arrives later, changes over time |
| Logo and brand imagery | **Supabase Storage**, path held in `site_settings` | An asset, not a string. Same delivery path as product images. |
| Purity values | **`purities` table**, not a CHECK constraint | Owner asked for extensibility without redesign. A lookup table adds a row; a CHECK constraint needs a migration. |
| Photography standards, design tokens, brand attributes | `docs/design/` | Guidance for humans and build-time input. Never read at runtime. |

Two rules follow:

1. **No component hard-codes any of it.** Not the shop name, not the phone
   number, not a category. M4's acceptance criteria already test this for the
   phone number by grep.
2. **Every setting has a seeded default**, so the site renders before the owner
   has supplied a real value. A missing setting is never a crash — it is either
   a sensible default or a cleanly hidden section.

`site_settings` is a **single-row table with typed columns**, not a key/value
pair table. Key/value forces every read through a lookup that returns `string`,
losing type safety and making a typo a runtime bug. One row with real columns
generates proper TypeScript types like every other table.

## Consequences

### What this makes easier

- The site can be built now and populated later, by the owner, without a deploy.
- One place to look for any shop detail, on both clients.
- Adding a purity or a category is a row, not a migration.
- Sections with no content yet (social links, Maps) hide themselves cleanly
  rather than rendering an empty box.

### What this makes harder

- **It adds scope the PRD does not describe.** The PRD's Android app manages
  products and categories; it says nothing about editing shop details or About
  copy. Making settings owner-editable means an admin settings screen, which is
  new work in M8 — see the open question below.
- Every setting read is a database read, so the settings row must be cached and
  tied into the M9 revalidation map, or the site will not reflect an edit.
- A single-row table needs a constraint enforcing that exactly one row exists.

### What this commits us to

Content is data. Any future feature wanting to display a shop detail reads it
from `site_settings` rather than introducing a constant — including M11's
structured data, which needs address and hours for `LocalBusiness`.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Typed config module in the repository | Simplest and type-safe, and what M4.9 originally specified. Rejected as the primary home because editing it is a commit and a redeploy — it does not meet "without code changes". Still correct for genuinely build-time values. |
| Key/value settings table | Flexible but untyped. Every read returns `string`, and a mistyped key fails at runtime instead of compile time. |
| Markdown content files in the repository | Good for long prose and reviewable in git, but still a commit and a redeploy. |
| A headless CMS | Solves this properly at the cost of a second vendor, a second bill, and a second admin surface — for one shop with one page of prose. |

## Open sub-questions

- **Does the owner want to edit shop details and About copy themselves, in the
  Android app?** If yes, M8 needs a settings screen and this ADR stands as
  written. If they are content to send updated details for a developer to apply,
  the far cheaper answer is a typed config module and `site_settings` is
  unnecessary. **This is the one question blocking the ADR's acceptance** — it
  is the difference between roughly one extra M8 task and none.
- Whether `site_settings` should be readable by anonymous users for all fields,
  or whether some are admin-only. Decided with the M3.7 policies.
- How the settings row interacts with M9's cache tags. Decided in M9.3.

## References

- [prd.md](../../prd.md) — Contact Page, About Us, Category Management
- [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — M3.2, M4.9–M4.11, M8
- [ADR-0005](0005-image-storage-and-renditions.md) — how the logo is stored and served
