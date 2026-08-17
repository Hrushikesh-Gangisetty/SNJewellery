import { timingSafeEqual } from "node:crypto";
import { revalidateTag } from "next/cache";
import { serverEnv } from "@/lib/config/env";
import { REVALIDATE_SECONDS } from "@/lib/data/cache";
import { parseWebhookPayload, tagsFor } from "@/lib/data/revalidate";

/**
 * The revalidation endpoint. Implements M9.2 and
 * [ADR-0006](../../../../docs/adr/0006-cache-revalidation-strategy.md).
 *
 * A Supabase database webhook calls this when a row changes, and it
 * invalidates the cache tags that row affects. This is the only write-ish
 * path the website has, and it writes nothing to the database — CLAUDE.md
 * §3.1 still holds.
 *
 * ── Why this is idempotent for free ──────────────────────────────────
 * Webhooks retry, so a duplicate delivery is expected, not exceptional.
 * `revalidateTag` marks a tag stale; marking an already-stale tag stale
 * again does nothing and costs nothing. There is no counter to
 * double-increment and no row to double-insert, so the handler needs no
 * deduplication of its own.
 *
 * ── Why failures are logged with the tags ────────────────────────────
 * A dropped revalidation is invisible by construction: the site simply
 * stays stale until the ISR interval catches it. The log line is what
 * makes M9.7's diagnosis procedure possible, and it names the tags so a
 * stale page can be traced back to the delivery that should have cleared
 * it. Nothing from the payload's rows is logged — a product row is not
 * sensitive, but this is the wrong place to start making that judgement.
 */

/**
 * Node, not Edge: `timingSafeEqual` is a Node built-in and this route
 * does nothing that benefits from edge placement — it is called by a
 * database a handful of times a day, not by customers.
 */
export const runtime = "nodejs";

/**
 * Never prerendered or cached. A cached revalidation endpoint would
 * answer the second delivery from cache without revalidating anything,
 * which fails silently in exactly the way this endpoint exists to prevent.
 */
export const dynamic = "force-dynamic";

/** The header the webhook presents the shared secret in. */
const SECRET_HEADER = "x-revalidation-secret";

/**
 * How long a dropped webhook leaves the site stale, for the failure log.
 * Derived from the ISR interval rather than written out, so the number in
 * the log cannot drift from the number that governs the behaviour.
 */
const FALLBACK_MINUTES = Math.round(REVALIDATE_SECONDS / 60);

/**
 * Constant-time comparison, so the response time does not reveal how much
 * of the secret was correct. `timingSafeEqual` throws on a length
 * mismatch, hence the explicit length check first — which does leak the
 * secret's length, and that is not worth protecting.
 */
function secretMatches(presented: string, expected: string): boolean {
  const a = Buffer.from(presented, "utf8");
  const b = Buffer.from(expected, "utf8");
  return a.length === b.length && timingSafeEqual(a, b);
}

export async function POST(request: Request): Promise<Response> {
  let expected: string;
  try {
    expected = serverEnv.revalidationSecret;
  } catch (cause) {
    // The variable is missing or blank. That is a deployment fault, not a
    // bad request — answering 401 would send the webhook into a retry
    // loop against an endpoint that cannot ever succeed.
    console.error("[revalidate] REVALIDATION_SECRET is not configured", cause);
    return Response.json({ error: "not configured" }, { status: 500 });
  }

  const presented = request.headers.get(SECRET_HEADER);
  if (presented === null || !secretMatches(presented, expected)) {
    // Deliberately terse. A caller without the secret learns only that it
    // was rejected, not whether the header was absent or merely wrong.
    return Response.json({ error: "unauthorised" }, { status: 401 });
  }

  let body: unknown;
  try {
    body = await request.json();
  } catch {
    return Response.json({ error: "body is not valid JSON" }, { status: 400 });
  }

  const payload = parseWebhookPayload(body);
  if (payload === null) {
    console.error("[revalidate] unrecognised payload shape");
    return Response.json({ error: "unrecognised payload" }, { status: 400 });
  }

  const tags = tagsFor(payload);

  try {
    for (const tag of tags) revalidateTag(tag);
  } catch (cause) {
    console.error(
      `[revalidate] FAILED ${payload.type} on ${payload.table}; tags: ${tags.join(", ")}; ` +
        `the site stays stale until the ${FALLBACK_MINUTES}-minute ISR interval expires`,
      cause,
    );
    // 500 so the webhook retries. The handler is idempotent, so a retry
    // that partially succeeded the first time is harmless.
    return Response.json({ error: "revalidation failed" }, { status: 500 });
  }

  // Logged on success too, not only on failure. A stale page is diagnosed
  // by asking "did the webhook arrive, and what did it clear?" — and the
  // absence of a line answers the first half. One line per mutation on a
  // catalogue edited a few times a day is not log noise.
  console.info(
    `[revalidate] ${payload.type} on ${payload.table}; cleared: ${tags.join(", ")}`,
  );

  return Response.json({ revalidated: tags });
}
