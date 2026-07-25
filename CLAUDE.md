# CLAUDE.md

Permanent repository instructions for the Jewellery Catalogue Platform. These apply to every session and override default behaviour.

**Read these first:**

| Document | Role |
|---|---|
| [prd.md](prd.md) | **Source of truth for requirements.** What must be built. Never contradict it; if it seems wrong, say so and ask. |
| [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) | Milestones, tasks, dependencies, acceptance criteria. What to build next. |
| [docs/adr/](docs/adr/) | Why the architecture is what it is. Check before proposing a structural change. |
| [docs/design/](docs/design/) | The design system. The visual source of truth for both clients. |

---

# 1 · Workflow Rules

These are binding. They exist because this project is built incrementally by an agent with a human reviewing, and that only works if each increment is small enough to actually review.

### 1.1 Never implement an entire milestone in one iteration

Milestones are units of *review*. **Tasks** (`M4.3`, `M7.6`) are units of *work*. Implement one task, then stop.

If a task turns out to be larger than it looked, say so and propose a split rather than pressing on. A task that produces a 900-line diff was the wrong size, regardless of whether the code is correct.

### 1.2 Plan before coding

Before writing code for a task:

1. Read the task's *Done when* condition and the milestone's acceptance criteria.
2. Read the relevant existing code. Reuse what is there — do not write a second function that does what an existing one does.
3. State the approach in a few sentences, naming the files you will touch.

For anything structural, unclear, or touching more than a few files, plan first and get agreement. For a small, obvious task, a brief statement of intent is enough — do not perform ceremony for a one-file change.

### 1.3 Explain important architectural decisions briefly

When a decision has structural consequences, state it in two or three sentences: what you chose, what you rejected, and why.

**Brevity is the rule.** An architectural note is not an essay. If the decision is genuinely significant — it constrains future work, is expensive to reverse, or a future reader would question it — write an ADR in [docs/adr/](docs/adr/) instead of explaining it in chat, where it will be lost.

Do not explain routine choices. Naming a variable, picking a loop, following an existing pattern — these need no narration.

### 1.4 Keep commits small and meaningful

One logical change per commit. A commit should be reviewable in a few minutes and reverting it should undo exactly one thing.

Commit at task boundaries, not at milestone boundaries. See §10.

### 1.5 Update documentation whenever architecture changes

Documentation ships **in the same commit** as the change it describes, never as a follow-up.

- Structural decision → an ADR.
- Schema change → the migration, the generated TypeScript types, the Kotlin models, and `docs/database/schema.md`, together.
- New design value → `docs/design/` first, then the token, then the code.
- Changed deploy or environment step → `docs/deployment/`.

A commit that changes architecture without touching documentation is incomplete.

### 1.6 Never leave unfinished placeholder implementations

No `TODO` in shipped code. No function returning fake data to make a signature satisfied. No commented-out half-implementation. No `throw new Error("not implemented")` on a path something calls.

A task is **complete** or **explicitly reported incomplete**. There is no third state, and there is certainly no silently-incomplete state.

If you cannot finish a task — blocked on a decision, a credential, an unanswered question — stop and say exactly what is blocking it. Do not stub it and move on. A stub that looks finished is worse than no code, because it hides the gap.

### 1.7 Verify the application builds before completing a task

Before reporting a task complete:

- **Web:** `npm run verify` (lint, format, typecheck) **and** `npm run build`.
- **Android:** the relevant Gradle build.
- **Database:** the migration applies cleanly to an empty database.

Report what you ran and what it output. If it fails, the task is not complete — say so with the error. **Never claim a build passes without having run it.**

### 1.8 Stop after each completed task and wait for approval

After finishing one task: report what changed, what you verified, and what the next task is. **Then stop.**

Do not continue to the next task, do not start the next milestone, do not "while I'm here" an adjacent fix. If you notice something worth doing, mention it and let the decision be made.

The one exception: if a task cannot be completed without a trivially small adjacent change — an import, a type export — make it and say you did.

---

# 2 · Project Philosophy

**This is a catalogue, not a shop.** Nothing is sold online. Every feature exists to get a customer to visit the store or pick up the phone. When a decision is unclear, favour the one that makes a customer more likely to enquire.

**The photographs are the product.** A jewellery catalogue is an image delivery system with metadata attached. Image quality and image performance are features, not implementation details.

**The shop owner is not technical.** The Android app must work in under thirty seconds, on mobile data, one-handed, without a manual. Every added field and confirmation step is a cost paid by someone photographing rings between customers.

**Premium means restrained.** The PRD asks for a "clean and premium browsing experience". That means whitespace, quiet typography, and restraint — not more effects. Motion supports the photography; it never competes with it.

**Build for the catalogue that exists.** The PRD targets 100,000+ products; the shop likely starts with hundreds. Do not build for the large number where the small one is the reality — but do not paint into a corner either. See Open Question 3.

**Correct beats clever.** A solo-maintained project. Code that is obvious in six months is worth more than code that is elegant today.

---

# 3 · Architecture Philosophy

Read [docs/architecture/](docs/architecture/) for the system overview and [docs/adr/](docs/adr/) for the reasoning. These are the invariants:

### 3.1 The website never writes

All mutation originates in the Android app. The website uses the anonymous key and has no write path. Do not add one.

### 3.2 RLS is the security boundary

Not client code. Not a check in a component. **If a feature appears to need the service-role key from a client, the policy is wrong — fix the policy.** The service-role key never ships to any client, ever. See [ADR-0004](docs/adr/0004-authentication-and-roles.md).

### 3.3 One schema contract, two clients

The generated TypeScript types and the hand-written Kotlin models describe the same tables. **A migration updates both, in the same change.** A one-sided schema change is how the clients silently diverge. See [docs/database/](docs/database/).

### 3.4 Pages read through the data-access layer

No page and no component queries Supabase inline. Every read goes through the interface defined in M2.5. This is what makes the fixture-to-real swap free, and it stays the read boundary permanently. See [ADR-0009](docs/adr/0009-website-first-with-mock-data-adapter.md).

### 3.5 Every visual value is a token

No hard-coded colour, font size, spacing, radius, or duration in component code, on either platform. Everything traces to [docs/design/](docs/design/). See [ADR-0008](docs/adr/0008-design-tokens-single-source.md).

### 3.6 Storage paths are derived, never improvised

The convention in [ADR-0005](docs/adr/0005-image-storage-and-renditions.md) is the only way an image location is constructed.

### 3.7 Prefer boring

Reach for a new dependency only when the platform genuinely cannot do the job. Every dependency is weight in the bundle, a security surface, and something to upgrade. Next.js, Tailwind, Supabase, and Compose already do most of what this project needs.

---

# 4 · Coding Standards

### TypeScript

- **Strict mode. No `any`.** If a type is genuinely unknown, use `unknown` and narrow it.
- Prefer `type` for data shapes; use `interface` when declaration merging or extension is actually needed.
- No non-null assertions (`!`) to silence the compiler. If a value can be null, handle it.
- Server Components by default. `"use client"` only where interactivity requires it, and as far down the tree as possible.
- Data fetching lives in the data-access layer, never in a component.
- Handle the error path. A `catch` that swallows an error is a bug.

### Kotlin

- Explicit types on public APIs; inference inside functions.
- Prefer immutability — `val`, data classes, immutable collections.
- No `!!`. Handle nullability.
- Coroutines for async; no blocking calls on the main thread.
- Repositories return domain models, never raw network or database types.
- Compose: hoist state, keep composables free of side effects, and never do I/O in a composable.

### SQL

- Every migration is forward-only and idempotent where possible.
- Comment any column whose purpose is not obvious from its name — especially the four M3.4 fields the PRD's schema section omits.
- Every new table gets RLS enabled **and** policies, in the same migration. A table with RLS and no policies denies everything; a table without RLS exposes everything, and that failure is silent.

### Universally

- **Handle loading, empty, and error states.** Every surface, every time, per the M1.10 patterns. Not just the happy path.
- Comments explain *why*, never *what*. The code says what.
- Match the surrounding code's density, naming, and idiom. A file should not reveal which parts an agent wrote.
- No dead code. No commented-out blocks "in case".

---

# 5 · Naming Conventions

| Thing | Convention | Example |
|---|---|---|
| TypeScript files — components | `PascalCase.tsx` | `ProductCard.tsx` |
| TypeScript files — other | `kebab-case.ts` | `data-access.ts` |
| React components | `PascalCase` | `ProductGallery` |
| Hooks | `useCamelCase` | `useFilterState` |
| TS variables, functions | `camelCase` | `getProductBySlug` |
| TS types | `PascalCase`, no `I` prefix | `Product`, not `IProduct` |
| Constants | `SCREAMING_SNAKE_CASE` | `MAX_IMAGE_DIMENSION` |
| Kotlin files, classes | `PascalCase` | `ProductRepository.kt` |
| Kotlin functions, properties | `camelCase` | `uploadProduct` |
| Composables | `PascalCase` | `ProductForm` |
| Database tables | `snake_case`, plural | `product_images` |
| Database columns | `snake_case` | `display_order` |
| Migrations | `NNNN_verb_subject.sql` | `0003_add_product_tags.sql` |
| ADRs | `NNNN-kebab-title.md` | `0006-cache-revalidation-strategy.md` |
| Design tokens | see [ADR-0008](docs/adr/0008-design-tokens-single-source.md) | must read naturally in Tailwind *and* Compose |
| Branches | `type/short-description` | `feat/product-gallery` |

**Naming rules that matter more than casing:**

- Name for what something *is*, not how it is implemented. `getFeaturedProducts`, not `queryProductsWhereFeaturedTrue`.
- Booleans read as assertions: `isVisible`, `hasImages`, `archived`.
- No abbreviations except universally understood ones (`id`, `url`, `img` in a token name).
- **Domain terms match the PRD.** The PRD says *purity*, *featured*, *sold*, *archived*, *category* — use exactly those words in code, schema, and UI. Do not introduce a synonym.

---

# 6 · Folder Conventions

```
web/
├── app/                  # App Router routes only — thin, composing components
├── components/
│   ├── ui/               # shadcn/ui primitives
│   └── …                 # feature components, grouped by domain
├── lib/
│   ├── data/             # THE data-access layer — all reads live here
│   ├── config/           # validated env, shop details
│   └── …                 # utilities
└── styles/

android/app/src/main/java/…/
├── data/
│   ├── remote/           # Supabase, network
│   ├── local/            # Room / DataStore — offline drafts
│   └── models/           # mirrors the schema contract
├── domain/               # business logic, use cases
└── ui/
    ├── theme/            # tokens → Material 3 theme
    ├── components/
    └── screens/          # one package per screen: composable + view model

supabase/
├── migrations/           # versioned, forward-only
├── policies/             # RLS
└── seed/
```

**Rules:**

- **Routes stay thin.** `app/` composes; it does not implement. Logic belongs in `lib/` or a component.
- **`lib/data/` is the only place Supabase is queried** from the website.
- **Colocate by feature, not by type**, once a feature has more than about three files. `components/product/` beats scattering across `cards/`, `galleries/`, `lists/`.
- **One screen, one package** on Android: the composable and its view model together.
- **No file at the repository root** except `prd.md`, `DEVELOPMENT_PLAN.md`, `README.md`, `CLAUDE.md`, and tooling config. Documentation goes in `docs/`.

---

# 7 · Documentation Expectations

**Write down what a future contributor would otherwise have to reverse-engineer. Nothing else.**

| Change | Documentation required |
|---|---|
| Structural / architectural decision | An ADR in [docs/adr/](docs/adr/) |
| Schema change | `docs/database/schema.md` + generated types + Kotlin models, same commit |
| New or changed design value | `docs/design/` first, then the token, then the code |
| New data-access method | `docs/api/data-access.md` |
| Deploy or environment change | `docs/deployment/` |
| New environment variable | `.env.example` with a placeholder and a comment |

**Rules:**

1. **One home per fact.** Never restate a rule in two documents — link to the one that owns it. Duplicated documentation drifts, and drifted documentation is worse than none.
2. **Documentation ships with its change**, in the same commit.
3. **Never rewrite an Accepted ADR's decision.** Supersede it with a new one and update the old status to point at it.
4. **Do not document what the code says plainly.** A comment restating a function signature is noise.
5. Record measurements as numbers, in the repository. A performance figure in a chat message is worthless in three months. M5.8, M7.13, M9.6, M10.7, and M12.11 all require committed measurements.

---

# 8 · Performance Expectations

From the PRD's Success Metrics and Non-Functional Requirements. These are **targets to verify by measurement**, not aspirations.

| Target | Verified in |
|---|---|
| Mobile page load **under 2 seconds** | M12.4 — throttled mobile profile |
| Mobile Lighthouse Performance **above 90** | M12 |
| Lighthouse SEO **above 95** | M11.8 |
| Product upload **under 30 seconds** on mobile data | M7.13 |
| Upload **visible on the website within 60 seconds** | M9.6 |
| Search latency at ~100k products within the documented budget | M10.7 |

**Practices that follow:**

- **Images are the performance story.** Correct rendition, accurate `sizes`, fixed aspect ratios, lazy below the fold, priority for the hero and the gallery's first image.
- **Fixed aspect ratios everywhere** so layout shift is structurally impossible, not merely avoided.
- Server Components by default; dynamic imports for heavy client components.
- Keyset pagination, never `OFFSET`, on anything that can grow.
- Every query hits an index. Check with `EXPLAIN`.
- **Measure, do not assume.** M5.8 records a baseline precisely so later claims are checkable.

---

# 9 · Security Expectations

**Non-negotiable:**

1. **No secret in git. Ever.** Not in code, not in a config file, not in a commit message, not "temporarily". New variables go in `.env.example` as placeholders.
2. **The service-role key never reaches a client.** Not the website bundle, not the APK. M5.3 verifies by searching the built output.
3. **RLS is the boundary.** Every table gets RLS enabled and policies in the same migration as its creation.
4. **Policies are tested adversarially.** Confirming the happy path proves nothing. Attempt the attacks: read a hidden category's products via `product_images`; escalate a role; write with the anonymous key.
5. **No public sign-up.** Admin accounts are created deliberately.
6. **HTTPS only**, with HTTP redirecting.
7. **Never log a secret, token, or session.**
8. **Validate input server-side or in the database**, never only in the client.
9. **Preview deployments never point at the production database.**

**If you find a security problem, stop and report it.** Do not quietly work around it.

---

# 10 · Git Conventions

### Commits

```
type(scope): imperative summary under ~70 chars

Why this change, if not obvious from the summary. What was
considered and rejected, if relevant.

Refs: M4.3
```

**Types:** `feat`, `fix`, `refactor`, `perf`, `docs`, `style`, `test`, `chore`, `build`.
**Scopes:** `web`, `android`, `db`, `design`, `docs`, `ci`.

Reference the task (`M4.3`) so the commit ties back to the plan.

### Rules

- **One logical change per commit.** Reviewable in a few minutes; reverting undoes exactly one thing.
- **Commit at task boundaries**, not milestone boundaries.
- Documentation ships in the same commit as the change it describes.
- **Never commit a broken build.** §1.7 applies before every commit.
- Write why, not what. The diff shows what.
- Prefer a new commit to amending a pushed one.

### Branches

`type/short-description` — `feat/product-gallery`, `fix/upload-rollback`.

Never commit directly to `main`. Branch first.

### Rules for the agent specifically

- **Commit and push only when asked.** Do not commit as a reflex at the end of a task.
- Never use `--no-verify`. Never bypass signing. If a hook fails, fix the cause.
- Never force-push a shared branch.
- Never commit a generated file that should be ignored, or a `.env`.

---

# 11 · AI Collaboration Guidelines

How to work in this repository as an agent.

### Scope

**Do what was asked. Not more.** If you notice an adjacent improvement, mention it — do not make it. Scope creep in an agent-built project is how a small review becomes an unreviewable one.

If the request is ambiguous in a way that changes the work materially, ask. If it is ambiguous in a way a careful colleague would resolve with a sensible default, use the default and say which one you used.

### Reuse before writing

Search before implementing. This project deliberately centralises things — the data-access layer, the shop config module, design tokens, the storage path convention. Writing a second way to do any of them is a defect, not a shortcut.

### Honesty about state

- If a build fails, say so with the output. Never report a passing build you did not run.
- If a task is partly done, say exactly which part and what blocks the rest.
- If you skipped something, say so.
- If you are unsure whether something works, say that rather than implying verification.
- **Do not describe work as complete when it is stubbed.** See §1.6.

### When you disagree with the plan or the PRD

Say so, in a sentence or two, then proceed with what was asked under stated assumptions — unless proceeding would be unsafe or would waste the work if the concern is right, in which case stop and ask.

The PRD is the source of truth, but it is not infallible: it already omitted four fields its own feature list requires (see M3.4). Flag gaps like that rather than silently patching or silently following.

If the user reaffirms a decision after you have raised a concern, that is their call. Proceed with the full request.

### Blocked work

Several tasks are blocked on Open Questions in [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md#risks--open-questions) and three ADRs are **Proposed**. If a task depends on one:

1. Do everything that does not depend on the answer.
2. Say precisely what is blocked and which question blocks it.
3. Do not guess an answer and build on it silently. If you must proceed, state the assumption prominently.

**A task depending on a Proposed ADR cannot be called complete until that ADR is Accepted.**

### Verification

Verify with the acceptance criteria in the plan, not with your own judgement of whether the code looks right. The criteria are deliberately observable — "Lighthouse mobile Performance above 90", "no orphaned storage object after delete". Run the check.

For anything customer-facing, remember that a desktop browser's device emulation is not a phone. The plan requires physical device verification for a reason.

### Reporting

After each task: what changed (with file references), what you verified and its output, anything you noticed but did not do, and the next task. Then stop.

Keep it short. A wall of text describing a three-file change is not thoroughness.
