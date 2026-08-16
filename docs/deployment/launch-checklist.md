# Launch checklist and performance baseline

The smoke sweep that must pass before the site is shown to anyone, and the
Lighthouse numbers M11 and M12 will be measured against.

Produced by **M5.7** and **M5.8**. Deploy and rollback are
[website.md](website.md).

---

## 1 · How to run this

Everything below is reproducible from a terminal — deliberately, so that a
re-run after a change is a command rather than an afternoon.

```bash
# Status codes, redirects, headers
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' <url>

# Lighthouse, one route, mobile (the default form factor)
CHROME_PATH="<path to chrome>" npx lighthouse@12 <url> \
  --only-categories=performance,accessibility,best-practices,seo \
  --output=json --output-path=<file>.json \
  --chrome-flags="--headless=new --no-sandbox --disable-gpu"

# Desktop adds --preset=desktop
```

**Lighthouse's performance score moves a point or two between identical
runs.** The home page below is the median of three; the rest are single runs,
which is why only the home figure should be quoted as *the* baseline.

---

## 2 · Smoke checklist — 2026-08-16

Against `https://snjewellery.in`, serving commit `81f8b06`.

> **The header fix below (`d4dc8f6`) is committed but not deployed.** The push
> is blocked on a GitHub credential — Git Credential Manager has no cached
> token and cannot prompt in this environment. Everything recorded here is
> from the deployed `81f8b06` unless a row says otherwise.

### Routes

| Route | Status | Title |
|---|---|---|
| `/` | 200 | SN Jewellery & Silver Palace |
| `/catalogue` | 200 | All jewellery · SN Jewellery |
| `/about` | 200 | About · SN Jewellery |
| `/contact` | 200 | Contact · SN Jewellery |
| `/category/<slug>` × 11 | 200 | *(category name)* · SN Jewellery |
| `/robots.txt` | 200 | — |
| `/nope` | **404** | |
| `/product/<unknown>` | **404** | |
| `/category/<unknown>` | **404** | |

All eleven categories were checked individually, not sampled.

### Canonical host, HTTPS, redirects — M5.4

| Check | Result |
|---|---|
| `http://snjewellery.in/` → | `308 https://snjewellery.in/` |
| `http://www.snjewellery.in/` → | `308 https://www.snjewellery.in/` |
| `https://www.snjewellery.in/catalogue` → | `308 https://snjewellery.in/catalogue` |
| `http://www.snjewellery.in/catalogue` → final | `https://snjewellery.in/catalogue`, 2 hops, 200 |
| Path preserved through the `www` redirect | yes — `/`, `/catalogue`, `/about`, `/contact`, `/category/gold-rings` |
| HSTS | `max-age=63072000` (2 years) |
| Certificate, apex | `CN=snjewellery.in`, Let's Encrypt, expires 2026-11-14 |
| Certificate, `www` | `CN=www.snjewellery.in`, Let's Encrypt, expires 2026-11-14 |
| Canonical tag | present and per-route, e.g. `/catalogue` → `https://snjewellery.in/catalogue` |
| `*.vercel.app` alias | 200, canonical points at `snjewellery.in` — [deliberate](website.md#the-third-host) |

### Indexing — M5.5

| Check | Result |
|---|---|
| Production `robots.txt` | `Allow: /` with `Host: https://snjewellery.in` |
| Production robots meta | `index, follow` |
| Non-production build `robots.txt` | `Disallow: /` |
| Non-production robots meta | `noindex, nofollow, nocache` |

The non-production pair was checked against a local build with `VERCEL_ENV`
absent, which is the same code path a preview takes.

### Secrets — M5.3

| Check | Result |
|---|---|
| `service_role` in any served JS chunk | **0 occurrences** |
| Supabase project in the live bundle | `vknetcfjyercyollrzeb` — production, not development |

### Content and conversion

| Check | Result |
|---|---|
| `tel:` link | `tel:+919440248401` on `/`, `/about`, `/contact`, `/catalogue` |
| WhatsApp link | `wa.me/919440248401` on the same four |
| Placeholder sweep — *lorem*, *placeholder*, *coming soon*, *TODO*, fixture slugs, `localhost`, `example.com` | **clean** on every route checked |
| Empty catalogue state | "The catalogue is not online yet" with an *Ask what is in store* action |
| Empty new-arrivals state | "No pieces to show yet" with an explanation |
| `lang` / viewport | `lang="en"`, `width=device-width, initial-scale=1` |
| Static asset caching | `public,max-age=31536000,immutable` |
| Icons — favicon, icon, apple-icon, logo, monogram | all 200 |

### Mobile and desktop layout

Measured over the DevTools protocol at 412px, 640px and 1440px, reading real
computed styles and bounding boxes rather than a screenshot.

**This sweep found a defect.** On the deployed `81f8b06`, at 412px the header
rendered *both* the winged monogram and the full lockup, plus the WhatsApp
button meant to appear only at `sm` and above. Five items competing for the
width, so the flex row shrank them: the monogram to 34×36 from a square
source, the lockup to 67×44 instead of 71×44. The brand mark was squashed on
every page, on the device the shop's customers actually use.

`d4dc8f6` fixes it; see [lib/cn.ts](../../web/lib/cn.ts) for the class-conflict
rule that came out of it. The right-hand column is that commit, measured
against a local production build:

| Check | Deployed `81f8b06` | Local `d4dc8f6` |
|---|---|---|
| Wordmarks visible at 412px | **2 — monogram and lockup** | 1 — monogram |
| Wordmarks visible at 1440px | 1 — lockup | 1 — lockup |
| Monogram box at 412px | **34×36** (source is square) | 36×36 |
| Header lockup box | **67×44** (source ratio needs 71) | hidden below `lg`; 71×44 above |
| Footer lockup box | 71×44 | 71×44 |
| WhatsApp button below `sm` | **visible** | hidden |
| Header height below `lg` | — | 65px (`min-h-16`) |
| Mobile drawer when closed | `inert`, off-canvas | `inert`, off-canvas |

**Re-run this table against the live site once `d4dc8f6` deploys.**

### Console

Lighthouse's `errors-in-console` audit: **no errors** on any of the seven runs
below.

---

## 3 · Lighthouse baseline — 2026-08-16

Lighthouse 12.8.2, headless Chrome, default simulated throttling. Mobile is
the default form factor; desktop rows used `--preset=desktop`.

Against the live site on commit `81f8b06`.

| Route | Form factor | Perf | A11y | Best practices | SEO |
|---|---|---:|---:|---:|---:|
| `/` | mobile | **99** | **100** | **96** | **100** |
| `/catalogue` | mobile | 99 | 100 | 96 | 100 |
| `/category/gold-rings` | mobile | 99 | 100 | 96 | 100 |
| `/about` | mobile | 100 | 100 | 96 | 100 |
| `/contact` | mobile | 100 | 100 | 96 | 100 |
| `/` | desktop | 100 | 100 | 100 | 100 |
| `/catalogue` | desktop | 100 | 100 | 100 | 100 |

Home mobile is the median of three runs scoring 100, 99, 99.

**The mobile 96 is the squashed logo**, and nothing else — `image-aspect-ratio`
is the only failing audit in the category, at weight 1. Desktop scores 100
because the lockup is not being shrunk there.

On a local production build of `d4dc8f6`, `image-aspect-ratio` **passes** and
best practices is **100**, with accessibility still 100. That run's SEO score
is not comparable and is not quoted here: it was built without `VERCEL_ENV`,
so its pages correctly carry `noindex` and Lighthouse marks them uncrawlable.
**Re-measure against the live site once `d4dc8f6` deploys.**

### Core Web Vitals, mobile

| Route | FCP | LCP | TBT | CLS | Speed Index |
|---|---:|---:|---:|---:|---:|
| `/` | 0.82 s | 1.90 s | 10 ms | **0.000** | 0.82 s |
| `/catalogue` | 0.94 s | 2.06 s | 18 ms | **0.000** | 0.95 s |
| `/category/gold-rings` | 0.92 s | 2.00 s | 5 ms | **0.000** | 0.92 s |
| `/about` | 0.81 s | 1.93 s | 17 ms | **0.000** | 0.81 s |
| `/contact` | 0.81 s | 1.90 s | 11 ms | **0.000** | 0.81 s |

**CLS is 0.000 everywhere**, which is the fixed-aspect-ratio rule in CLAUDE.md
§8 doing exactly what it was for.

### Server timing, unthrottled

TTFB from a residential connection in India, for context on how much of the
above is the network and how much is Vercel:

| Route | TTFB | Total |
|---|---:|---:|
| `/` | 105 ms | 121 ms |
| `/catalogue` | 87 ms | 112 ms |
| `/category/gold-rings` | 154 ms | 167 ms |
| `/about` | 82 ms | 93 ms |

---

## 4 · What this baseline does not measure

**Read this before quoting the numbers above.**

1. **The catalogue was empty — 0 products.** Every page measured is the shell:
   no product photography, no grid of images, no gallery. CLAUDE.md §8 calls
   images "the performance story", and none of it is in these numbers. A
   99 here is not evidence that a page showing forty photographs will score 99.

   **Re-run this whole section after M5.6** and treat *that* as the baseline
   M11 and M12 improve against. Keep this one — the difference between the two
   is the honest cost of the catalogue's images, which is worth knowing.

2. **No product page exists**, so `/product/<slug>` was never smoke tested:
   not the gallery, not the related-products row, and not the WhatsApp message
   that is supposed to carry the piece's name and URL. M4.12's acceptance
   criteria remain open on that last one.

3. **No physical device.** Everything above is headless Chrome with device
   emulation. The plan requires a real phone for the conversion buttons —
   WhatsApp, the dialer, the maps app — and emulation cannot stand in for it.

4. **The rates panel did not render**, correctly: it is built to show nothing
   until both a gold and a silver rate are published, and production has
   neither yet. It appears once the owner enters them.

5. **The directions button did not render**, also correctly: `site.address.geo`
   and `mapsUrl` are both null, and a button pointing nowhere is worse than no
   button. Supplying either turns it on.

6. **`d4dc8f6` is measured locally, not live**, because the push is blocked on
   a credential. Every row marked as such needs re-running against
   `https://snjewellery.in` once it deploys.
