# Documentation

Documentation index for the Jewellery Catalogue Platform.

## Where things live

| Location | Contents |
|---|---|
| [prd.md](../prd.md) | **Source of truth for requirements.** What the product must do. |
| [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md) | Milestones, tasks, dependencies, acceptance criteria. |
| [CLAUDE.md](../CLAUDE.md) | Permanent repository instructions and working rules for Claude Code. |
| [adr/](adr/) | Architecture Decision Records — *why* the architecture is what it is. |
| [architecture/](architecture/) | How the system fits together: data flow, rendering, sync. |
| [api/](api/) | Data-access contracts, query documentation, Edge Functions. |
| [database/](database/) | The schema contract, migration workflow, RLS model. |
| [deployment/](deployment/) | Environments, deploy and rollback runbooks. |
| [design/](design/) | The design system: brand, tokens, components, UX, accessibility. |

## Why the PRD and the plan stay at the repository root

Everything else moved into `docs/`, but `prd.md` and `DEVELOPMENT_PLAN.md` are deliberately left at the root, along with `README.md` and `CLAUDE.md`. They are the two documents referenced most often and they are the entry point for anyone — human or agent — picking up the project. Burying the source of truth two directories down adds friction to the thing that should have least.

This is a cheap decision to reverse. If you would rather they lived in `docs/`, moving them requires updating the links in `README.md`, `CLAUDE.md`, and the cross-references inside each.

## Which milestone produces what

Most of these directories are intentionally near-empty right now. Each one's README states what will fill it and when. Nothing here is a placeholder to be quietly forgotten — every document is a named deliverable of a specific milestone in [DEVELOPMENT_PLAN.md](../DEVELOPMENT_PLAN.md).

| Directory | Filled by |
|---|---|
| `adr/` | Continuously — a new ADR whenever an architectural decision is made |
| `design/` | **M1** (Design System) — the next milestone |
| `api/` | M2.5, then extended in M4.1 |
| `database/` | M3.11 |
| `architecture/` | M4.7 (rendering), M9.7 (sync) |
| `deployment/` | M5.8 |

## Documentation rules

1. **Architectural decisions go in `adr/`, not in prose elsewhere.** If a document explains *why* a structural choice was made, that reasoning belongs in an ADR that the document links to.
2. **One home per fact.** Do not restate a rule in two documents; link to the one that owns it. Duplicated documentation drifts, and drifted documentation is worse than none.
3. **Documentation changes ship with the change they describe**, in the same commit. See the workflow rules in [CLAUDE.md](../CLAUDE.md).
4. **Write down what a future contributor would otherwise reverse-engineer.** Not what the code already says plainly.
