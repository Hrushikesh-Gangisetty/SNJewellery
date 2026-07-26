// Registers the TypeScript resolve hook. Separate file because
// `--import` needs a module, and `register()` must run before the
// entry point is resolved.
import { register } from "node:module";
import { pathToFileURL } from "node:url";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
register(pathToFileURL(resolve(here, "ts-resolve.mjs")).href);
