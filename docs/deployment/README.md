# Deployment

Environments, deploy and rollback procedures, and the operational checks that surround a release.

Produced by **M5.8** in [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md).

## Documents

| Document | Contents | Task |
|---|---|---|
| `environments.md` | Development, preview, and production — what differs, and which keys belong where | M5.1–M5.3 |
| `website.md` | Vercel deploy and rollback runbook, monorepo build configuration, domain and DNS | M5.2–M5.4, M5.8 |
| `database.md` | Applying migrations to production, and what to do when one needs reverting | M5.1 |
| `android.md` | Build, sign, and distribute the admin app | M6, pending Open Question 5 |
| `launch-checklist.md` | The smoke checklist, and the recorded baseline Lighthouse scores | M5.7, M5.8 |

## Environments

| Environment | Website | Database |
|---|---|---|
| Development | Local `next dev` | Local Supabase or the development project |
| Preview | Vercel preview per branch | Development project — **never** production |
| Production | Vercel production on the custom domain | Production Supabase project |

Preview deployments must be excluded from search indexing (M5.5). A preview pointed at the production database would let an unreviewed branch mutate real data — which is why previews use the development project.

## Non-negotiables

1. **The service-role key never reaches a client.** M5.3's acceptance criterion is a search of the built output; the key must not appear. The website uses the anonymous key and RLS. See [ADR-0004](../adr/0004-authentication-and-roles.md).
2. **No development key in production, and no production key in a local `.env`.** Both directions cause real damage.
3. **Secrets live in the platform's environment configuration**, never in the repository. `.env.example` carries placeholder names only.
4. **HTTPS only**, with HTTP redirecting, and one canonical host with the other redirecting (M5.4).
5. **Migrations reach production before the code that depends on them.** Deploying a build that queries a column the production database lacks breaks the live site.

## Baseline measurements

M5.8 records a mobile Lighthouse baseline — performance, accessibility, SEO — *before* M11 and M12 begin. This is what makes their targets verifiable rather than assertable, and M12.11 records the before/after against it.

Keep these committed to the repository. A performance number in a chat message is worthless three months later.

## Open question

**Open Question 5** — Play Store or direct APK distribution for the admin app. Play Store means a developer account, store listing, privacy policy, and review delay; direct APK means a documented install and update path the shop owner can follow. `android.md` cannot be finished either way until this is decided.
