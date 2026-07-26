/**
 * Joins class names, dropping falsy values.
 *
 * Deliberately not `clsx` + `tailwind-merge`: this project has one small
 * component set and no runtime class conflicts to resolve, so two
 * dependencies would be weight for nothing. CLAUDE.md §3.7 — prefer
 * boring, reach for a dependency only when the platform cannot do it.
 *
 * If genuine conflict resolution is ever needed (a variant overriding a
 * base utility), revisit then.
 */
export function cn(...parts: readonly (string | false | null | undefined)[]) {
  return parts.filter(Boolean).join(" ");
}
