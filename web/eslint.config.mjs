import { dirname } from "path";
import { fileURLToPath } from "url";
import { FlatCompat } from "@eslint/eslintrc";

/*
 * `eslint-config-next` 15 ships in the legacy eslintrc format, so it
 * is loaded through FlatCompat rather than imported as flat config.
 *
 * The scaffold generated a Next 16 style config importing
 * "eslint-config-next/core-web-vitals" directly; that path does not
 * resolve in 15, which is the version the PRD specifies. If Next is
 * ever upgraded to 16, this file reverts to those direct imports.
 */

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const compat = new FlatCompat({ baseDirectory: __dirname });

const eslintConfig = [
  ...compat.extends("next/core-web-vitals", "next/typescript"),
  {
    ignores: [".next/**", "out/**", "build/**", "next-env.d.ts"],
  },
];

export default eslintConfig;
