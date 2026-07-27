#!/usr/bin/env node
/**
 * Generates the platform design-token artefacts from the single source
 * of truth at docs/design/tokens.json, per ADR-0008.
 *
 *   node tools/generate-tokens.mjs           write the artefacts
 *   node tools/generate-tokens.mjs --check   verify they are current (CI)
 *
 * Emits:
 *   web/app/globals.css                     Tailwind v4 @theme block
 *   android/design-tokens/Tokens.kt          Compose values, wired up in M6.2
 *
 * Before emitting anything it validates every contrast assertion in
 * tokens.json against the WCAG 2.1 formula. A palette change that breaks
 * accessibility fails here rather than reaching a browser — which is the
 * whole point of generating rather than hand-mirroring.
 *
 * Never edit a generated file by hand; edit tokens.json and re-run.
 */

import { readFileSync, writeFileSync, mkdirSync, existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const SOURCE = join(ROOT, "docs/design/tokens.json");
const CSS_OUT = join(ROOT, "web/app/globals.css");
const KT_OUT = join(ROOT, "android/design-tokens/Tokens.kt");

const CHECK = process.argv.includes("--check");
const BANNER = "GENERATED FROM docs/design/tokens.json - DO NOT EDIT BY HAND";

// ── Contrast validation ────────────────────────────────────────────────

const channel = (c) => {
  const s = c / 255;
  return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
};

const luminance = (hex) => {
  const n = parseInt(hex.slice(1), 16);
  return (
    0.2126 * channel((n >> 16) & 255) +
    0.7152 * channel((n >> 8) & 255) +
    0.0722 * channel(n & 255)
  );
};

const contrast = (a, b) => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};

const round2 = (n) => Math.round(n * 100) / 100;

function validateContrast(tokens) {
  const { bodyTextMin, nonTextMin, bodyTextPairs, nonTextPairs } =
    tokens.contrastRules;
  const failures = [];

  for (const [mode, palette] of Object.entries(tokens.colour)) {
    const check = (pairs, min, kind) => {
      for (const [fg, bg] of pairs) {
        if (!palette[fg] || !palette[bg]) {
          failures.push(`${mode}: unknown token in pair ${fg}/${bg}`);
          continue;
        }
        const ratio = round2(contrast(palette[fg], palette[bg]));
        if (ratio < min) {
          failures.push(
            `${mode}: ${fg} on ${bg} = ${ratio}:1, below ${kind} minimum ${min}:1 ` +
              `(${palette[fg]} on ${palette[bg]})`,
          );
        }
      }
    };
    check(bodyTextPairs, bodyTextMin, "body-text");
    check(nonTextPairs, nonTextMin, "non-text");
  }

  return failures;
}

// ── CSS emitter ────────────────────────────────────────────────────────

function cssVar(name) {
  return `--sn-${name}`;
}

function buildCss(tokens) {
  const {
    colour,
    font,
    space,
    radius,
    elevation,
    breakpoint,
    container,
    motion,
    aspect,
  } = tokens;
  const L = [];

  L.push(`/* ${BANNER}`);
  L.push(` *`);
  L.push(` * Regenerate:  node tools/generate-tokens.mjs`);
  L.push(` * Source:      docs/design/tokens.json`);
  L.push(` * Rationale:   docs/design/colour.md, typography.md, layout.md`);
  L.push(` *              docs/adr/0008-design-tokens-single-source.md`);
  L.push(` */`);
  L.push("");
  L.push(`@import "tailwindcss";`);
  L.push("");

  // Semantic colour variables, themeable at runtime.
  const emitPalette = (palette, indent) =>
    Object.entries(palette).map(
      ([k, v]) => `${indent}${cssVar(k)}: ${v};`,
    );

  L.push(`/* Light is the default; dark follows the system preference, and an`);
  L.push(` * explicit data-theme attribute overrides both. */`);
  L.push(`:root {`);
  L.push(...emitPalette(colour.light, "  "));
  L.push("");
  L.push(`  /* Motion durations. Tailwind v4 has no --duration-* theme`);
  L.push(`   * namespace, so these are plain variables, used as`);
  L.push(`   * duration-[var(--sn-duration-base)] or in bespoke CSS. */`);
  for (const [k, v] of Object.entries(motion.duration)) {
    L.push(`  --sn-duration-${k}: ${v};`);
  }
  L.push(`}`);
  L.push("");
  L.push(`@media (prefers-color-scheme: dark) {`);
  L.push(`  :root {`);
  L.push(...emitPalette(colour.dark, "    "));
  L.push(`  }`);
  L.push(`}`);
  L.push("");
  L.push(`:root[data-theme="light"] {`);
  L.push(...emitPalette(colour.light, "  "));
  L.push(`}`);
  L.push("");
  L.push(`:root[data-theme="dark"] {`);
  L.push(...emitPalette(colour.dark, "  "));
  L.push(`}`);
  L.push("");

  // Tailwind theme. `inline` so utilities reference the vars rather than
  // freezing the light-mode value at build time.
  L.push(`@theme inline {`);

  L.push(`  /* Colour */`);
  for (const k of Object.keys(colour.light)) {
    L.push(`  --color-${k}: var(${cssVar(k)});`);
  }

  L.push("");
  L.push(`  /* Font families */`);
  L.push(
    `  --font-display: var(${font.family.displayLoadedVar}), ${font.family.displayFallback};`,
  );
  L.push(
    `  --font-body: var(${font.family.bodyLoadedVar}), ${font.family.bodyFallback};`,
  );

  L.push("");
  L.push(`  /* Type scale */`);
  for (const [name, s] of Object.entries(font.scale)) {
    const size = s.fluid
      ? `clamp(${s.min}, ${s.fluid}, ${s.max})`
      : s.size;
    L.push(`  --text-${name}: ${size};`);
    L.push(`  --text-${name}--line-height: ${s.lineHeight};`);
    L.push(`  --text-${name}--font-weight: ${s.weight};`);
    if (s.tracking && s.tracking !== "0") {
      L.push(`  --text-${name}--letter-spacing: ${s.tracking};`);
    }
  }

  L.push("");
  L.push(`  /* Spacing */`);
  for (const [k, v] of Object.entries(space)) {
    L.push(`  --spacing-${k}: ${v};`);
  }

  L.push("");
  L.push(`  /* Radius */`);
  for (const [k, v] of Object.entries(radius)) {
    L.push(`  --radius-${k}: ${v};`);
  }

  L.push("");
  L.push(`  /* Elevation */`);
  for (const [k, v] of Object.entries(elevation)) {
    if (k.startsWith("$")) continue;
    // Each step carries both expressions; the web wants the shadow and
    // Compose wants the dp. See layout.md §3.
    L.push(`  --shadow-elevation-${k}: ${v.shadow};`);
  }

  L.push("");
  L.push(`  /* Breakpoints */`);
  for (const [k, v] of Object.entries(breakpoint)) {
    L.push(`  --breakpoint-${k}: ${v};`);
  }

  L.push("");
  L.push(`  /* Containers */`);
  for (const [k, v] of Object.entries(container)) {
    L.push(`  --container-${k}: ${v};`);
  }

  L.push("");
  L.push(`  /* Easing */`);
  for (const [k, v] of Object.entries(motion.easing)) {
    L.push(`  --ease-${k}: ${v};`);
  }

  L.push("");
  L.push(`  /* Image aspect ratios */`);
  for (const [k, v] of Object.entries(aspect)) {
    if (k.startsWith("$")) continue;
    L.push(`  --aspect-${k}: ${v};`);
  }

  L.push(`}`);
  L.push("");

  // Utilities the scale implies but Tailwind cannot express as theme keys.
  const label = font.scale.label;
  const spec = font.scale.spec;
  L.push(`/* Scale entries carrying more than Tailwind's theme keys allow. */`);
  if (label?.transform) {
    L.push(`@utility text-label {`);
    L.push(`  font-size: var(--text-label);`);
    L.push(`  line-height: var(--text-label--line-height);`);
    L.push(`  font-weight: var(--text-label--font-weight);`);
    L.push(`  letter-spacing: var(--text-label--letter-spacing);`);
    L.push(`  text-transform: ${label.transform};`);
    L.push(`}`);
    L.push("");
  }
  if (spec?.numeric) {
    L.push(`/* Purity and weight: tabular figures so digits align in grids. */`);
    L.push(`@utility text-spec {`);
    L.push(`  font-size: var(--text-spec);`);
    L.push(`  line-height: var(--text-spec--line-height);`);
    L.push(`  font-weight: var(--text-spec--font-weight);`);
    L.push(`  font-variant-numeric: ${spec.numeric};`);
    L.push(`}`);
    L.push("");
  }

  L.push(`/* Base element defaults. Everything else is a utility. */`);
  L.push(`body {`);
  L.push(`  background-color: var(${cssVar("surface")});`);
  L.push(`  color: var(${cssVar("text-primary")});`);
  L.push(`  font-family: var(--font-body);`);
  L.push(`  font-size: var(--text-body-m);`);
  L.push(`  line-height: var(--text-body-m--line-height);`);
  L.push(`}`);
  L.push("");
  L.push(`/* Visible focus on every focusable element - see accessibility.md */`);
  L.push(`:focus-visible {`);
  L.push(`  outline: 2px solid var(${cssVar("focus")});`);
  L.push(`  outline-offset: 2px;`);
  L.push(`}`);
  L.push("");
  L.push(`/* Entrance for content arriving AFTER interaction - see motion.md §4.`);
  L.push(` * Opacity plus an 8px rise, never a stagger: a visible cascade across`);
  L.push(` * a large grid is a delay imposed on someone trying to browse.`);
  L.push(` *`);
  L.push(` * Above-the-fold content must never use this. Animating the first`);
  L.push(` * row delays the customer's first sight of the merchandise and`);
  L.push(` * harms LCP, which is why this is opt-in per element rather than a`);
  L.push(` * rule on the grid.`);
  L.push(` *`);
  L.push(` * Under reduced motion the global rule below collapses the duration,`);
  L.push(` * and 'both' leaves the element at its natural end state - reduced,`);
  L.push(` * not invisible. */`);
  L.push(`@keyframes sn-enter {`);
  L.push(`  from {`);
  L.push(`    opacity: 0;`);
  L.push(`    transform: translateY(var(--spacing-2));`);
  L.push(`  }`);
  L.push(`}`);
  L.push("");
  L.push(`.sn-enter {`);
  L.push(
    `  animation: sn-enter var(${cssVar("duration-base")}) var(--ease-decelerate) both;`,
  );
  L.push(`}`);
  L.push("");
  L.push(`/* Skeleton pulse - see motion.md §4, which specifies 'deliberate,`);
  L.push(` * looped' with standard easing. Tailwind's own animate-pulse runs`);
  L.push(` * 2s on a curve of its own and would be a token written by hand.`);
  L.push(` *`);
  L.push(` * 'alternate' makes deliberate the time in EACH direction, so the`);
  L.push(` * full cycle is a calm ~1s. A 480ms round trip would flicker, and`);
  L.push(` * a flickering placeholder competes with the photographs it is`);
  L.push(` * standing in for.`);
  L.push(` *`);
  L.push(` * It never reaches 0: under reduced motion the loop stops, and a`);
  L.push(` * placeholder that settled invisible would read as an empty page`);
  L.push(` * rather than a loading one (motion.md §6). */`);
  L.push(`@keyframes sn-pulse {`);
  L.push(`  from {`);
  L.push(`    opacity: 1;`);
  L.push(`  }`);
  L.push(`  to {`);
  L.push(`    opacity: 0.55;`);
  L.push(`  }`);
  L.push(`}`);
  L.push("");
  L.push(`.sn-pulse {`);
  L.push(
    `  animation: sn-pulse var(${cssVar("duration-deliberate")}) var(--ease-standard) infinite alternate;`,
  );
  L.push(`}`);
  L.push("");
  L.push(`/* Honour reduced-motion globally rather than per component, so a`);
  L.push(` * missed check in one component cannot reintroduce motion.`);
  L.push(` * See motion.md and accessibility.md. */`);
  L.push(`@media (prefers-reduced-motion: reduce) {`);
  L.push(`  *,`);
  L.push(`  *::before,`);
  L.push(`  *::after {`);
  L.push(`    animation-duration: 0.01ms !important;`);
  L.push(`    animation-iteration-count: 1 !important;`);
  L.push(`    transition-duration: 0.01ms !important;`);
  L.push(`    scroll-behavior: auto !important;`);
  L.push(`  }`);
  L.push(`}`);
  L.push("");

  return L.join("\n");
}

// ── Kotlin emitter ─────────────────────────────────────────────────────

const kebabToCamel = (s) =>
  s.replace(/-([a-z0-9])/g, (_, c) => c.toUpperCase());

const kebabToConst = (s) => s.replace(/-/g, "_").toUpperCase();

const remToPx = (v) => {
  const m = /^([\d.]+)rem$/.exec(v);
  return m ? Math.round(parseFloat(m[1]) * 16) : null;
};

function buildKotlin(tokens) {
  const {
    meta,
    colour,
    font,
    space,
    radius,
    elevation,
    container,
    touchTarget,
    motion,
  } = tokens;
  const L = [];

  L.push(`package ${meta.androidPackage}`);
  L.push("");
  L.push(`import androidx.compose.ui.graphics.Color`);
  L.push(`import androidx.compose.ui.unit.dp`);
  L.push(`import androidx.compose.ui.unit.sp`);
  L.push("");
  L.push(`/**`);
  L.push(` * ${BANNER}`);
  L.push(` *`);
  L.push(` * Regenerate:  node tools/generate-tokens.mjs`);
  L.push(` * Source:      docs/design/tokens.json`);
  L.push(` *`);
  L.push(` * These are raw token values. M6.2 maps them into a Material 3`);
  L.push(` * ColorScheme and Typography. Screens must reference the theme,`);
  L.push(` * never this object directly.`);
  L.push(` *`);
  L.push(` * Text sizes are in sp so the user's system font-size preference is`);
  L.push(` * respected; everything else is dp.`);
  L.push(` */`);
  L.push(`object Tokens {`);

  for (const [mode, palette] of Object.entries(colour)) {
    L.push(`    object ${mode === "light" ? "Light" : "Dark"} {`);
    for (const [k, v] of Object.entries(palette)) {
      L.push(`        val ${kebabToCamel(k)} = Color(0xFF${v.slice(1).toUpperCase()})`);
    }
    L.push(`    }`);
    L.push("");
  }

  L.push(`    object Space {`);
  for (const [k, v] of Object.entries(space)) {
    const px = v === "0" ? 0 : remToPx(v);
    if (px !== null) L.push(`        val s${k} = ${px}.dp`);
  }
  L.push(`    }`);
  L.push("");

  L.push(`    object Radius {`);
  for (const [k, v] of Object.entries(radius)) {
    const px = v === "0" ? 0 : v.endsWith("px") ? parseInt(v, 10) : remToPx(v);
    if (px !== null) {
      L.push(`        val ${kebabToCamel(k)} = ${px}.dp`);
    }
  }
  L.push(`    }`);
  L.push("");

  L.push(`    object Type {`);
  L.push(`        const val DISPLAY_FAMILY = "${font.family.display}"`);
  L.push(`        const val BODY_FAMILY = "${font.family.body}"`);
  L.push(`        /** Hard floor for the display serif - see typography.md #1. */`);
  L.push(`        val displayMin = ${font.displayMinPx}.sp`);
  L.push("");
  for (const [name, s] of Object.entries(font.scale)) {
    // Fluid web sizes collapse to their maximum on Android, which has no
    // viewport-relative unit. The min is reachable via the size classes.
    const raw = s.fluid ? s.max : s.size;
    const px = remToPx(raw);
    if (px === null) continue;
    const id = kebabToCamel(name);
    L.push(`        val ${id}Size = ${px}.sp`);
    L.push(`        val ${id}LineHeight = ${Math.round(px * s.lineHeight)}.sp`);
    L.push(`        const val ${kebabToConst(name)}_WEIGHT = ${s.weight}`);
  }
  L.push(`    }`);
  L.push("");

  L.push(`    object Elevation {`);
  L.push(`        /** layout.md #3: e0 with a hairline border is the default.`);
  L.push(`         *  Dark mode expresses raised-ness with surfaceRaised, not`);
  L.push(`         *  shadow, because a shadow is nearly invisible there. */`);
  for (const [k, v] of Object.entries(elevation)) {
    if (k.startsWith("$")) continue;
    L.push(`        val e${k} = ${v.dp}.dp`);
  }
  L.push(`    }`);
  L.push("");

  L.push(`    object Motion {`);
  for (const [k, v] of Object.entries(motion.duration)) {
    const ms = parseInt(v, 10);
    L.push(`        const val ${kebabToConst(k)}_MS = ${ms}L`);
  }
  L.push(`    }`);
  L.push("");

  L.push(`    object Layout {`);
  for (const [k, v] of Object.entries(container)) {
    const px = remToPx(v);
    if (px !== null) L.push(`        val container${k[0].toUpperCase() + k.slice(1)} = ${px}.dp`);
  }
  L.push(`        /** Material 3 minimum touch target. */`);
  L.push(`        val touchTarget = ${parseInt(touchTarget.android, 10)}.dp`);
  L.push(`    }`);
  L.push(`}`);
  L.push("");

  return L.join("\n");
}

// ── Main ───────────────────────────────────────────────────────────────

function main() {
  const tokens = JSON.parse(readFileSync(SOURCE, "utf8"));

  const failures = validateContrast(tokens);
  if (failures.length > 0) {
    console.error("Contrast validation FAILED - nothing was generated:\n");
    for (const f of failures) console.error(`  ${f}`);
    console.error(
      "\nFix the palette in docs/design/tokens.json, then update the measured\n" +
        "ratios in docs/design/colour.md to match.",
    );
    process.exit(1);
  }

  const outputs = [
    [CSS_OUT, buildCss(tokens), "web/app/globals.css"],
    [KT_OUT, buildKotlin(tokens), "android/design-tokens/Tokens.kt"],
  ];

  let stale = 0;
  for (const [path, content, label] of outputs) {
    const current = existsSync(path) ? readFileSync(path, "utf8") : null;
    const same = current !== null && current.replace(/\r\n/g, "\n") === content;

    if (CHECK) {
      if (!same) {
        console.error(`STALE: ${label}`);
        stale++;
      } else {
        console.log(`current: ${label}`);
      }
      continue;
    }

    if (same) {
      console.log(`unchanged: ${label}`);
    } else {
      mkdirSync(dirname(path), { recursive: true });
      writeFileSync(path, content, "utf8");
      console.log(`${current === null ? "created" : "updated"}: ${label}`);
    }
  }

  const pairCount =
    (tokens.contrastRules.bodyTextPairs.length +
      tokens.contrastRules.nonTextPairs.length) *
    Object.keys(tokens.colour).length;

  if (CHECK && stale > 0) {
    console.error(
      `\n${stale} generated file(s) are stale. Run: node tools/generate-tokens.mjs`,
    );
    process.exit(1);
  }

  console.log(`\ncontrast: ${pairCount} pairs validated, all pass`);
}

main();
