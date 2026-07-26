import path from "node:path";
import type { NextConfig } from "next";

/**
 * The monorepo has two lockfiles — one at the root for repo tooling (the
 * pinned Supabase CLI, the token generator) and one here for the website.
 * Without an explicit root Next infers the repository root and warns, and
 * its file tracing follows the wrong tree — which matters for the Vercel
 * build in M5.2.
 */
const nextConfig: NextConfig = {
  outputFileTracingRoot: path.join(import.meta.dirname, ".."),

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
