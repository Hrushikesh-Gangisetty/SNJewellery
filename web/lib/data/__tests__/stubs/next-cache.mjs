// Stands in for `next/cache` in revalidate-contract.mjs.
//
// The real `revalidateTag` needs a running Next.js request context and
// throws outside one, so the endpoint cannot be exercised as a plain
// function without this. Recording the calls is also what makes the
// idempotency assertion possible: "no double-work" is a statement about
// how many times revalidateTag ran, which is invisible from the response.

let onRevalidate = () => {};

/** Called by the test before importing the route. */
export function __configure(options) {
  onRevalidate = options.onRevalidate ?? (() => {});
}

export function revalidateTag(tag) {
  onRevalidate(tag);
}

export function revalidatePath(path) {
  onRevalidate(path);
}

/**
 * The route imports the TAGS map from lib/data/cache.ts, which wraps every
 * read in `unstable_cache` at module load. Passing the function straight
 * through is enough: this test never calls those reads, it only needs the
 * module to evaluate.
 */
export function unstable_cache(fn) {
  return fn;
}
