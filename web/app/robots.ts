import type { MetadataRoute } from "next";
import { isIndexable, publicEnv } from "@/lib/config/env";

/**
 * `robots.txt`, generated rather than written as a static file.
 *
 * ── Preview deployments are excluded ─────────────────────────────────
 * A static file would say the same thing on every deployment, and every
 * branch preview would invite crawling of an unreleased catalogue. This
 * asks [isIndexable] instead, so the answer follows the deployment.
 *
 * **This is only half of it.** `robots.txt` asks a crawler not to *fetch*
 * a page; it does not stop the URL being indexed if something links to
 * it, and a disallowed page can still appear in results as a bare link.
 * The `noindex` header in the root layout is the half that actually
 * removes it, and the two read the same function so they cannot drift.
 *
 * The sitemap is M11's, and is referenced only where it will exist.
 */
export default function robots(): MetadataRoute.Robots {
  if (!isIndexable()) {
    return {
      rules: { userAgent: "*", disallow: "/" },
    };
  }

  return {
    rules: {
      userAgent: "*",
      allow: "/",
    },
    host: publicEnv.siteUrl,
  };
}
