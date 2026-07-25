/*
 * Placeholder route, not a placeholder implementation.
 *
 * The real home page — hero, featured collections, newest items,
 * category shortcuts, store information — is M4.2, and it reads
 * from the data-access layer defined in M2.5. Neither exists yet.
 *
 * This file exists only so the app has a root route and builds.
 * M4.2 replaces it wholesale. It deliberately carries no brand
 * styling, because M1 has not defined any yet.
 */
export default function Home() {
  return (
    <main className="flex flex-1 items-center justify-center p-8">
      <p>Jewellery catalogue — in development.</p>
    </main>
  );
}
