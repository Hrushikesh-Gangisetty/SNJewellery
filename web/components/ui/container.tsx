import { cn } from "@/lib/cn";

/**
 * Page-width container with gutters. See docs/design/layout.md §5.
 *
 * Gutters are never zero — text touching a screen edge is the most common
 * mobile layout defect. Do not nest containers: one per page section,
 * because nesting compounds padding.
 */

const widths = {
  prose: "max-w-prose", // 680px, caps the measure at ~70 characters
  content: "max-w-content", // 1280px, the default
  wide: "max-w-wide", // 1536px, full-bleed photography
  full: "max-w-full",
} as const;

export type ContainerWidth = keyof typeof widths;

export function Container({
  width = "content",
  as: Tag = "div",
  className,
  children,
}: {
  width?: ContainerWidth;
  as?: "div" | "section" | "header" | "footer" | "main" | "nav";
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <Tag
      className={cn(
        "mx-auto w-full",
        widths[width],
        // Gutters step up with breakpoint — layout.md §5.
        "px-4 md:px-6 lg:px-8 xl:px-12",
        className,
      )}
    >
      {children}
    </Tag>
  );
}
