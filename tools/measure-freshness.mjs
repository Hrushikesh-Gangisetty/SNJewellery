// Measures how long a catalogue change takes to appear on the live site.
//
//   node tools/measure-freshness.mjs <url> --expect "text"
//   node tools/measure-freshness.mjs <url> --expect-gone "text"
//
// Produces the M9.6 numbers. Polls a URL from a cold client until the
// expected content appears, and prints the elapsed time.
//
// ── Why a script rather than a stopwatch ─────────────────────────────
// The measurement is "time from mutation to visible on production", and a
// person with a phone in one hand cannot also time a page to the second.
// More importantly the polling has to defeat every cache between here and
// Vercel, or it measures a stale response and reports a number that is
// too good. A stopwatch cannot do that; this can.
//
// Start it BEFORE tapping Save on the phone. It reports from its own
// start, so the clock includes the upload itself - which is what the
// PRD's "visible within one minute of upload" actually means.

const args = process.argv.slice(2);
const url = args[0];

const flag = (name) => {
  const i = args.indexOf(name);
  return i === -1 ? null : args[i + 1];
};

const expect = flag("--expect");
const expectGone = flag("--expect-gone");
const timeoutSeconds = Number(flag("--timeout") ?? 900);
const intervalMs = Number(flag("--interval") ?? 2000);

if (!url || (!expect && !expectGone)) {
  console.error(
    "usage: node tools/measure-freshness.mjs <url> --expect <text>\n" +
      "       node tools/measure-freshness.mjs <url> --expect-gone <text>\n\n" +
      "options: --timeout <seconds, default 900>  --interval <ms, default 2000>",
  );
  process.exit(2);
}

const needle = expect ?? expectGone;
const wantPresent = expect !== null;

console.log(`
Freshness measurement
  url:      ${url}
  waiting:  ${wantPresent ? "until PRESENT" : "until GONE"} -> ${JSON.stringify(needle)}
  timeout:  ${timeoutSeconds}s

Start your change on the phone NOW. Timing from this moment.
`);

const started = Date.now();
const elapsed = () => ((Date.now() - started) / 1000).toFixed(1);

let attempts = 0;
let sawCacheHit = 0;

while ((Date.now() - started) / 1000 < timeoutSeconds) {
  attempts++;

  // Cache-buster in the query string AND no-store, because either alone
  // can still be answered by an intermediary. A measurement that reads a
  // cached response is worse than no measurement - it under-reports.
  const probe = `${url}${url.includes("?") ? "&" : "?"}_cb=${Date.now()}`;

  let body = "";
  let cacheHeader = "";
  try {
    const res = await fetch(probe, {
      cache: "no-store",
      headers: { "cache-control": "no-cache", pragma: "no-cache" },
    });
    body = await res.text();
    cacheHeader = res.headers.get("x-vercel-cache") ?? "";
    if (cacheHeader === "HIT") sawCacheHit++;

    if (res.status === 404 && !wantPresent) {
      console.log(`  ${elapsed()}s  404 - the page is gone`);
      report(true);
    }
  } catch (e) {
    console.log(`  ${elapsed()}s  request failed: ${e.message}`);
    await sleep(intervalMs);
    continue;
  }

  const present = body.includes(needle);
  if (present === wantPresent) {
    console.log(`  ${elapsed()}s  matched (x-vercel-cache: ${cacheHeader})`);
    report(true);
  }

  if (attempts % 5 === 0) {
    console.log(
      `  ${elapsed()}s  still waiting (x-vercel-cache: ${cacheHeader})`,
    );
  }
  await sleep(intervalMs);
}

console.log(`\n  TIMED OUT after ${elapsed()}s`);
report(false);

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

function report(ok) {
  const seconds = Number(elapsed());
  // The budget line is only meaningful if the content actually appeared.
  // Printing "within 60s" under a timeout would be a tool reporting
  // success on a failed run - the one thing a measurement must never do.
  const budget = ok
    ? `${seconds <= 60 ? "within" : "OVER"} the PRD's 60s`
    : `n/a - never appeared within the ${timeoutSeconds}s timeout`;

  console.log(`
─────────────────────────────────────────────
  result:   ${ok ? "VISIBLE" : "NOT VISIBLE (timed out)"}
  elapsed:  ${seconds}s
  polls:    ${attempts}
  budget:   ${budget}
${sawCacheHit > 0 ? `  note:     ${sawCacheHit} response(s) came from the edge cache\n` : ""}─────────────────────────────────────────────

Record this in docs/deployment/launch-checklist.md §6.
`);
  process.exit(ok && seconds <= 60 ? 0 : 1);
}
