/**
 * Validated environment configuration.
 *
 * This is the ONLY place `process.env` is read. Everything else
 * imports from here, so a missing or malformed variable produces one
 * clear error naming the variable, rather than surfacing later as an
 * `undefined` URL or a confusing failure inside a component.
 *
 * See CLAUDE.md §9 and web/.env.example.
 *
 * ── Why validation is lazy, not eager ──────────────────────────────
 * Validating at module load would be marginally faster to fail, but
 * it would also mean the app cannot start at all until a Supabase
 * project exists (M3.1). The website shell, component primitives, and
 * fixture-backed data layer (M2.6–M2.10, M2.5) need no database, and
 * blocking that work on credentials we do not have yet would be
 * self-inflicted.
 *
 * So each value validates on first access and memoises the result.
 * The failure is still immediate and still names the variable — it
 * just happens when something actually needs the value.
 *
 * Note on `NEXT_PUBLIC_*`: Next.js inlines these at build time by
 * literal text substitution, so each must be written out as
 * `process.env.NEXT_PUBLIC_FOO`. A dynamic lookup like
 * `process.env[name]` is NOT replaced and would be undefined in the
 * browser. That is why the raw value is passed in rather than read
 * from a variable name.
 */

type Validator = (raw: string) => string;

/** Requires an absolute http(s) URL. Strips any trailing slash. */
const asUrl: Validator = (raw) => {
  let parsed: URL;
  try {
    parsed = new URL(raw);
  } catch {
    throw new Error(`expected an absolute URL, received "${raw}"`);
  }
  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    throw new Error(
      `expected http:// or https://, received "${parsed.protocol}"`,
    );
  }
  // Normalised so callers can concatenate paths without a double slash.
  return raw.replace(/\/+$/, "");
};

/** Requires any non-empty value. */
const asNonEmpty: Validator = (raw) => raw;

/**
 * Validates once and caches. `name` is only used for the error
 * message; `raw` must be a literal `process.env.X` reference.
 */
function lazy(name: string, raw: string | undefined, validate: Validator) {
  let cached: string | undefined;

  return (): string => {
    if (cached !== undefined) return cached;

    if (raw === undefined || raw.trim() === "") {
      throw new Error(
        `Missing required environment variable ${name}. ` +
          `Copy web/.env.example to web/.env.local and set it.`,
      );
    }

    try {
      cached = validate(raw.trim());
    } catch (cause) {
      const detail = cause instanceof Error ? cause.message : String(cause);
      throw new Error(`Invalid environment variable ${name}: ${detail}`);
    }

    return cached;
  };
}

/**
 * Public configuration — safe to reference from client components.
 *
 * The Supabase anon key belongs here deliberately: row-level security
 * is what restricts it to reading published products, so exposing it
 * to the browser is by design, not an oversight.
 * See docs/adr/0004-authentication-and-roles.md
 */
const publicGetters = {
  supabaseUrl: lazy(
    "NEXT_PUBLIC_SUPABASE_URL",
    process.env.NEXT_PUBLIC_SUPABASE_URL,
    asUrl,
  ),
  supabaseAnonKey: lazy(
    "NEXT_PUBLIC_SUPABASE_ANON_KEY",
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY,
    asNonEmpty,
  ),
  siteUrl: lazy(
    "NEXT_PUBLIC_SITE_URL",
    process.env.NEXT_PUBLIC_SITE_URL,
    asUrl,
  ),
} as const;

export const publicEnv = {
  get supabaseUrl(): string {
    return publicGetters.supabaseUrl();
  },
  get supabaseAnonKey(): string {
    return publicGetters.supabaseAnonKey();
  },
  get siteUrl(): string {
    return publicGetters.siteUrl();
  },
};

/**
 * Server-only configuration.
 *
 * Never import this from a client component — that would attempt to
 * bundle a secret into browser JavaScript.
 *
 * `revalidationSecret` is consumed by the webhook endpoint in M9.2.
 */
const serverGetters = {
  revalidationSecret: lazy(
    "REVALIDATION_SECRET",
    process.env.REVALIDATION_SECRET,
    asNonEmpty,
  ),
};

export const serverEnv = {
  get revalidationSecret(): string {
    return serverGetters.revalidationSecret();
  },
};
