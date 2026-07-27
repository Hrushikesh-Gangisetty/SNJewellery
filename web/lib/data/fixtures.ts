import type {
  Category,
  ImageAspect,
  MetalRate,
  Product,
  ProductImage,
  Purity,
} from "./types";

/**
 * Fixture catalogue for development, tests, and empty-state work.
 *
 * ADR-0009 warns that tidy fixtures hide real layout bugs, so this data
 * deliberately includes the awkward cases:
 *
 *   - a very long product name (wraps to two lines on a 168px card)
 *   - products with no weight, no summary, no colours, no tags
 *   - a single-image product
 *   - a product with NO images at all
 *   - a sold product (must stay visible, with a badge)
 *   - an archived product (must never be returned to the website)
 *   - a hidden category (its products must never appear)
 *   - an empty visible category
 *   - portrait-aspect pieces alongside square ones
 *
 * Real photographs and the launch catalogue arrive in M5.6. Every image
 * here points at the local placeholder, so nothing depends on the network
 * and the aspect-ratio boxes are still exercised.
 */

const PLACEHOLDER = "/placeholder-product.svg";

/** Purity lookup — extensible by adding a row, per ADR-0010. */
export const fixturePurities: readonly Purity[] = [
  { id: "pu-22k", code: "22K", label: "22K Gold", displayOrder: 1 },
  { id: "pu-18k", code: "18K", label: "18K Gold", displayOrder: 2 },
  { id: "pu-slv", code: "Silver", label: "Silver", displayOrder: 3 },
];

/**
 * Today's rates. Both published, so the fixture exercises the panel's
 * rendered state; the unpublished state is what the live database sits in
 * until the owner sets a rate, and the panel is expected to hide there.
 */
export const fixtureMetalRates: readonly MetalRate[] = [
  { metal: "gold", ratePerGram: 7240, updatedAt: "2026-07-27T03:42:00Z" },
  { metal: "silver", ratePerGram: 92.5, updatedAt: "2026-07-27T03:42:00Z" },
];

const purity = (code: string): Purity => {
  const found = fixturePurities.find((p) => p.code === code);
  if (!found) throw new Error(`fixture error: unknown purity "${code}"`);
  return found;
};

export const fixtureCategories: readonly Category[] = [
  {
    id: "ca-necklaces",
    slug: "necklaces",
    name: "Necklaces",
    displayOrder: 1,
    isVisible: true,
  },
  {
    id: "ca-rings",
    slug: "gold-rings",
    name: "Gold Rings",
    displayOrder: 2,
    isVisible: true,
  },
  {
    id: "ca-earrings",
    slug: "earrings",
    name: "Earrings",
    displayOrder: 3,
    isVisible: true,
  },
  {
    id: "ca-bangles",
    slug: "bangles",
    name: "Bangles",
    displayOrder: 4,
    isVisible: true,
  },
  {
    id: "ca-silver",
    slug: "silver-jewellery",
    name: "Silver Jewellery",
    displayOrder: 5,
    isVisible: true,
  },
  {
    id: "ca-bridal",
    slug: "bridal-jewellery",
    name: "Bridal Jewellery",
    displayOrder: 6,
    isVisible: true,
  },
  // Visible but EMPTY — exercises the empty-category state.
  {
    id: "ca-kids",
    slug: "kids-collection",
    name: "Kids Collection",
    displayOrder: 7,
    isVisible: true,
  },
  // HIDDEN — this category and its products must never reach a customer.
  {
    id: "ca-hidden",
    slug: "unreleased-collection",
    name: "Unreleased Collection",
    displayOrder: 8,
    isVisible: false,
  },
];

const category = (slug: string): Category => {
  const found = fixtureCategories.find((c) => c.slug === slug);
  if (!found) throw new Error(`fixture error: unknown category "${slug}"`);
  return found;
};

let imageSeq = 0;
const images = (count: number, aspect: ImageAspect): ProductImage[] =>
  Array.from({ length: count }, (_, i) => {
    imageSeq += 1;
    return {
      id: `im-${imageSeq}`,
      url: PLACEHOLDER,
      storagePath: `products/fixture/${imageSeq}.svg`,
      displayOrder: i,
      aspect,
    };
  });

type Draft = Omit<Product, "createdAt" | "updatedAt"> & {
  /** Days before "now", so fixtures have a stable, ordered timeline. */
  daysAgo: number;
};

// Fixed epoch so fixture ordering is deterministic across runs.
const EPOCH = Date.UTC(2026, 6, 20);
const ts = (daysAgo: number) =>
  new Date(EPOCH - daysAgo * 86_400_000).toISOString();

const drafts: readonly Draft[] = [
  {
    id: "pr-01",
    slug: "temple-design-bridal-necklace",
    name: "Temple Design Bridal Necklace",
    summary: "Traditional temple work with intricate detailing.",
    description:
      "A traditional temple-design bridal necklace, handcrafted with detailed motifs drawn from South Indian temple architecture. Suited to bridal wear and worn with matching earrings.",
    category: category("bridal-jewellery"),
    purity: purity("22K"),
    weightGrams: 48.6,
    colours: ["Yellow"],
    tags: ["bridal", "temple", "traditional", "necklace"],
    featured: true,
    sold: false,
    archived: false,
    images: images(4, "product-portrait"),
    daysAgo: 1,
  },
  {
    // Deliberately long name — wraps to two lines on a 168px card.
    id: "pr-02",
    slug: "antique-finish-lakshmi-haram-long-chain",
    name: "Antique Finish Lakshmi Haram with Long Chain",
    summary: "Antique-finish haram with Lakshmi motif.",
    description:
      "An antique-finish haram featuring a central Lakshmi motif, strung on a long chain. Traditionally worn for weddings and festival occasions.",
    category: category("necklaces"),
    purity: purity("22K"),
    weightGrams: 62.15,
    colours: ["Yellow", "Antique"],
    tags: ["haram", "antique", "lakshmi", "long chain", "festival"],
    featured: true,
    sold: false,
    archived: false,
    images: images(3, "product-portrait"),
    daysAgo: 2,
  },
  {
    // SOLD — must remain visible with a badge.
    id: "pr-03",
    slug: "diamond-cut-jhumka-earrings",
    name: "Diamond Cut Jhumka Earrings",
    summary: "Classic jhumkas with a diamond-cut finish.",
    description:
      "Classic jhumka earrings with a diamond-cut finish that catches light from every angle.",
    category: category("earrings"),
    purity: purity("22K"),
    weightGrams: 12.4,
    colours: ["Yellow"],
    tags: ["jhumka", "earrings", "diamond cut"],
    featured: true,
    sold: true,
    archived: false,
    images: images(2, "product"),
    daysAgo: 4,
  },
  {
    // No weight recorded, no colours — optional fields absent.
    id: "pr-04",
    slug: "mens-signet-ring",
    name: "Men's Signet Ring",
    summary: "Plain signet ring with a brushed face.",
    description: "A plain signet ring with a brushed face and rounded shank.",
    category: category("gold-rings"),
    purity: purity("18K"),
    weightGrams: null,
    colours: [],
    tags: ["ring", "mens", "signet"],
    featured: false,
    sold: false,
    archived: false,
    images: images(2, "product"),
    daysAgo: 5,
  },
  {
    // SINGLE image.
    id: "pr-05",
    slug: "silver-anklet-pair",
    name: "Silver Anklet Pair",
    summary: "Hallmarked silver anklets with ghungroo detail.",
    description:
      "A pair of hallmarked silver anklets with fine ghungroo bells along the length.",
    category: category("silver-jewellery"),
    purity: purity("Silver"),
    weightGrams: 84.0,
    colours: [],
    tags: ["anklet", "silver", "payal"],
    featured: false,
    sold: false,
    archived: false,
    images: images(1, "product"),
    daysAgo: 7,
  },
  {
    // NO images at all — page must still render every spec.
    id: "pr-06",
    slug: "plain-gold-bangle-set",
    name: "Plain Gold Bangle Set",
    summary: null,
    description: null,
    category: category("bangles"),
    purity: purity("22K"),
    weightGrams: 31.75,
    colours: ["Yellow"],
    tags: ["bangle", "plain", "daily wear"],
    featured: false,
    sold: false,
    archived: false,
    images: [],
    daysAgo: 9,
  },
  {
    id: "pr-07",
    slug: "kundan-choker-set",
    name: "Kundan Choker Set",
    summary: "Kundan choker with matching earrings.",
    description:
      "A kundan choker set with matching earrings, finished with pearl drops along the lower edge.",
    category: category("bridal-jewellery"),
    purity: purity("22K"),
    weightGrams: 55.3,
    colours: ["Yellow", "White"],
    tags: ["kundan", "choker", "bridal", "set"],
    featured: false,
    sold: false,
    archived: false,
    images: images(3, "product-portrait"),
    daysAgo: 11,
  },
  {
    id: "pr-08",
    slug: "rose-gold-stud-earrings",
    name: "Rose Gold Stud Earrings",
    summary: "Small everyday studs in rose gold.",
    description: "Small everyday studs in an 18K rose gold finish.",
    category: category("earrings"),
    purity: purity("18K"),
    weightGrams: 3.2,
    colours: ["Rose"],
    tags: ["stud", "earrings", "rose gold", "daily wear"],
    featured: false,
    sold: false,
    archived: false,
    images: images(2, "product"),
    daysAgo: 13,
  },
  {
    id: "pr-09",
    slug: "silver-pooja-thali-set",
    name: "Silver Pooja Thali Set",
    summary: "Hallmarked silver thali with diya and kumkum holder.",
    description:
      "A hallmarked silver pooja thali set including a diya and a kumkum holder.",
    category: category("silver-jewellery"),
    purity: purity("Silver"),
    weightGrams: 320.5,
    colours: [],
    tags: ["silver", "pooja", "thali", "gift"],
    featured: false,
    sold: false,
    archived: false,
    images: images(2, "product"),
    daysAgo: 16,
  },
  {
    // SOLD, and not featured.
    id: "pr-10",
    slug: "gold-mangalsutra-black-beads",
    name: "Gold Mangalsutra with Black Beads",
    summary: "Traditional mangalsutra with a gold pendant.",
    description:
      "A traditional mangalsutra strung with black beads and finished with a gold pendant.",
    category: category("necklaces"),
    purity: purity("22K"),
    weightGrams: 22.8,
    colours: ["Yellow", "Black"],
    tags: ["mangalsutra", "traditional", "necklace"],
    featured: false,
    sold: true,
    archived: false,
    images: images(2, "product-portrait"),
    daysAgo: 19,
  },
  {
    id: "pr-11",
    slug: "cocktail-ring-cz-stone",
    name: "Cocktail Ring with CZ Stone",
    summary: "Statement cocktail ring with a central CZ stone.",
    description:
      "A statement cocktail ring set with a central cubic zirconia stone and a textured band.",
    category: category("gold-rings"),
    purity: purity("18K"),
    weightGrams: 6.9,
    colours: ["Yellow", "White"],
    tags: ["ring", "cocktail", "cz", "statement"],
    featured: false,
    sold: false,
    archived: false,
    images: images(2, "product"),
    daysAgo: 22,
  },
  {
    id: "pr-12",
    slug: "broad-kada-bangle",
    name: "Broad Kada Bangle",
    summary: "Broad kada with a hand-engraved surface.",
    description: "A broad kada bangle with a hand-engraved surface pattern.",
    category: category("bangles"),
    purity: purity("22K"),
    weightGrams: 44.2,
    colours: ["Yellow"],
    tags: ["kada", "bangle", "engraved"],
    featured: false,
    sold: false,
    archived: false,
    images: images(3, "product"),
    daysAgo: 26,
  },
  {
    // ARCHIVED — must never be returned to the website.
    id: "pr-13",
    slug: "discontinued-pendant-design",
    name: "Discontinued Pendant Design",
    summary: "Withdrawn from the catalogue.",
    description: "This piece has been archived and should never be public.",
    category: category("necklaces"),
    purity: purity("22K"),
    weightGrams: 8.1,
    colours: [],
    tags: ["pendant"],
    featured: false,
    sold: false,
    archived: true,
    images: images(1, "product"),
    daysAgo: 30,
  },
  {
    // In a HIDDEN category — must never be returned to the website.
    id: "pr-14",
    slug: "unreleased-festival-collection-piece",
    name: "Unreleased Festival Collection Piece",
    summary: "Not yet launched.",
    description: "In a hidden category and should never be public.",
    category: category("unreleased-collection"),
    purity: purity("22K"),
    weightGrams: 18.0,
    colours: [],
    tags: ["unreleased"],
    // Featured AND in a hidden category — the combination most likely to
    // leak through a home-page query that forgets to check visibility.
    featured: true,
    sold: false,
    archived: false,
    images: images(2, "product"),
    daysAgo: 33,
  },
];

/**
 * The complete fixture set, INCLUDING archived products and products in
 * hidden categories. The source implementation is responsible for
 * excluding those — exactly as RLS will in M4.1.
 */
export const fixtureProducts: readonly Product[] = drafts.map(
  ({ daysAgo, ...product }) => ({
    ...product,
    createdAt: ts(daysAgo),
    updatedAt: ts(daysAgo),
  }),
);
