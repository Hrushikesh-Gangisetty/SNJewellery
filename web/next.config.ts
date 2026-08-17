import path from "node:path";
import type { NextConfig } from "next";

/**
 * The monorepo has two lockfiles — one at the root for repo tooling (the
 * pinned Supabase CLI, the token generator) and one here for the website.
 * Without an explicit root Next infers the repository root and warns, and
 * its file tracing follows the wrong tree — which matters for the Vercel
 * build in M5.2.
 */
/**
 * One host serves the site; every other host that reaches it redirects.
 *
 * Vercel can do this in its dashboard, and that is one checkbox nobody
 * can see from the repository. Declaring it here means the rule is
 * reviewable, survives the project being reconfigured or recreated, and
 * is testable in a local `next start` — and it costs nothing at runtime,
 * because a `has: host` redirect compiles into the routing manifest and
 * is answered at the edge rather than by a function.
 *
 * The canonical host is whatever `NEXT_PUBLIC_SITE_URL` names, so this
 * never hard-codes a domain and never disagrees with the canonical tag
 * or with `robots.txt`, which read the same variable.
 *
 * This does NOT cover the `*.vercel.app` deployment alias, which serves
 * the same content on a third host. Redirecting that would break
 * Vercel's own per-deployment URLs, so the canonical tag in the root
 * layout is what points search engines away from it.
 */
async function canonicalHostRedirects() {
  const siteUrl = process.env.NEXT_PUBLIC_SITE_URL;
  if (!siteUrl) return [];

  const canonical = new URL(siteUrl);
  // If the canonical host is already the `www` one there is no other
  // host to fold in, and the rule below would redirect it to itself.
  if (canonical.host.startsWith("www.")) return [];

  return [
    {
      source: "/:path*",
      has: [{ type: "host" as const, value: `www.${canonical.host}` }],
      destination: `${canonical.origin}/:path*`,
      permanent: true,
    },
  ];
}

/**
 * Cache headers (M9.4).
 *
 * ── What is deliberately NOT here ────────────────────────────────────
 * The catalogue pages set no `Cache-Control`. That is not an omission.
 * Each carries `export const revalidate = 600`, and Vercel derives the
 * CDN's `s-maxage` from that same value — so the edge cache and the ISR
 * cache expire together and `revalidateTag` purges both. Writing an
 * explicit header over the top is how the two drift apart: the tag would
 * clear Next's cache while the edge kept serving the old HTML until its
 * own, separately-specified age ran out. That is precisely the "stale
 * edge cache defeats revalidation" failure M9.4 exists to prevent, and
 * the way to avoid it is to let one value drive both.
 *
 * ── What is here, and why it needs saying ────────────────────────────
 * The revalidation endpoint. `dynamic = "force-dynamic"` stops Next
 * caching it, but that governs Next, not whatever sits in front of it —
 * a proxy or a corporate intermediary is entitled to cache a 200 to a
 * POST it was not told about. A cached revalidation response would be
 * answered without the handler ever running, which fails silently and
 * leaves the site stale: the exact bug this milestone is about. The
 * header costs nothing on a route called a few times a day.
 */
async function cacheHeaders() {
  return [
    {
      source: "/api/revalidate",
      headers: [
        {
          key: "Cache-Control",
          value: "no-store, no-cache, must-revalidate",
        },
      ],
    },
  ];
}

const nextConfig: NextConfig = {
  outputFileTracingRoot: path.join(import.meta.dirname, ".."),

  redirects: canonicalHostRedirects,

  headers: cacheHeaders,

  images: {
    /**
     * Product photographs are served from Supabase Storage, whose public
     * URLs live on the project subdomain. next/image refuses remote hosts
     * that are not listed here, so this must be in place before real
     * photographs render — currently the seed points at a local SVG, so
     * nothing exercises it yet.
     *
     * Read from the environment rather than hard-coded, so development and
     * production each point at their own project without a code change.
     */
    remotePatterns: process.env.NEXT_PUBLIC_SUPABASE_URL
      ? [
          {
            protocol: "https",
            hostname: new URL(process.env.NEXT_PUBLIC_SUPABASE_URL).hostname,
            pathname: "/storage/v1/object/public/**",
          },
        ]
      : [],
  },
};

export default nextConfig;
