// Registers a resolve hook that points `next/cache` at ./next-cache.mjs.
//
// Separate from the stub itself because `register()` must run before the
// entry point resolves anything - the same reason tools/register-ts.mjs
// exists. Loaded via --import in the test:revalidate script.
import { register } from "node:module";

register(new URL("./next-cache-resolve.mjs", import.meta.url).href);
