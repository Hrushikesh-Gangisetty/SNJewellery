# Environments

Three environments, and which credentials belong in each. The rules here are
[CLAUDE.md §9](../../CLAUDE.md); this is where they become specific.

Produced by **M5.1–M5.3**.

---

## 1 · What each one is

| | Website | Database | Indexed by search |
|---|---|---|---|
| **Development** | `next dev` on your machine | Development Supabase project | No |
| **Preview** | A Vercel deployment per branch and per pull request | **Development** project | No |
| **Production** | The custom domain | Production Supabase project | Yes |

**A preview never points at production.** A preview is an unreviewed branch with
a write path that RLS permits for an admin — pointing it at the real database
means an experiment can mutate the shop's live catalogue. This is the single
most damaging misconfiguration available here, and it is one dropdown away at
all times.

**Only production is indexable.** Enforced in code rather than by remembering:
`isIndexable()` in [`web/lib/config/env.ts`](../../web/lib/config/env.ts) reads
`VERCEL_ENV`, and both `robots.txt` and the pages' `noindex` directive ask it.
See M5.5.

---

## 2 · Variables, and where each belongs

| Variable | Development | Preview | Production | Notes |
|---|---|---|---|---|
| `NEXT_PUBLIC_SUPABASE_URL` | dev project | dev project | **prod project** | |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | dev key | dev key | **prod key** | Public by design — RLS is the boundary ([ADR-0004](../adr/0004-authentication-and-roles.md)) |
| `NEXT_PUBLIC_SITE_URL` | `http://localhost:3000` | leave unset → Vercel's URL | the custom domain | No trailing slash |
| `REVALIDATION_SECRET` | any value | its own value | **its own value** | Never shared between environments (M9.2) |
| `VERCEL_ENV` | — | — | — | **Platform-injected. Never set it by hand.** |

`web/.env.example` is the template and carries placeholders only. `web/.env.local`
is gitignored and holds the development values.

### The service-role key

**It belongs in no environment of this website.** The site has no write path
(CLAUDE.md §3.1) and never needs it. If a feature appears to require it, the RLS
policy is wrong — fix the policy.

Verify after any build:

```bash
cd web && npm run build
grep -r "service_role" .next/static .next/server   # must return nothing
```

That is M5.3's acceptance criterion, and it is worth re-running whenever a
dependency that touches Supabase is upgraded.

---

## 3 · Rotating a key

1. Rotate it in the Supabase dashboard (Settings → API).
2. Update it in Vercel for **production** and **preview** separately — they are
   different values on the same screen and it is easy to change one.
3. Update your local `web/.env.local`.
4. **Redeploy.** `NEXT_PUBLIC_*` values are inlined into the bundle at build
   time, so changing one in the dashboard does nothing to the deployment already
   serving. This surprises people every time.

The Android app's credentials live in `android/local.properties`, not here —
see [android.md](android.md).

---

## 4 · Checklist before the first production deploy

- [ ] Production Supabase project exists, with every migration applied
- [ ] `npm run db:test-rls` passes against **production**
- [ ] Production admin account created (there is no public sign-up)
- [ ] Vercel production variables set to the **production** project
- [ ] Vercel preview variables set to the **development** project
- [ ] `grep -r "service_role" .next` returns nothing
- [ ] `NEXT_PUBLIC_SITE_URL` is the canonical domain, no trailing slash
