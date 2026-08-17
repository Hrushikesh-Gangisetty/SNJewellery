// Verifies the M9.2 revalidation endpoint and the M9.3 tag map.
//
// Run with:  npm run test:revalidate
//
// Two things are checked, matching M9.2's acceptance criteria: a request
// without the correct secret is rejected, and a duplicate delivery causes
// no error and no double-work. The tag map is checked separately because
// a mutation mapped to the wrong tags is a silently stale page - the
// failure ADR-0006 calls out as the one worth testing for.
//
// The route is imported directly and called with a Request. That keeps
// this a plain Node script like catalogue-contract.mjs, with no server to
// start and no framework, but it does mean `revalidateTag` has to be
// stubbed - the real one needs a Next.js request context. The stub is
// substituted by a resolve hook registered in the npm script, because
// `next/cache` is a bare specifier that a plain import cannot intercept.

// Must be set before the route module loads: it reads the secret through
// lib/config/env, which memoises on first access.
process.env.REVALIDATION_SECRET = "test-secret-value";

const revalidated = [];
let revalidateThrows = false;

const cacheStub = await import("./stubs/next-cache.mjs");
cacheStub.__configure({
  onRevalidate: (tag) => {
    if (revalidateThrows) throw new Error("simulated revalidation failure");
    revalidated.push(tag);
  },
});

const { POST } = await import("../../../app/api/revalidate/route.ts");
const { parseWebhookPayload, tagsFor } = await import("../revalidate.ts");

console.log(`
Revalidation contract - M9.2 endpoint, M9.3 tag map
`);

let pass = 0,
  fail = 0;
const ok = (name, cond, detail = "") => {
  if (cond) {
    pass++;
    console.log(`  PASS  ${name}`);
  } else {
    fail++;
    console.log(`  FAIL  ${name}${detail ? "  -> " + detail : ""}`);
  }
};

const SECRET = "test-secret-value";

const post = (body, secret) =>
  POST(
    new Request("https://example.test/api/revalidate", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        ...(secret === undefined ? {} : { "x-revalidation-secret": secret }),
      },
      body: typeof body === "string" ? body : JSON.stringify(body),
    }),
  );

const productChange = {
  type: "UPDATE",
  table: "products",
  record: { slug: "temple-design-bridal-necklace" },
  old_record: { slug: "temple-design-bridal-necklace" },
};

// ── The secret ───────────────────────────────────────────────────────
revalidated.length = 0;

ok("missing secret is rejected", (await post(productChange)).status === 401);
ok(
  "wrong secret is rejected",
  (await post(productChange, "not-the-secret")).status === 401,
);
ok(
  "secret of the same length but different value is rejected",
  (await post(productChange, "test-secret-valuX")).status === 401,
);
ok(
  "empty secret is rejected",
  (await post(productChange, "")).status === 401,
);
ok(
  "no tag was revalidated by any rejected request",
  revalidated.length === 0,
  revalidated.join(","),
);
ok(
  "correct secret is accepted",
  (await post(productChange, SECRET)).status === 200,
);

// ── Malformed bodies ─────────────────────────────────────────────────
revalidated.length = 0;

ok(
  "invalid JSON is a 400, not a 500",
  (await post("{not json", SECRET)).status === 400,
);
ok(
  "unknown table is rejected",
  (await post({ ...productChange, table: "users" }, SECRET)).status === 400,
);
ok(
  "unknown operation is rejected",
  (await post({ ...productChange, type: "TRUNCATE" }, SECRET)).status === 400,
);
ok(
  "row that is not an object is rejected",
  (await post({ ...productChange, record: "nope" }, SECRET)).status === 400,
);
ok(
  "no tag was revalidated by any malformed request",
  revalidated.length === 0,
  revalidated.join(","),
);

// ── Idempotency: the duplicate-delivery criterion ────────────────────
revalidated.length = 0;

const first = await post(productChange, SECRET);
const firstTags = (await first.json()).revalidated;
const afterFirst = [...revalidated];

const second = await post(productChange, SECRET);
const secondTags = (await second.json()).revalidated;

ok("duplicate delivery also returns 200", second.status === 200);
ok(
  "duplicate delivery revalidates the same tags, not more",
  JSON.stringify(firstTags) === JSON.stringify(secondTags),
  `${JSON.stringify(firstTags)} vs ${JSON.stringify(secondTags)}`,
);
ok(
  "duplicate delivery does no extra work per delivery",
  revalidated.length === afterFirst.length * 2,
  `${afterFirst.length} then ${revalidated.length}`,
);

// ── A revalidation failure is a 500, so the webhook retries ──────────
revalidateThrows = true;
ok(
  "a failing revalidateTag yields 500",
  (await post(productChange, SECRET)).status === 500,
);
revalidateThrows = false;

// ── The tag map (M9.3) ───────────────────────────────────────────────
// The criterion is that revalidating one product leaves unrelated pages
// cached, so each case asserts what is ABSENT as much as what is present.
const tagsOf = (body) => tagsFor(parseWebhookPayload(body));
const has = (body, tag) => tagsOf(body).includes(tag);

const productUpdate = {
  type: "UPDATE",
  table: "products",
  record: { slug: "gold-ring" },
  old_record: { slug: "gold-ring" },
};

ok("a product change invalidates the lists", has(productUpdate, "products"));
ok(
  "a product change invalidates its own page",
  has(productUpdate, "product:gold-ring"),
);
ok(
  "a product change does NOT invalidate another product's page",
  !has(productUpdate, "product:silver-ring"),
  tagsOf(productUpdate).join(","),
);
ok(
  "a product change does NOT invalidate the category list",
  !has(productUpdate, "categories"),
  tagsOf(productUpdate).join(","),
);

// The renamed-slug case: the page cached under the OLD slug must go too,
// or the site keeps serving a product page at a URL that no longer exists.
const renamed = {
  type: "UPDATE",
  table: "products",
  record: { slug: "gold-ring-new" },
  old_record: { slug: "gold-ring-old" },
};
ok("a renamed slug invalidates the new page", has(renamed, "product:gold-ring-new"));
ok("a renamed slug invalidates the OLD page", has(renamed, "product:gold-ring-old"));

const deleted = {
  type: "DELETE",
  table: "products",
  record: null,
  old_record: { slug: "gone-forever" },
};
ok("a delete invalidates the lists", has(deleted, "products"));
ok(
  "a delete invalidates the deleted product's page",
  has(deleted, "product:gone-forever"),
  tagsOf(deleted).join(","),
);

const inserted = {
  type: "INSERT",
  table: "products",
  record: { slug: "brand-new" },
  old_record: null,
};
ok("an insert invalidates the lists", has(inserted, "products"));
ok("an insert invalidates its own page", has(inserted, "product:brand-new"));

// A category visibility toggle changes which PRODUCTS are public, so the
// product lists must go too - not just the category chip.
const categoryToggle = {
  type: "UPDATE",
  table: "categories",
  record: { slug: "rings", is_visible: false },
  old_record: { slug: "rings", is_visible: true },
};
ok("a category change invalidates the category list", has(categoryToggle, "categories"));
ok("a category change invalidates its own listing", has(categoryToggle, "category:rings"));
ok(
  "a category change invalidates the product lists too",
  has(categoryToggle, "products"),
  tagsOf(categoryToggle).join(","),
);

// product_images carries product_id and no slug, so the product-level tag
// is the coarse one. Asserted explicitly so the limitation is visible if
// someone later expects a slug-scoped tag here.
const imageReorder = {
  type: "UPDATE",
  table: "product_images",
  record: { product_id: "0000-1111", display_order: 1 },
  old_record: { product_id: "0000-1111", display_order: 0 },
};
ok("an image reorder invalidates the lists", has(imageReorder, "products"));
ok(
  "an image reorder does NOT invalidate the category list",
  !has(imageReorder, "categories"),
  tagsOf(imageReorder).join(","),
);

// A row with no usable slug must still invalidate the lists rather than
// throwing - a malformed row should degrade, not crash the endpoint.
const slugless = {
  type: "UPDATE",
  table: "products",
  record: { name: "no slug here" },
  old_record: null,
};
ok("a slugless product row still invalidates the lists", has(slugless, "products"));
ok(
  "a slugless product row yields no product: tag",
  !tagsOf(slugless).some((t) => t.startsWith("product:")),
  tagsOf(slugless).join(","),
);

console.log(`
${pass} passed, ${fail} failed
`);
process.exit(fail === 0 ? 0 : 1);
