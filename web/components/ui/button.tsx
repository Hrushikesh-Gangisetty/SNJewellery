import Link from "next/link";
import { cn } from "@/lib/cn";

/**
 * Button and button-styled link. See docs/design/components.md.
 *
 * Every variant meets the 44px minimum touch target from layout.md §6,
 * including `ghost` — visual size may be smaller than the hit area, but
 * the hit area may not shrink.
 *
 * `accent` is used as a FILL with dark text on it, never as text colour:
 * at 2.38:1 on white it is decorative-only (colour.md §1).
 */

const base = [
  // min-h-11 is 44px — the touch-target floor, not a style choice.
  "inline-flex min-h-11 items-center justify-center gap-2",
  "rounded-md px-5 py-2",
  "text-body-m font-medium",
  "transition-colors duration-[var(--sn-duration-fast)] ease-standard",
  "disabled:pointer-events-none disabled:opacity-50",
  // Focus comes from the global :focus-visible rule in globals.css.
].join(" ");

const variants = {
  /** Gold fill, dark text — 7.35:1, safe. */
  primary: "bg-accent text-on-accent hover:brightness-95",
  /** Outlined. border-interactive is the only border token meeting 3:1. */
  secondary:
    "border border-border-interactive text-text-primary hover:bg-surface-sunken",
  /** Text only. */
  ghost: "text-text-primary hover:bg-surface-sunken px-3",
  /** Destructive — admin surfaces only; the public site has no such action. */
  destructive: "bg-danger text-surface hover:brightness-95",
} as const;

export type ButtonVariant = keyof typeof variants;

type Common = {
  variant?: ButtonVariant;
  className?: string;
  children: React.ReactNode;
};

export function Button({
  variant = "primary",
  className,
  children,
  ...props
}: Common & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button className={cn(base, variants[variant], className)} {...props}>
      {children}
    </button>
  );
}

/**
 * A link styled as a button. Uses `next/link` for internal hrefs and a
 * plain anchor for external ones — `tel:`, `wa.me`, and maps links must
 * not be routed by the client router.
 */
export function ButtonLink({
  href,
  variant = "primary",
  className,
  children,
  ...props
}: Common &
  Omit<React.AnchorHTMLAttributes<HTMLAnchorElement>, "href"> & {
    href: string;
  }) {
  const classes = cn(base, variants[variant], className);
  const isInternal = href.startsWith("/") && !href.startsWith("//");

  if (isInternal) {
    return (
      <Link href={href} className={classes} {...props}>
        {children}
      </Link>
    );
  }

  return (
    <a
      href={href}
      className={classes}
      // Only add noopener for http(s); it is meaningless on tel:.
      {...(href.startsWith("http")
        ? { target: "_blank", rel: "noopener noreferrer" }
        : {})}
      {...props}
    >
      {children}
    </a>
  );
}
