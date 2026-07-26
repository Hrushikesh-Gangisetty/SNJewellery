import { createClient, type SupabaseClient } from "@supabase/supabase-js";
import { publicEnv } from "@/lib/config/env";
import type { Database } from "./database.types";

/**
 * Read-only Supabase client for the website.
 *
 * **The website never writes.** All mutation originates in the Android
 * app (CLAUDE.md §3.1). This client uses the ANON key, whose only
 * permitted operation is SELECT on published rows — enforced by RLS, not
 * by convention, and verified by `npm run db:test-rls`.
 *
 * The service-role key must never appear in this package. It bypasses RLS
 * and would be readable by anyone who opens the bundle. M5.3 searches the
 * built output to confirm it is absent.
 *
 * ── Why the client is created lazily ─────────────────────────────────
 * Constructing it at module load reads the environment at import time,
 * which means merely *importing* anything from `@/lib/data` demands
 * Supabase credentials — even when the fixture source is the one in use.
 *
 * That broke the fixture test path and `NEXT_PUBLIC_USE_FIXTURES=1`
 * offline development, and it defeated the whole point of validating the
 * environment lazily (see lib/config/env.ts). Creating the client on
 * first query restores the property that fixture work needs no
 * credentials at all.
 *
 * `persistSession: false` because there is nothing to persist: the public
 * site has no login. Leaving it on would write to storage for no reason
 * and complicate server rendering.
 */
let client: SupabaseClient<Database> | null = null;

export function getSupabase(): SupabaseClient<Database> {
  client ??= createClient<Database>(
    publicEnv.supabaseUrl,
    publicEnv.supabaseAnonKey,
    {
      auth: {
        persistSession: false,
        autoRefreshToken: false,
      },
    },
  );
  return client;
}
