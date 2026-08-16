# Deploying the website

Vercel project setup, the deploy and rollback procedure, and the domain.

Produced by **M5.2–M5.4** and **M5.8**. Environment variables are
[environments.md](environments.md).

---

## 1 · Project setup, once

This is a monorepo: the Next.js app is in `web/`, not at the root.

| Setting | Value | Why |
|---|---|---|
| Framework preset | Next.js | |
| **Root Directory** | `web` | The one setting that is wrong by default here. Leave it at the repository root and the build finds no `package.json` with a `next` dependency. |
| Build command | *(default)* | `next build`, inferred |
| Install command | *(default)* | |
| Node version | 22.x | Matches the toolchain the repository is developed against |
| Production branch | `main` | Commits go straight to `main` — see CLAUDE.md §10 |

Set the environment variables **before** the first deploy. A build with a
missing `NEXT_PUBLIC_SUPABASE_URL` fails at the first page that reads data,
which reads as a code fault rather than a configuration one.

---

## 2 · Deploying

**A push to `main` deploys to production.** There is no other step and no manual
promotion. A push to any other branch produces a preview.

```bash
git push origin main
```

Before pushing anything you intend to reach the public:

```bash
cd web && npm run verify && npm run build
```

That is CLAUDE.md §1.7, and it is the difference between finding a type error on
your machine and finding it in a deployment the shop is looking at.

---

## 3 · Rolling back

**Roll back first, diagnose second.** The catalogue being wrong for ten minutes
while someone reads a stack trace is a real cost.

### The fast path — Vercel's instant rollback

1. Vercel dashboard → the project → **Deployments**
2. Find the last deployment known good
3. **⋯ → Promote to Production**

This re-points the domain at a build that already exists. It takes seconds and
needs no rebuild. **It does not revert the repository** — `main` still contains
the bad commit, and the next push will deploy it again. So:

### Then, in the repository

```bash
git revert <bad-commit>
git push origin main
```

`revert` rather than `reset`: `main` is pushed, and rewriting it breaks every
other clone. Prefer a new commit — CLAUDE.md §10.

### What a rollback cannot undo

- **A migration.** The database does not roll back with the deployment. If the
  bad deploy shipped a migration, see [database.md](database.md) — and note the
  ordering rule: migrations reach production *before* the code that needs them,
  precisely so a rollback of the code lands on a database that still works.
- **Uploaded photographs.** Storage objects are not versioned.
- **A rotated key.** Rotating back is a second rotation.

---

## 4 · The domain

### Adding it

1. Vercel → the project → **Settings → Domains**
2. Add both `example.com` and `www.example.com`
3. Set `NEXT_PUBLIC_SITE_URL` to the canonical host, with no trailing slash

**Pick one and mean it.** Serving the same catalogue on two hosts splits its
search ranking between them and makes every canonical URL ambiguous.

**The redirect is in the repository, not in the dashboard.** `next.config.ts`
derives it from `NEXT_PUBLIC_SITE_URL` and redirects the `www` host to the
canonical one with a path-preserving 308. Setting Vercel's own domain-level
redirect as well is harmless but redundant, and it is a rule nobody can see from
the code — the version here is reviewable and testable in a local `next start`.
So `NEXT_PUBLIC_SITE_URL` is the single control: change it and the redirect,
the canonical tags and `robots.txt` all follow.

### The third host

The `*.vercel.app` deployment alias serves the same pages, and it is
deliberately **not** redirected — doing so would break Vercel's per-deployment
URLs, which are how a build is inspected before it is promoted. The canonical
tag in the root layout is what keeps it out of search results instead. Expect
`https://<project>.vercel.app` to return `200` with a canonical pointing at the
real domain; that is correct, not a misconfiguration.

### DNS

At the registrar, for the apex and the `www` subdomain:

| Record | Host | Points to |
|---|---|---|
| `A` | `@` | Vercel's apex IP, shown in the dashboard |
| `CNAME` | `www` | `cname.vercel-dns.com` |

Vercel's Domains screen shows the exact values for the project — use those
rather than any written here, because they change.

Propagation is usually minutes and occasionally hours. HTTPS is automatic once
DNS resolves: Vercel issues and renews the certificate, and HTTP redirects to
HTTPS without configuration. That is M5.4's acceptance criterion, and it is
worth checking from a phone on mobile data rather than only from the machine
that set it up — a stale local DNS cache will happily show you the old answer.

```bash
curl -sI http://example.com        | head -1   # expect 30x
curl -sI https://www.example.com   | head -1   # expect 30x, if www is not canonical
curl -sI https://example.com       | head -1   # expect 200
```

Note that the `www` redirect ships in the build, so it only appears once a
deployment carrying it has gone live — a freshly added domain will serve `200`
on both hosts until then.

---

## 5 · After the first production deploy

- [ ] `https://<domain>/robots.txt` says `Allow: /` — a `Disallow: /` here means
      `VERCEL_ENV` is not `production`, which means this is not the production
      deployment you think it is
- [ ] A preview deployment's `/robots.txt` says `Disallow: /`
- [ ] View source on the home page: `<meta name="robots" content="index, follow">`
- [ ] `https://<domain>/does-not-exist` renders the styled 404, not a stack trace
- [ ] `curl -o /dev/null -w '%{http_code}' https://<domain>/product/does-not-exist`
      returns **404**, not 200. The styled page rendering is not enough — a soft
      404 looks identical in a browser and keeps the URL in Google's index
- [ ] `https://www.<domain>/catalogue` redirects to `https://<domain>/catalogue`,
      preserving the path
- [ ] The rates panel — see the note in M5's plan entry if it is missing
- [ ] Run the smoke checklist (M5.7) and record the Lighthouse baseline (M5.8)
