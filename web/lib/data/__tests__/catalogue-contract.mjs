// Verifies the CatalogueSource contract in ../source.ts against the
// fixture implementation.
//
// Run with:  npm run test:data
//
// The visibility rules are the ones most likely to break silently, so
// they are checked from EVERY entry point rather than once. When M4.1
// swaps in the Supabase implementation, this file should pass unchanged
// against it - that is how we know RLS reproduces the fixture behaviour.
const mod = await import("../index.ts");
const { productImageAlt } = mod;
const { fixtureProducts } = await import("../fixtures.ts");

// Which source to exercise. `fixture` needs nothing; `supabase` needs
// web/.env.local and a seeded database.
const WHICH = process.env.CONTRACT_SOURCE ?? "fixture";
const catalogue =
  WHICH === "supabase" ? mod.supabaseCatalogueSource : mod.fixtureCatalogueSource;
console.log(`
CatalogueSource contract — source: ${WHICH}
`);

let pass = 0, fail = 0;
const ok = (name, cond, detail = "") => {
  if (cond) { pass++; console.log(`  PASS  ${name}`); }
  else { fail++; console.log(`  FAIL  ${name}${detail ? "  -> " + detail : ""}`); }
};

// Identify products by SLUG, never by id.
//
// The fixture ids ("pr-13") do not exist in Supabase, where ids are
// UUIDs. An assertion like `!items.some(p => p.id === "pr-13")` is
// therefore trivially TRUE against Supabase regardless of what RLS does —
// it would pass even if the archived product were fully exposed. Slugs
// are identical in both sources, so they are the only sound identity here.
const ARCHIVED = "discontinued-pendant-design";
const HIDDEN = "unreleased-festival-collection-piece";
const SOLD = "diamond-cut-jhumka-earrings";
const NEWEST = "temple-design-bridal-necklace";

// ── Rule 1 & 2: archived and hidden-category never leak ──────────────
const cats = await catalogue.getVisibleCategories();
ok("hidden category absent from getVisibleCategories",
   !cats.some(c => c.slug === "unreleased-collection"));
ok("getCategoryBySlug returns null for hidden category",
   (await catalogue.getCategoryBySlug("unreleased-collection")) === null);
ok("getCategoryBySlug returns null for unknown slug",
   (await catalogue.getCategoryBySlug("no-such-thing")) === null);

const all = await catalogue.getAllProducts({ limit: 100 });
ok("archived absent from getAllProducts", !all.items.some(p => p.slug === ARCHIVED));
ok("hidden-category product absent from getAllProducts",
   !all.items.some(p => p.slug === HIDDEN));

const featured = await catalogue.getFeaturedProducts(50);
ok("featured product in a hidden category does NOT leak (the trap case)",
   !featured.some(p => p.slug === HIDDEN),
   featured.map(p => p.slug).join(","));

const newest = await catalogue.getNewestProducts(50);
ok("archived absent from getNewestProducts", !newest.some(p => p.slug === ARCHIVED));
ok("getProductBySlug returns null for archived",
   (await catalogue.getProductBySlug("discontinued-pendant-design")) === null);
ok("getProductBySlug returns null for hidden-category product",
   (await catalogue.getProductBySlug("unreleased-festival-collection-piece")) === null);
ok("getProductsByCategory on hidden category yields empty",
   (await catalogue.getProductsByCategory("unreleased-collection")).items.length === 0);
ok("getProductsByCategory on unknown slug yields empty (not everything)",
   (await catalogue.getProductsByCategory("nope")).items.length === 0);

// related products must also respect it
const necklace = await catalogue.getProductBySlug("gold-mangalsutra-black-beads");
const related = await catalogue.getRelatedProducts(necklace, 50);
ok("related products exclude archived sibling in same category",
   !related.some(p => p.slug === ARCHIVED), related.map(p=>p.slug).join(","));
ok("related products exclude the product itself",
   !related.some(p => p.id === necklace.id));

// ── Rule 3: sold products ARE returned ───────────────────────────────
const soldItem = await catalogue.getProductBySlug("diamond-cut-jhumka-earrings");
ok("sold product IS returned by getProductBySlug", soldItem !== null && soldItem.sold === true);
ok("sold product IS present in getAllProducts", all.items.some(p => p.slug === SOLD));
ok("sold product IS present in featured", featured.some(p => p.slug === SOLD));

// ── Rule 4: null, not throw ──────────────────────────────────────────
ok("unknown product slug returns null", (await catalogue.getProductBySlug("zzz")) === null);

// ── Rule 5: deterministic ordering + pagination ──────────────────────
const a = (await catalogue.getAllProducts({ limit: 100 })).items.map(p => p.id).join(",");
const b = (await catalogue.getAllProducts({ limit: 100 })).items.map(p => p.id).join(",");
ok("ordering is deterministic across calls", a === b);
ok("newest-first ordering", all.items[0].slug === NEWEST, all.items[0].slug);

const p1 = await catalogue.getAllProducts({ limit: 5 });
const p2 = await catalogue.getAllProducts({ limit: 5, cursor: p1.nextCursor });
const p3 = await catalogue.getAllProducts({ limit: 5, cursor: p2.nextCursor });
const paged = [...p1.items, ...p2.items, ...p3.items].map(p => p.id);
ok("pagination has no overlap", new Set(paged).size === paged.length);
ok("pagination covers everything", paged.length === all.items.length, `${paged.length} vs ${all.items.length}`);
ok("last page reports hasMore false and null cursor", p3.hasMore === false && p3.nextCursor === null);
ok("unknown cursor yields empty page, not a restart",
   (await catalogue.getAllProducts({ limit: 5, cursor: "bogus" })).items.length === 0);

// ── Awkward fixture cases exist (ADR-0009) ───────────────────────────
ok("a product with zero images exists", all.items.some(p => p.images.length === 0));
ok("a product with exactly one image exists", all.items.some(p => p.images.length === 1));
ok("a product with null weight exists", all.items.some(p => p.weightGrams === null));
ok("a product with null summary exists", all.items.some(p => p.summary === null));
ok("a portrait-aspect image exists",
   all.items.some(p => p.images.some(i => i.aspect === "product-portrait")));
ok("a long product name (>34 chars) exists",
   all.items.some(p => p.name.length > 34));
ok("an empty but visible category exists",
   (await catalogue.getProductsByCategory("kids-collection")).items.length === 0
   && cats.some(c => c.slug === "kids-collection"));

// image display order within a product
const multi = all.items.find(p => p.images.length > 2);
ok("images are in ascending displayOrder",
   multi.images.every((im, i) => im.displayOrder === i));

// ── Alt text ─────────────────────────────────────────────────────────
// Purity left alt text on 2026-07-27 along with the rest of the
// customer-facing purity display. A screen-reader user must not be told
// something a sighted user is not (accessibility.md §1).
const allAlt = all.items.flatMap(p => p.images.map((_, i) => productImageAlt(p, i)));

ok("alt text is name and category",
   productImageAlt(soldItem) === "Diamond Cut Jhumka Earrings — Earrings",
   productImageAlt(soldItem));
const ring = await catalogue.getProductBySlug("mens-signet-ring");
ok("alt text carries no purity for an 18K gold ring",
   productImageAlt(ring) === "Men's Signet Ring — Gold Rings",
   productImageAlt(ring));
const anklet = await catalogue.getProductBySlug("silver-anklet-pair");
ok("alt text carries no purity for silver jewellery",
   productImageAlt(anklet) === "Silver Anklet Pair — Silver Jewellery",
   productImageAlt(anklet));
ok("no alt text mentions a purity code",
   allAlt.every(alt => !/\b\d{2}K\b/.test(alt)),
   allAlt.find(alt => /\b\d{2}K\b/.test(alt)));

// This assertion used to hold literal 0x08 and 0x01 bytes where \b and \1
// were intended, so it matched nothing and passed vacuously for as long as
// it has existed. The positive control below it is what stops that
// recurring: if the pattern stops detecting a repeat, a test fails.
const repeatedWord = /\b(\w+)\s+\1\b/i;
ok("the repeated-word check actually detects a repeat",
   repeatedWord.test("Silver Silver Jewellery"));
ok("no alt text contains a consecutive repeated word",
   allAlt.every(alt => !repeatedWord.test(alt)),
   allAlt.find(alt => repeatedWord.test(alt)));

ok("alt text numbers additional views",
   productImageAlt(soldItem, 1).endsWith(", view 2"));
ok("no alt text is empty", allAlt.every(alt => alt.trim().length > 0));

// ── Purities ─────────────────────────────────────────────────────────
// Retained in the schema and on the interface, hidden from customers —
// see the note on getPurities in ../source.ts.
const pur = await catalogue.getPurities();
ok("purities are 22K, 18K, Silver in order",
   pur.map(p => p.code).join(",") === "22K,18K,Silver", pur.map(p=>p.code).join(","));

console.log(`\n${pass} passed, ${fail} failed  (fixture set: ${fixtureProducts.length} products, ${all.items.length} public)`);
process.exit(fail === 0 ? 0 : 1);
