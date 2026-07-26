# ADR-0010: Where configurable brand and site content lives

- **Status:** ✅ **Accepted** — 2026-07-26
- **Date:** 2026-07-25 (decision revised 2026-07-26)
- **Deciders:** Hrushikesh Gangisetty
- **Affects:** M3.2, M4.9–M4.11, M1

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

**Revised 2026-07-26.** The owner confirmed they will send updated details for a
developer to apply, rather than editing them in the app. So there is **no
`site_settings` table and no admin settings screen** — the cheaper option, and
the one that adds no scope.

Content is assigned by **who changes it and how often**:

| Content | Home | Why |
|---|---|---|
| Categories | `categories` table | Owner manages them in the app (M8.6) |
| Products, images | `products`, `product_images` | The catalogue |
| Purity values | **`purities` table**, not a CHECK constraint | Owner asked for extensibility without redesign. A lookup table adds a row; a CHECK constraint needs a migration. |
| Shop name, address, phone, WhatsApp, hours, social links, Maps location | **Typed config module** — `web/lib/config/site.ts` | Changes rarely, and a developer applies it. Type-safe and reviewable in git. |
| About page prose, business history, certifications | **Typed config module**, same file | Same cadence, same author |
| Logo and brand imagery | **`web/public/`**, path referenced from the config module | A build-time asset. Storage is for product photography, which is user-generated; the logo is neither. |
| Photography standards, design tokens, brand attributes | `docs/design/` | Guidance for humans and build-time input. Never read at runtime. |

Three rules follow, and they are what actually deliver the owner's requirement:

1. **No component hard-codes any of it.** Not the shop name, not the phone
   number, not an opening hour. M4's acceptance criteria test this for the phone
   number by grep, and the same rule covers every other value.
2. **Every value is optional where it can be, with a defined absent-state.** A
   missing social handle, Maps location, founding year, or About section renders
   as *nothing* — a cleanly hidden section, never an empty box, a broken link, or
   a crash. This is what lets M4 ship before the owner has supplied the content.
3. **The config module is the single source.** Structured data in M11.3 reads
   address and hours from it, not from duplicated constants.

The distinction that matters: **product data is database, site content is config,
design decisions are documentation.** Anything the owner changes routinely is in
the database and editable in the app. Anything that changes once a year is
config.

## Consequences

### What this makes easier

- The site can be built now against a typed shape and populated later, with no
  schema work and no admin screen.
- Type safety: a missing required field is a build error, not a blank page. A
  database settings table could not offer that.
- One place to look for any shop detail. Changes are reviewable in git with
  history, which a database row is not.
- Adding a purity or a category is still a row, not a migration.
- No caching or revalidation concern — config is inlined at build time, so it
  cannot go stale relative to the deployed site.

### What this makes harder

- **Updating shop details requires a developer and a redeploy.** This does not
  literally satisfy "without code changes" — it satisfies "without *component*
  changes", which the owner confirmed is what they meant. Worth restating plainly
  so nobody is surprised: changing the phone number is a one-line edit, a
  commit, and a deploy.
- If the owner later wants self-service editing, that is a migration plus an M8
  settings screen. The config module's shape makes the port mechanical, but it is
  not free.

### What this commits us to

The config module's shape becomes the contract that pages, the footer, the
Contact page, and M11's `LocalBusiness` structured data all read from. Adding a
field is trivial; moving the whole thing into the database later is not.

## Alternatives considered

| Alternative | Why not |
|---|---|
| **`site_settings` table + admin settings screen** | The originally proposed decision, and the only option that literally means "no code changes". Rejected 2026-07-26: the owner is content to send details for a developer to apply, and this adds a table, RLS policies, a cache-invalidation concern, and an M8 screen the PRD never asked for. |
| Key/value settings table | Flexible but untyped. Every read returns `string`, and a mistyped key fails at runtime instead of compile time. |
| Markdown content files | Good for long prose and reviewable in git, but adds a parsing step for content that fits comfortably in typed objects. Reconsider if the About page grows into several pages of prose. |
| A headless CMS | Solves this properly at the cost of a second vendor, a second bill, and a second admin surface — for one shop with one page of prose. |

## Open sub-questions

- ~~Does the owner want to edit shop details themselves?~~ **Decided
  2026-07-26: no. Typed config module.**
- Whether the About page's prose eventually justifies markdown files rather than
  string fields. Revisit at M4.11 once the owner supplies the actual copy — a few
  paragraphs is fine as strings; several sections with headings is not.

## References

- [prd.md](../../prd.md) — Contact Page, About Us, Category Management
- [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — M3.2, M4.9–M4.11, M8
- [ADR-0005](0005-image-storage-and-renditions.md) — how the logo is stored and served
