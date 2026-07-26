import Link from "next/link";
import { cn } from "@/lib/cn";
import type { Category } from "@/lib/data";

/**
 * Category link, also the filter control in M10.
 *
 * min-h-11 is the 44px touch target from layout.md §6. Active state is
 * carried by fill AND `aria-current`, never by colour alone.
 */
export function CategoryChip({
  category,
  active = false,
  className,
}: {
  category: Category;
  active?: boolean;
  className?: string;
}) {
  return (
    <Link
      href={`/category/${category.slug}`}
      aria-current={active ? "page" : undefined}
      className={cn(
        "inline-flex min-h-11 items-center rounded-md px-4 py-2",
        "text-body-s",
        "ease-standard transition-colors duration-[var(--sn-duration-fast)]",
        active
          ? "bg-accent text-on-accent"
          : "border-border-interactive text-text-primary hover:bg-surface-sunken border",
        className,
      )}
    >
      {category.name}
    </Link>
  );
}
