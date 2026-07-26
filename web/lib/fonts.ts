import { Cormorant_Garamond, Inter } from "next/font/google";

/**
 * Typefaces, per docs/design/typography.md.
 *
 * Self-hosted by `next/font` at build time — no runtime request to
 * Google, which would be a third-party connection, a privacy
 * consideration, and a render-blocking dependency.
 *
 * Only the weights typography.md §3 lists are loaded. Loading the full
 * families would cost several hundred kilobytes against a sub-two-second
 * budget for no benefit.
 */

/**
 * Display serif. **Never used below 28px** — it is high-contrast with
 * thin hairlines that weaken badly at small sizes and on low-DPI Android
 * screens, which describes a large share of this audience.
 *
 * Only `display-xl`, `display-l`, and `heading-l` (28px, the floor) use
 * it. If it still looks weak on a real device in M2.6's check, the
 * fallback is Marcellus — a change to tokens.json, not to components.
 */
export const fontDisplay = Cormorant_Garamond({
  subsets: ["latin"],
  weight: ["500"],
  display: "swap",
  variable: "--font-display-loaded",
  // Georgia is metrically closer to a Garamond than the generic serif,
  // so the swap shifts layout less.
  fallback: ["Georgia", "serif"],
});

/**
 * UI and body. Carries everything below 28px, which is nearly all text.
 *
 * Loaded as a **variable font** — `weight` is deliberately omitted, so
 * one file covers 400, 500, and 600 rather than three static faces.
 *
 * Measured after the change: total on-disk font weight stayed at 308 KB
 * across 12 files, so the variable switch did not reduce the build. What
 * matters is that only **70 KB across 2 files is preloaded** — the Latin
 * subsets actually needed. The other 10 files are Cyrillic, Greek,
 * Vietnamese and latin-ext ranges, emitted with `unicode-range` so a
 * browser rendering English never requests them. Runtime cost is 70 KB,
 * not 308 KB. Re-measure in M12.3 rather than trusting this note.
 */
export const fontBody = Inter({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-body-loaded",
  fallback: ["system-ui", "sans-serif"],
});

/** Applied to <html> so both families are available as CSS variables. */
export const fontVariables = `${fontDisplay.variable} ${fontBody.variable}`;
