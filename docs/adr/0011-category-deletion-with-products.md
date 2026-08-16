# ADR-0011: Deleting a category that still has pieces in it

- **Status:** ✅ **Accepted** — 2026-08-16
- **Date:** 2026-08-16
- **Deciders:** Hrushikesh Gangisetty
- **Affects:** M8.6, M8.8

## Context

Every product carries a `category_id`, and the column is `not null` with
`on delete restrict` ([core tables](../../supabase/migrations/20260726000100_core_tables.sql)).
So a category with pieces filed under it cannot be removed while they point at
it, and the database says so with SQLSTATE **23503**.

M8.8 asks for a decision: *block deletion of a non-empty category with an
explanation, or require reassignment.* The app has to do one of them, because
the alternative — letting the delete through — is not available. It would need
either `on delete set null`, which the column forbids, or `on delete cascade`,
which would delete the owner's photographs and rows as a side effect of tidying
a list. Neither is on the table.

The real choice is therefore between:

1. **Block, and explain.** The delete refuses; the owner refiles the pieces from
   the catalogue screen and deletes the category afterwards.
2. **Reassign, then delete.** The app asks which category the pieces should move
   to, moves all of them, and deletes the empty category.

## Decision

**Block, with an explanation that says how many pieces are involved and offers a
way to see them.**

Deleting a category with pieces in it returns `DeleteCategoryResult.InUse`,
carrying the count. The dialog states it — *"12 pieces are filed under this
category, so it cannot be deleted. Move them to another category first."* — and
offers **Show these pieces**, which opens the catalogue already filtered to that
category and to `All` rather than `Live`.

## Why not reassignment

**Because the destination is a per-piece decision, not a per-category one.** A
category that is being retired holds pieces that belong in different places — a
mixed "Festival Collection" scatters into necklaces, bangles and rings. A bulk
move has to send all of them to one category, so it converts one honest refusal
into forty quietly mis-filed pieces, each of which then appears under the wrong
heading on the website. The owner would have to do the per-piece work anyway,
except now it is hidden.

**Because it is a bulk write the owner cannot review.** The app is used
one-handed, between customers, on mobile data. A single tap that rewrites forty
rows has no undo, and building one would mean recording the previous category of
every piece — a feature considerably larger than the thing it protects.

**Because blocking loses nothing.** Refiling is already possible: the catalogue
filters by category and the edit form changes it. The refusal costs the owner
the pieces' worth of taps they would have spent anyway, and it costs them
nothing they cannot get back.

**Because it matches the invariant rather than working around it.** The schema's
position is that a product always has a real category. Blocking is that
statement surfaced in the UI; reassignment is a client deciding on the owner's
behalf what the constraint should have said.

## Consequences

- A category cannot be deleted until it is empty. The app never leaves a piece
  pointing at a category that is gone, and it does not need to check for one —
  the foreign key is what guarantees it, and no client can bypass it.
- The count is read **after** the refusal, not before the delete. Asking first
  would put a request on every deletion to answer a question that is nearly
  always "none".
- **Archived pieces are counted.** They hold the foreign key exactly as live
  pieces do. The dashboard's figures exclude archived pieces because they answer
  "how big is my catalogue"; this count answers "what is holding the key", and
  excluding them would produce the one genuinely confusing message available
  here — *"0 pieces are filed under this category, so it cannot be deleted"*.
- If the shop ever retires a large category, this is manual work proportional to
  its size. That is the accepted cost, and it is the point at which to revisit
  this decision — with a **reviewable multi-select move** in the catalogue
  screen, not with a hidden bulk write behind a delete button.

## Alternatives considered

**A "move all pieces to…" step inside the delete flow.** Rejected above: it is
the same bulk write with a confirmation in front of it, and a confirmation does
not make forty mis-filed pieces reviewable.

**An "Uncategorised" category the pieces fall into.** Rejected. It makes the
column's `not null` meaningless in practice, and it puts a heading on the
website that means "the shop has not decided" — visible to customers, which is
the one place this should never surface.

**Soft-deleting the category instead.** Rejected as a second archived-style flag
on a table that already has `is_visible`. Hiding a category already does exactly
what a soft delete would do — it and its pieces leave the website, and it stays
in the app — so this would be a third state whose only difference from hiding is
its name.
