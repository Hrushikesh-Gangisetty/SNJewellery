// Resolve hook: lets Node load TypeScript's extensionless relative
// imports (and the @/ alias) so lib/ modules can be exercised directly
// with --experimental-strip-types, without adding a test-runner
// dependency. Used by the contract tests under web/lib/**/__tests__.
import { existsSync } from "node:fs";
import { fileURLToPath, pathToFileURL } from "node:url";
import { dirname, resolve as res } from "node:path";

import { fileURLToPath as f2u } from "node:url";
const WEB = res(dirname(f2u(import.meta.url)), "..", "web");

export async function resolve(spec, ctx, next) {
  let s = spec;
  if (s.startsWith("@/")) s = pathToFileURL(res(WEB, s.slice(2))).href;

  const isRel = s.startsWith("./") || s.startsWith("../");
  if (isRel || s.startsWith("file:")) {
    // parentURL is not always a file: URL - during module.register() it is
    // a node:internal specifier, which fileURLToPath rejects outright.
    const parentIsFile = ctx.parentURL?.startsWith("file:") === true;
    const base = parentIsFile ? dirname(fileURLToPath(ctx.parentURL)) : WEB;
    const abs = s.startsWith("file:") ? fileURLToPath(s) : res(base, s);
    for (const cand of [abs, `${abs}.ts`, `${abs}.tsx`, res(abs, "index.ts")]) {
      if (existsSync(cand) && !cand.endsWith("/")) {
        try {
          const st = await import("node:fs").then((m) => m.statSync(cand));
          if (st.isFile()) return next(pathToFileURL(cand).href, ctx);
        } catch {}
      }
    }
  }
  return next(spec, ctx);
}
