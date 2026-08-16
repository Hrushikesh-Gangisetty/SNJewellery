/**
 * Joins class names, dropping falsy values.
 *
 * Deliberately not `clsx` + `tailwind-merge`: this project has one small
 * component set, so two dependencies would be weight for nothing.
 * CLAUDE.md §3.7 — prefer boring, reach for a dependency only when the
 * platform cannot do it.
 *
 * ── What that costs, and how to stay out of it ──────────────────────
 * This joins; it does not resolve. Passing a utility that conflicts with
 * one already in a component's base class does NOT override it — both
 * land in `class`, at equal specificity, and the winner is whichever
 * Tailwind emitted later in the stylesheet. It is silent, and it is not
 * the one you wrote last.
 *
 * M5.7 found this live: `className="hidden sm:inline-flex"` on a
 * component whose base was `inline-flex` never hid anything, so the
 * mobile header carried both wordmarks and an extra button.
 *
 * **Use a variant to override a base utility** — `max-sm:hidden` rather
 * than `hidden` — because a variant does outrank the bare utility. If a
 * case ever needs real conflict resolution, that is when to revisit the
 * decision above, not before.
 */
export function cn(...parts: readonly (string | false | null | undefined)[]) {
  return parts.filter(Boolean).join(" ");
}
