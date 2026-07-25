import type { Metadata } from "next";
import "./globals.css";

/*
 * Typefaces are deliberately not configured here.
 *
 * The scaffold's Geist fonts were removed: choosing a typeface is
 * M1.4's decision, and wiring it up via `next/font` is M2.6. The
 * app shell — header, footer, mobile drawer — arrives in M2.7.
 *
 * The metadata below is provisional. Real per-page titles,
 * descriptions, and Open Graph tags are M11's work, and the brand
 * name itself is blocked on Open Question 9.
 */
export const metadata: Metadata = {
  title: "Jewellery Catalogue",
  description:
    "Browse our jewellery collections. Visit our store or contact us to enquire.",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="h-full antialiased">
      <body className="flex min-h-full flex-col">{children}</body>
    </html>
  );
}
