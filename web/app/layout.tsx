import type { Metadata } from "next";
import { Header } from "@/components/layout/header";
import { Footer } from "@/components/layout/footer";
import { fontVariables } from "@/lib/fonts";
import { site } from "@/lib/config/site";
import { getVisibleCategories } from "@/lib/data/cache";
import "./globals.css";

/**
 * Per-page titles, Open Graph tags, and structured data are M11's work.
 * This is the baseline: a real title and description from the site config,
 * never a hard-coded string.
 */
export const metadata: Metadata = {
  title: {
    default: site.name,
    template: `%s · ${site.shortName}`,
  },
  description: site.description,
};

export default async function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  // The header and footer both list categories, so they are fetched once
  // here rather than twice. Reads go through the data boundary — never
  // Supabase directly (CLAUDE.md §3.4).
  const categories = await getVisibleCategories();

  return (
    <html lang="en" className={`${fontVariables} h-full antialiased`}>
      <body className="bg-surface text-text-primary flex min-h-full flex-col">
        {/*
         * First focusable element on the page, visually hidden until
         * focused. Required by docs/design/accessibility.md §2.
         */}
        <a
          href="#main"
          className="bg-surface text-text-primary focus:ring-focus sr-only focus:not-sr-only focus:absolute focus:top-4 focus:left-4 focus:z-50 focus:rounded-md focus:px-4 focus:py-2 focus:ring-2"
        >
          Skip to content
        </a>

        <Header categories={categories} />
        {children}
        <Footer categories={categories} />
      </body>
    </html>
  );
}
