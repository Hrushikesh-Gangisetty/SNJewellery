#!/usr/bin/env node
/**
 * Compares the two halves of the schema contract.
 *
 * `web/lib/data/database.types.ts` is generated from the live database by
 * `npm run db:types`, so it is the authority. The Kotlin models in
 * `android/.../data/models/SchemaContract.kt` are hand-written, so they
 * are what drifts.
 *
 * CLAUDE.md §3.3 requires a migration to update both clients in one
 * commit. Nothing enforced that — a forgotten Kotlin column produced no
 * error anywhere, on either side, until a row failed to deserialise on a
 * phone. This is the enforcement.
 *
 * Compares table sets, column sets per table, nullability, and enum
 * values. Deliberately does NOT compare types: the mapping is
 * intentional and documented (uuid→String, timestamptz→String,
 * numeric→Double), and encoding it here would mean two places to change.
 *
 * Usage:  node tools/check-schema-contract.mjs
 */

import { readFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const TS = join(ROOT, "web/lib/data/database.types.ts");
const KT = join(
  ROOT,
  "android/app/src/main/java/com/snjewellery/admin/data/models/SchemaContract.kt",
);

/** camelCase a snake_case column, matching the Kotlin property convention. */
const camel = (s) => s.replace(/_([a-z])/g, (_, c) => c.toUpperCase());

// ── The generated TypeScript side ─────────────────────────────────────

function parseTypeScript(source) {
  const tables = new Map();

  // Each table appears as `name: { Row: { ... }` inside public.Tables.
  const tableBlocks = source.matchAll(
    /^ {6}(\w+): \{\n {8}Row: \{\n(.*?)\n {8}\}/gms,
  );

  for (const [, table, body] of tableBlocks) {
    const columns = new Map();
    for (const line of body.split("\n")) {
      const m = line.match(/^\s*(\w+):\s*(.+?)\s*$/);
      if (!m) continue;
      columns.set(m[1], { nullable: / \| null$/.test(m[2]) });
    }
    if (columns.size > 0) tables.set(table, columns);
  }

  const enums = new Map();
  // Every schema in the file has an Enums block, and the first one
  // (graphql_public) is empty — matching only the first is how this
  // check silently reported "0 enums" and passed.
  for (const [, block] of source.matchAll(/Enums: \{\n([\s\S]*?)\n {4}\}/g)) {
    for (const line of block.split("\n")) {
      const m = line.match(/^\s*(\w+):\s*(.+?)\s*$/);
      if (!m) continue;
      const values = [...m[2].matchAll(/"([^"]+)"/g)].map((v) => v[1]);
      if (values.length > 0) enums.set(m[1], new Set(values));
    }
  }

  return { tables, enums };
}

// ── The hand-written Kotlin side ──────────────────────────────────────

/**
 * `CategoryRow` → `categories`. The Kotlin class name cannot be derived
 * from the table reliably (`purities` → `PurityRow`), so the mapping is
 * declared rather than guessed.
 */
const CLASS_TO_TABLE = {
  CategoryRow: "categories",
  PurityRow: "purities",
  ProductRow: "products",
  ProductImageRow: "product_images",
  MetalRateRow: "metal_rates",
  UserRow: "users",
};

const KOTLIN_ENUM_TO_PG = {
  ProductImageAspect: "product_image_aspect",
  UserRole: "user_role",
  Metal: "metal",
};

function parseKotlin(source) {
  const tables = new Map();

  for (const [className, table] of Object.entries(CLASS_TO_TABLE)) {
    const m = source.match(
      new RegExp(`data class ${className}\\(([\\s\\S]*?)\\n\\)`),
    );
    if (!m) continue;

    const columns = new Map();
    // @SerialName("x") val y: T? = null
    for (const field of m[1].matchAll(
      /@SerialName\("([^"]+)"\)\s*val\s+\w+:\s*([^,\n]+)/g,
    )) {
      columns.set(field[1], { nullable: /\?(\s*=.*)?$/.test(field[2].trim()) });
    }
    tables.set(table, columns);
  }

  const enums = new Map();
  for (const [kotlinName, pgName] of Object.entries(KOTLIN_ENUM_TO_PG)) {
    const m = source.match(
      new RegExp(`enum class ${kotlinName} \\{([\\s\\S]*?)\\n\\}`),
    );
    if (!m) continue;
    enums.set(
      pgName,
      new Set([...m[1].matchAll(/@SerialName\("([^"]+)"\)/g)].map((v) => v[1])),
    );
  }

  return { tables, enums };
}

// ── Compare ──────────────────────────────────────────────────────────

const problems = [];
const ts = parseTypeScript(readFileSync(TS, "utf8"));
const kt = parseKotlin(readFileSync(KT, "utf8"));

if (ts.tables.size === 0) {
  problems.push("parsed no tables from the generated types — the parser is stale");
}
if (kt.tables.size === 0) {
  problems.push("parsed no data classes from the Kotlin models — the parser is stale");
}
// A check that silently compares nothing is worse than no check: it
// reports success. Both sides are known to have enums, so zero means the
// parser broke, not that the schema changed.
if (ts.enums.size === 0) {
  problems.push("parsed no enums from the generated types — the parser is stale");
}
if (kt.enums.size === 0) {
  problems.push("parsed no enums from the Kotlin models — the parser is stale");
}

for (const [table, tsColumns] of ts.tables) {
  const ktColumns = kt.tables.get(table);
  if (!ktColumns) {
    problems.push(`${table}: no Kotlin data class (add one, and to CLASS_TO_TABLE)`);
    continue;
  }

  for (const [column, { nullable }] of tsColumns) {
    const ktColumn = ktColumns.get(column);
    if (!ktColumn) {
      problems.push(`${table}.${column}: missing from Kotlin (as ${camel(column)})`);
    } else if (ktColumn.nullable !== nullable) {
      problems.push(
        `${table}.${column}: nullable in ${nullable ? "Postgres" : "Kotlin"} only`,
      );
    }
  }

  for (const column of ktColumns.keys()) {
    if (!tsColumns.has(column)) {
      problems.push(`${table}.${column}: in Kotlin but not in the database`);
    }
  }
}

for (const [name, tsValues] of ts.enums) {
  const ktValues = kt.enums.get(name);
  if (!ktValues) {
    problems.push(`enum ${name}: no Kotlin equivalent`);
    continue;
  }
  for (const v of tsValues) {
    if (!ktValues.has(v)) problems.push(`enum ${name}: "${v}" missing from Kotlin`);
  }
  for (const v of ktValues) {
    if (!tsValues.has(v)) problems.push(`enum ${name}: "${v}" in Kotlin only`);
  }
}

if (problems.length > 0) {
  console.error("schema contract MISMATCH:\n");
  for (const p of problems) console.error(`  - ${p}`);
  console.error(
    "\nBoth clients must change with the migration, in one commit." +
      "\nSee docs/database/schema.md § Changing this schema.",
  );
  process.exit(1);
}

const columnCount = [...ts.tables.values()].reduce((n, c) => n + c.size, 0);
console.log(
  `schema contract: ${ts.tables.size} tables, ${columnCount} columns, ` +
    `${ts.enums.size} enums — TypeScript and Kotlin agree`,
);
