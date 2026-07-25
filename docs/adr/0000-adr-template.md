# ADR-NNNN: Short title stating the decision

- **Status:** Proposed | Accepted | Superseded by [ADR-NNNN](.) | Deprecated
- **Date:** YYYY-MM-DD
- **Deciders:** who made this call
- **Affects:** which milestones or directories this constrains

## Context

What situation forces a decision. The constraints, the requirement driving it, and what makes it non-obvious.

Write this so someone who arrives in six months and disagrees with the decision can see what you were weighing. If the context section is thin, the decision was probably not worth an ADR.

State facts, not conclusions. If the PRD or a measurement drives the decision, cite it.

## Decision

The decision, stated in one or two sentences, in the active voice: "We will …"

Then the specifics — what this concretely means for the code, the schema, or the workflow. Enough that someone can follow it without re-deriving it.

## Consequences

### What this makes easier

Honest benefits. Not marketing.

### What this makes harder

The costs. Every real decision has some — if this section is empty, the decision has not been thought through, or it did not need an ADR.

### What this commits us to

The things now expensive to change, and what changing them later would cost.

## Alternatives considered

| Alternative | Why not |
|---|---|
| The obvious other option | The specific reason, not "it's worse" |

Record alternatives even when the choice feels obvious now. The value of an ADR is mostly in this table — it is what stops the same debate being re-run every six months, and what tells a future reader whether their new idea was already rejected for a reason that still holds.

## Open sub-questions

Anything this ADR does **not** settle, and which milestone or task settles it. An ADR may be Accepted while leaving details open, as long as they are named here.

## References

- Links to the PRD section, milestone, or external documentation that informed this.

---

## How to use this template

1. Copy to `NNNN-short-kebab-title.md`, numbering sequentially. Never reuse a number.
2. Status starts **Proposed** if it needs a decision from the project owner, **Accepted** if it records a choice already made.
3. **Never edit an Accepted ADR to change its decision.** Write a new ADR that supersedes it, and update the old one's status to point at the new one. The history is the point — an edited ADR loses the record of what was once believed and why it changed.
4. Correcting a typo or adding a reference to an Accepted ADR is fine. Changing what it decided is not.
5. Add it to the table in [README.md](README.md).

### What deserves an ADR

Anything structural, hard to reverse, or likely to be questioned later: choice of platform or framework, the security boundary, the schema contract, how the clients share code or design, build order that constrains later work, anything with a recurring cost.

### What does not

Library choices with no structural consequence, formatting and style (those belong in [CLAUDE.md](../../CLAUDE.md)), and anything a single commit could reverse without touching other code.
