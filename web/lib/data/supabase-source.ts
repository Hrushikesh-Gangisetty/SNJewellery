import { getSupabase } from "./supabase-client";
import { DEFAULT_PAGE_SIZE, type CatalogueSource } from "./source";
import type { Category, Page, PageRequest, Product, Purity } from "./types";

/**
 * Supabase implementation of CatalogueSource.
 *
 * ── What is deliberately NOT here ────────────────────────────────────
 * No `.eq("archived", false)`. No visibility filtering. No exclusion of
 * hidden categories.
 *
 * Those rules are enforced by row-level security, so an archived product
 * is not merely filtered out — it is invisible to this key. Repeating the
 * filters here would give the false impression that removing them is
 * survivable, and would let the two implementations drift.
 *
 * The fixture implementation applies the same rules by hand precisely
 * because it has no RLS to lean on. Both must behave identically, which
 * is what `npm run test:data` and `npm run db:test-rls` together verify.
 *
 * ── Ordering ─────────────────────────────────────────────────────────
 * Every list orders by (created_at desc, id desc). The id tiebreak is not
 * decoration: without it, two products created in the same transaction
 * have an undefined relative order, and keyset pagination silently skips
 * or repeats rows.
 */

/**
 * Columns selected for a product, with its category, purity and images
 * joined. Declared once so every query returns the same shape and the
 * mapper has a single input to handle.
 */
const PRODUCT_SELECT = `
  id, slug, name, summary, description,
  weight_grams, colours, tags,
  featured, sold, archived,
  created_at, updated_at,
  category:categories!inner (
    id, slug, name, display_order, is_visible
  ),
  purity:purities (
    id, code, label, display_order
  ),
  images:product_images (
    id, url, storage_path, display_order, aspect
  )
`;

/** The shape PRODUCT_SELECT returns, before mapping to the domain model. */
type ProductRow = {
  id: string;
  slug: string;
  name: string;
  summary: string | null;
  description: string | null;
  weight_grams: number | null;
  colours: string[];
  tags: string[];
  featured: boolean;
  sold: boolean;
  archived: boolean;
  created_at: string;
  updated_at: string;
  category: {
    id: string;
    slug: string;
    name: string;
    display_order: number;
    is_visible: boolean;
  };
  purity: {
    id: string;
    code: string;
    label: string;
    display_order: number;
  } | null;
  images: {
    id: string;
    url: string;
    storage_path: string;
    display_order: number;
    aspect: "product" | "product-portrait";
  }[];
};

/**
 * Maps a database row to the domain model: snake_case to camelCase, and
 * nested rows to nested objects. This is the only place that mapping
 * happens — components never see a database shape.
 */
function toProduct(row: ProductRow): Product {
  return {
    id: row.id,
    slug: row.slug,
    name: row.name,
    summary: row.summary,
    description: row.description,
    category: {
      id: row.category.id,
      slug: row.category.slug,
      name: row.category.name,
      displayOrder: row.category.display_order,
      isVisible: row.category.is_visible,
    },
    purity: row.purity
      ? {
          id: row.purity.id,
          code: row.purity.code,
          label: row.purity.label,
          displayOrder: row.purity.display_order,
        }
      : null,
    // numeric(10,2) arrives as a string from some drivers; coerce so
    // callers can rely on toFixed().
    weightGrams: row.weight_grams === null ? null : Number(row.weight_grams),
    colours: row.colours,
    tags: row.tags,
    featured: row.featured,
    sold: row.sold,
    archived: row.archived,
    // Postgres does not guarantee order in a joined array, so sort here
    // rather than trusting it. Gallery order is user-visible.
    images: [...row.images]
      .sort((a, b) => a.display_order - b.display_order)
      .map((image) => ({
        id: image.id,
        url: image.url,
        storagePath: image.storage_path,
        displayOrder: image.display_order,
        aspect: image.aspect,
      })),
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function toCategory(row: {
  id: string;
  slug: string;
  name: string;
  display_order: number;
  is_visible: boolean;
}): Category {
  return {
    id: row.id,
    slug: row.slug,
    name: row.name,
    displayOrder: row.display_order,
    isVisible: row.is_visible,
  };
}

/**
 * Throws on error rather than returning empty.
 *
 * An empty catalogue and a failed query look identical to a component,
 * and silently rendering "no products" when the database is unreachable
 * would hide a real outage behind an empty state. Next.js turns the throw
 * into the error boundary, which is the honest outcome.
 */
function unwrap<T>(result: {
  data: T | null;
  error: { message: string } | null;
}): T {
  if (result.error) {
    throw new Error(`Supabase query failed: ${result.error.message}`);
  }
  if (result.data === null) {
    throw new Error("Supabase returned no data and no error");
  }
  return result.data;
}

/**
 * Keyset pagination.
 *
 * The cursor is the last row's `created_at` and `id`, encoded together,
 * mirroring the composite sort. Offset pagination would degrade on deep
 * pages and can skip rows when the underlying set changes between
 * requests.
 */
function encodeCursor(product: Product): string {
  return Buffer.from(`${product.createdAt}|${product.id}`).toString(
    "base64url",
  );
}

/**
 * Both halves are interpolated into a PostgREST `or` filter below, and
 * since M4.3 the cursor arrives from the browser via a server action
 * rather than only from `encodeCursor`. So they are validated, not
 * trusted: a decoded value that is not a timestamp and a UUID is treated
 * as an unrecognisable cursor.
 *
 * RLS still bounds what any filter can reach, so this is not the security
 * boundary — it stops crafted input reaching the query builder at all.
 */
const TIMESTAMP_PATTERN = /^\d{4}-\d{2}-\d{2}T[\d:.]+(?:Z|[+-][\d:]+)?$/;
const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function decodeCursor(
  cursor: string,
): { createdAt: string; id: string } | null {
  try {
    const [createdAt, id] = Buffer.from(cursor, "base64url")
      .toString("utf8")
      .split("|");
    if (!createdAt || !id) return null;
    if (!TIMESTAMP_PATTERN.test(createdAt) || !UUID_PATTERN.test(id)) {
      return null;
    }
    return { createdAt, id };
  } catch {
    return null;
  }
}

async function pagedProducts(
  page: PageRequest | undefined,
  applyFilters: (
    query: ReturnType<typeof buildProductQuery>,
  ) => ReturnType<typeof buildProductQuery>,
): Promise<Page<Product>> {
  const limit = page?.limit ?? DEFAULT_PAGE_SIZE;
  const cursor = page?.cursor ? decodeCursor(page.cursor) : null;

  // An unrecognisable cursor yields an empty page rather than silently
  // restarting from the top, which would loop forever in an infinite
  // scroll. Matches the fixture implementation.
  if (page?.cursor && !cursor) {
    return { items: [], nextCursor: null, hasMore: false };
  }

  let query = applyFilters(buildProductQuery());

  if (cursor) {
    // Composite keyset: strictly earlier, or same instant and lower id.
    query = query.or(
      `created_at.lt.${cursor.createdAt},and(created_at.eq.${cursor.createdAt},id.lt.${cursor.id})`,
    );
  }

  // Fetch one extra row to learn whether another page exists, without a
  // second count query.
  const rows = unwrap(await query.limit(limit + 1));
  const products = (rows as unknown as ProductRow[]).map(toProduct);

  const hasMore = products.length > limit;
  const items = hasMore ? products.slice(0, limit) : products;
  const last = items.at(-1);

  return {
    items,
    nextCursor: hasMore && last ? encodeCursor(last) : null,
    hasMore,
  };
}

function buildProductQuery() {
  return getSupabase()
    .from("products")
    .select(PRODUCT_SELECT)
    .order("created_at", { ascending: false })
    .order("id", { ascending: false });
}

export const supabaseCatalogueSource: CatalogueSource = {
  async getVisibleCategories(): Promise<readonly Category[]> {
    const rows = unwrap(
      await getSupabase()
        .from("categories")
        .select("id, slug, name, display_order, is_visible")
        .order("display_order", { ascending: true }),
    );
    return rows.map(toCategory);
  },

  async getCategoryBySlug(slug: string): Promise<Category | null> {
    const { data, error } = await getSupabase()
      .from("categories")
      .select("id, slug, name, display_order, is_visible")
      .eq("slug", slug)
      .maybeSingle();

    if (error) {
      throw new Error(`Supabase query failed: ${error.message}`);
    }
    // null covers unknown slug AND hidden category — RLS makes them
    // indistinguishable, which is the correct behaviour.
    return data ? toCategory(data) : null;
  },

  async getPurities(): Promise<readonly Purity[]> {
    const rows = unwrap(
      await getSupabase()
        .from("purities")
        .select("id, code, label, display_order")
        .order("display_order", { ascending: true }),
    );
    return rows.map((row) => ({
      id: row.id,
      code: row.code,
      label: row.label,
      displayOrder: row.display_order,
    }));
  },

  async getFeaturedProducts(limit = 8): Promise<readonly Product[]> {
    const rows = unwrap(
      await buildProductQuery().eq("featured", true).limit(limit),
    );
    return (rows as unknown as ProductRow[]).map(toProduct);
  },

  async getNewestProducts(limit = 8): Promise<readonly Product[]> {
    const rows = unwrap(await buildProductQuery().limit(limit));
    return (rows as unknown as ProductRow[]).map(toProduct);
  },

  async getProductsByCategory(
    categorySlug: string,
    page?: PageRequest,
  ): Promise<Page<Product>> {
    return pagedProducts(page, (query) =>
      // Filters the joined category by slug. The !inner join in
      // PRODUCT_SELECT is what makes this a filter rather than a
      // left-join returning every product.
      query.eq("categories.slug", categorySlug),
    );
  },

  async getAllProducts(page?: PageRequest): Promise<Page<Product>> {
    return pagedProducts(page, (query) => query);
  },

  async getProductBySlug(slug: string): Promise<Product | null> {
    const { data, error } = await getSupabase()
      .from("products")
      .select(PRODUCT_SELECT)
      .eq("slug", slug)
      .maybeSingle();

    if (error) {
      throw new Error(`Supabase query failed: ${error.message}`);
    }
    return data ? toProduct(data as unknown as ProductRow) : null;
  },

  async getRelatedProducts(
    product: Product,
    limit = 4,
  ): Promise<readonly Product[]> {
    const rows = unwrap(
      await buildProductQuery()
        .eq("category_id", product.category.id)
        .neq("id", product.id)
        .limit(limit),
    );
    return (rows as unknown as ProductRow[]).map(toProduct);
  },
};
