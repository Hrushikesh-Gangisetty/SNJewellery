import { TAGS } from "./cache";

/**
 * Turning a database change into the set of cache tags it invalidates.
 *
 * Implements [ADR-0006](../../../docs/adr/0006-cache-revalidation-strategy.md)
 * and the M9.3 mutation-to-tag map. Kept separate from the route handler
 * so the mapping is testable without an HTTP server — the map is the part
 * that is easy to get wrong, and ADR-0006 warns that a mutation whose tag
 * is missing here is a silently stale page.
 *
 * ── Why the payload is parsed defensively ────────────────────────────
 * This arrives from Supabase over the network at a public URL. The secret
 * establishes that the *caller* is trusted, not that the *body* is well
 * formed — a schema change, a misconfigured webhook, or a partial
 * delivery all produce a shape that does not match. Nothing here uses a
 * non-null assertion or a cast to make the types agree.
 */

/** The Supabase webhook operations. `TRUNCATE` is not subscribed to. */
export type WebhookOperation = "INSERT" | "UPDATE" | "DELETE";

/** The tables a change to which can affect a cached page. */
export type WebhookTable = "products" | "categories" | "product_images";

/**
 * A Supabase database webhook body.
 *
 * `record` is the new row and is null on DELETE; `old_record` is the
 * previous row and is populated on UPDATE and DELETE. Both matter: a slug
 * that changed leaves a page cached under the OLD slug, so the old row's
 * tags have to be invalidated too.
 */
export type WebhookPayload = {
  readonly type: WebhookOperation;
  readonly table: WebhookTable;
  readonly record: Row | null;
  readonly old_record: Row | null;
};

/**
 * The columns tag derivation reads, all optional because the payload is
 * whatever the database sent. `product_images` rows carry `product_id`
 * but no slug, which is why the product-level tag cannot always be
 * derived — see `tagsFor`.
 */
type Row = {
  readonly slug?: unknown;
  readonly product_id?: unknown;
  readonly category_id?: unknown;
};

const OPERATIONS: readonly string[] = ["INSERT", "UPDATE", "DELETE"];
const TABLES: readonly string[] = ["products", "categories", "product_images"];

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** The row's slug, when it is present and actually a string. */
function slugOf(row: Row | null): string | null {
  if (row === null) return null;
  return typeof row.slug === "string" && row.slug !== "" ? row.slug : null;
}

/**
 * Parses an unknown body into a payload, or returns null.
 *
 * Returning null rather than throwing because the caller answers a
 * malformed body with 400 and a message — an exception would have to be
 * caught and converted back into exactly that.
 */
export function parseWebhookPayload(body: unknown): WebhookPayload | null {
  if (!isRecord(body)) return null;

  const { type, table, record, old_record: oldRecord } = body;

  if (typeof type !== "string" || !OPERATIONS.includes(type)) return null;
  if (typeof table !== "string" || !TABLES.includes(table)) return null;

  // Both rows are optional in principle but must be objects when present.
  if (record !== undefined && record !== null && !isRecord(record)) return null;
  if (oldRecord !== undefined && oldRecord !== null && !isRecord(oldRecord)) {
    return null;
  }

  return {
    type: type as WebhookOperation,
    table: table as WebhookTable,
    record: isRecord(record) ? (record as Row) : null,
    old_record: isRecord(oldRecord) ? (oldRecord as Row) : null,
  };
}

/**
 * The tags a change invalidates.
 *
 * ── The rules, and why each is narrower than it could be ─────────────
 *
 * **products** — every list can reorder or repopulate, so `products` is
 * unavoidable. Beyond that only the affected product's own slug, so
 * editing one piece leaves every other product page cached. That is
 * ADR-0006's stated requirement and M9.3's acceptance criterion.
 *
 * **categories** — the category list renders in the header and footer of
 * every page, so `categories` plus the specific category's listing. A
 * visibility toggle also changes which products are public, which is why
 * `products` is included: hiding a category must empty its pieces from
 * the home rows, not just grey out a chip.
 *
 * **product_images** — a reorder changes the card image, so the lists are
 * stale. The row carries `product_id`, not a slug, and this function does
 * not query the database — so the product's own page is covered by the
 * `products` tag rather than by `product:<slug>`. Coarser than the other
 * two paths, and deliberately: the alternative is a lookup on a path that
 * must stay fast and cannot fail.
 */
export function tagsFor(payload: WebhookPayload): readonly string[] {
  const tags = new Set<string>();

  switch (payload.table) {
    case "products": {
      tags.add(TAGS.products);

      // Both rows, because a renamed slug leaves the page cached under
      // the old one. On INSERT old_record is null; on DELETE record is.
      for (const row of [payload.record, payload.old_record]) {
        const slug = slugOf(row);
        if (slug !== null) tags.add(TAGS.product(slug));
      }
      break;
    }

    case "categories": {
      tags.add(TAGS.categories);
      tags.add(TAGS.products);

      for (const row of [payload.record, payload.old_record]) {
        const slug = slugOf(row);
        if (slug !== null) tags.add(TAGS.category(slug));
      }
      break;
    }

    case "product_images": {
      tags.add(TAGS.products);
      break;
    }
  }

  return [...tags];
}
