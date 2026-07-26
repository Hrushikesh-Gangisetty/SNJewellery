import { site } from "@/lib/config/site";

/**
 * Placeholder route, not a placeholder implementation.
 *
 * The real home page — hero, featured collections, newest items,
 * category shortcuts, store information — is M4.2, and it reads from the
 * data-access layer defined in M2.5. Neither exists yet.
 *
 * What this file does do is exercise the M1 tokens and the site config
 * end to end, so the foundation is provably working before M2.7 builds
 * the shell on top of it. M4.2 replaces it wholesale.
 */
export default function Home() {
  return (
    <main id="main" className="max-w-content mx-auto flex-1 px-4 py-24 md:px-6">
      <p className="text-label text-accent-text">In development</p>

      <h1 className="font-display text-display-xl text-text-primary mt-4">
        {site.name}
      </h1>

      <p className="text-body-l text-text-secondary mt-6 max-w-prose">
        {site.description}
      </p>

      <dl className="text-body-m mt-12 space-y-2">
        <div className="flex gap-2">
          <dt className="text-text-muted">Address</dt>
          <dd className="text-text-primary">{site.address.lines.join(", ")}</dd>
        </div>
        <div className="flex gap-2">
          <dt className="text-text-muted">Hours</dt>
          <dd className="text-text-primary">{site.hours.display}</dd>
        </div>
      </dl>
    </main>
  );
}
