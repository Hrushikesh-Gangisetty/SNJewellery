// Resolve hook: redirects `next/cache` to the stub in this directory.
//
// The real module needs a Next.js request context and throws outside one,
// so the route handler cannot be called as a plain function without this.
import { pathToFileURL } from "node:url";
import { dirname, resolve as res } from "node:path";
import { fileURLToPath } from "node:url";

const HERE = dirname(fileURLToPath(import.meta.url));
const STUB = pathToFileURL(res(HERE, "next-cache.mjs")).href;

export async function resolve(spec, ctx, next) {
  if (spec === "next/cache") return { url: STUB, shortCircuit: true };
  return next(spec, ctx);
}
