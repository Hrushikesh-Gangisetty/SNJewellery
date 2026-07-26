/**
 * Adversarial RLS verification against a live Supabase project.
 *
 * M3.7's acceptance criteria require that the policies be ATTACKED, not
 * merely shown to permit the happy path. A policy that allows legitimate
 * reads can still leak through an unexpected join, and that failure is
 * silent — which is why this file exists.
 *
 * Run:
 *   SUPABASE_URL=... SUPABASE_ANON_KEY=... node supabase/tests/rls.mjs
 *
 * Or, without handling keys yourself:
 *   node supabase/tests/rls.mjs --from-cli
 *
 * Uses the ANON key only. It never needs, and must never be given, the
 * service-role key — that bypasses RLS and would make every test pass
 * meaninglessly.
 */

import { execFileSync } from "node:child_process";

// ── Credentials ───────────────────────────────────────────────────────
let URL_ = process.env.SUPABASE_URL;
let ANON = process.env.SUPABASE_ANON_KEY;

if (process.argv.includes("--from-cli")) {
  const ref =
    process.env.SUPABASE_PROJECT_REF ??
    execFileSync("npx", ["supabase", "projects", "list", "-o", "json"], {
      encoding: "utf8",
      shell: true,
    })
      .trim()
      .match(/"(?:id|reference_id)":\s*"([a-z]{20})"/)?.[1];

  if (!ref) throw new Error("Could not determine the project ref.");
  const keys = JSON.parse(
    execFileSync(
      "npx",
      ["supabase", "projects", "api-keys", "--project-ref", ref, "-o", "json"],
      { encoding: "utf8", shell: true },
    ),
  );
  ANON = keys.find((k) => k.name === "anon")?.api_key;
  URL_ = `https://${ref}.supabase.co`;
}

if (!URL_ || !ANON) {
  console.error(
    "Missing credentials. Set SUPABASE_URL and SUPABASE_ANON_KEY, or pass --from-cli.",
  );
  process.exit(2);
}

const rest = `${URL_.replace(/\/+$/, "")}/rest/v1`;
const headers = {
  apikey: ANON,
  Authorization: `Bearer ${ANON}`,
  "Content-Type": "application/json",
};

// ── Harness ───────────────────────────────────────────────────────────
let pass = 0;
let fail = 0;

function ok(name, condition, detail = "") {
  if (condition) {
    pass++;
    console.log(`  PASS  ${name}`);
  } else {
    fail++;
    console.log(`  FAIL  ${name}${detail ? `\n          ${detail}` : ""}`);
  }
}

async function get(path) {
  const res = await fetch(`${rest}/${path}`, { headers });
  const text = await res.text();
  let body;
  try {
    body = JSON.parse(text);
  } catch {
    body = text;
  }
  return { status: res.status, body };
}

/**
 * Attempts a write and reports whether RLS blocked it.
 *
 * ── The subtlety this function exists for ────────────────────────────
 * RLS denial does NOT always surface as an HTTP error, and assuming it
 * does produces a test that fails on secure behaviour.
 *
 *   INSERT  — the WITH CHECK clause rejects the row, so PostgREST
 *             returns 401/403. An error, as expected.
 *
 *   UPDATE  — there is no UPDATE policy for anon, so RLS makes the row
 *   DELETE    invisible to the statement. Zero rows match, nothing
 *             changes, and PostgREST returns **204 No Content — a
 *             success status**.
 *
 * So a 204 on an UPDATE means "your write matched nothing", which is
 * exactly what we want, but it looks like success. The only sound test
 * is to ask for the affected rows back and assert the set is empty.
 *
 * `Prefer: return=representation` does that.
 */
async function attemptWrite(method, path, payload) {
  const res = await fetch(`${rest}/${path}`, {
    method,
    headers: { ...headers, Prefer: "return=representation" },
    body: payload ? JSON.stringify(payload) : undefined,
  });

  const text = await res.text();
  let rows = null;
  try {
    rows = JSON.parse(text);
  } catch {
    /* empty body on 204 */
  }

  const errored = res.status >= 400;
  const affectedNothing = Array.isArray(rows) && rows.length === 0;

  return {
    blocked: errored || affectedNothing,
    detail: `status ${res.status}, body ${text.slice(0, 120) || "(empty)"}`,
  };
}

// ── Tests ─────────────────────────────────────────────────────────────
console.log(`\nRLS verification against ${URL_}\nUsing the ANON key.\n`);

// Rule 1: the public CAN read the published catalogue.
const products = await get("products?select=slug,name,sold,archived&limit=100");
ok(
  "anon can read published products",
  products.status === 200 && Array.isArray(products.body) && products.body.length > 0,
  `status ${products.status} · ${JSON.stringify(products.body).slice(0, 160)}`,
);

const slugs = Array.isArray(products.body)
  ? products.body.map((p) => p.slug)
  : [];

// Rule 2: archived never leaks.
ok(
  "archived product is invisible to anon",
  !slugs.includes("discontinued-pendant-design"),
  `saw: ${slugs.join(", ")}`,
);
ok(
  "archived product is invisible when requested BY SLUG directly",
  (await get("products?slug=eq.discontinued-pendant-design&select=slug")).body
    ?.length === 0,
);

// Rule 3: hidden categories, and their products, never leak.
const cats = await get("categories?select=slug,is_visible&limit=100");
const catSlugs = Array.isArray(cats.body) ? cats.body.map((c) => c.slug) : [];
ok(
  "anon can read visible categories",
  cats.status === 200 && catSlugs.length > 0,
  `status ${cats.status}`,
);
ok(
  "hidden category is invisible to anon",
  !catSlugs.includes("unreleased-collection"),
  `saw: ${catSlugs.join(", ")}`,
);
ok(
  "no category returned has is_visible = false",
  Array.isArray(cats.body) && cats.body.every((c) => c.is_visible === true),
);

// THE TRAP: featured AND in a hidden category. This is what leaks through
// a home-page query that filters on `featured` but forgets visibility.
ok(
  "featured product inside a hidden category does NOT leak",
  !slugs.includes("unreleased-festival-collection-piece"),
  `saw: ${slugs.join(", ")}`,
);
const featured = await get("products?featured=eq.true&select=slug");
ok(
  "...nor when querying featured = true explicitly",
  Array.isArray(featured.body) &&
    !featured.body.some(
      (p) => p.slug === "unreleased-festival-collection-piece",
    ),
  JSON.stringify(featured.body).slice(0, 200),
);

// Rule 4: sold products ARE visible. Hiding them would be a bug.
ok(
  "sold product IS visible to anon (stays in the catalogue with a badge)",
  slugs.includes("diamond-cut-jhumka-earrings"),
  `saw: ${slugs.join(", ")}`,
);

// THE ATTACK: reach a hidden product's photographs by querying the images
// table directly, bypassing any join through products.
const allImages = await get("product_images?select=storage_path&limit=200");
const paths = Array.isArray(allImages.body)
  ? allImages.body.map((i) => i.storage_path)
  : [];
ok(
  "archived product's images are NOT reachable via product_images directly",
  !paths.some((p) => p.includes("discontinued-pendant-design")),
  `leaked: ${paths.filter((p) => p.includes("discontinued")).join(", ")}`,
);
ok(
  "hidden category product's images are NOT reachable via product_images",
  !paths.some((p) => p.includes("unreleased-festival")),
  `leaked: ${paths.filter((p) => p.includes("unreleased")).join(", ")}`,
);

// Purities are public — the site needs the labels.
const purities = await get("purities?select=code&order=display_order");
ok(
  "anon can read purities",
  purities.status === 200 && purities.body?.length === 3,
  `status ${purities.status} · ${JSON.stringify(purities.body)}`,
);

// The users table must not be readable by an anonymous client.
const users = await get("users?select=id,email,role");
ok(
  "anon cannot read the users table",
  users.status !== 200 || (Array.isArray(users.body) && users.body.length === 0),
  `status ${users.status} · ${JSON.stringify(users.body).slice(0, 160)}`,
);

// Rule 5: every write is rejected.
const attempts = [
  [
    "anon cannot INSERT a product",
    () =>
      attemptWrite("POST", "products", {
        slug: "rls-probe",
        name: "RLS probe",
        category_id: "00000000-0000-0000-0000-000000000000",
      }),
  ],
  [
    "anon cannot UPDATE a product",
    () =>
      attemptWrite("PATCH", "products?slug=eq.temple-design-bridal-necklace", {
        name: "tampered",
      }),
  ],
  [
    "anon cannot DELETE a product",
    () =>
      attemptWrite("DELETE", "products?slug=eq.temple-design-bridal-necklace"),
  ],
  [
    "anon cannot INSERT a category",
    () => attemptWrite("POST", "categories", { slug: "probe", name: "Probe" }),
  ],
  [
    "anon cannot un-hide a category",
    () =>
      attemptWrite("PATCH", "categories?slug=eq.unreleased-collection", {
        is_visible: true,
      }),
  ],
  [
    "anon cannot un-archive a product",
    () =>
      attemptWrite("PATCH", "products?slug=eq.discontinued-pendant-design", {
        archived: false,
      }),
  ],
  [
    "anon cannot DELETE a product image",
    () => attemptWrite("DELETE", "product_images?display_order=eq.0"),
  ],
  [
    "anon cannot INSERT a product image",
    () =>
      attemptWrite("POST", "product_images", {
        product_id: "00000000-0000-0000-0000-000000000000",
        url: "/x.svg",
        storage_path: "x",
      }),
  ],
  [
    "anon cannot grant itself a role",
    () =>
      attemptWrite("POST", "users", {
        id: "00000000-0000-0000-0000-000000000000",
        role: "admin",
      }),
  ],
];

for (const [name, run] of attempts) {
  const r = await run();
  ok(name, r.blocked, r.detail);
}

// Confirm the tamper attempts changed nothing.
const after = await get(
  "products?slug=eq.temple-design-bridal-necklace&select=name",
);
ok(
  "the product survived every tamper attempt unchanged",
  after.body?.[0]?.name === "Temple Design Bridal Necklace",
  JSON.stringify(after.body),
);
const stillHidden = await get(
  "categories?slug=eq.unreleased-collection&select=slug",
);
ok(
  "the hidden category is still hidden after the un-hide attempt",
  stillHidden.body?.length === 0,
);

console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail === 0 ? 0 : 1);
