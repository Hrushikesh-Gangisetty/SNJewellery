"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import { CallButton, WhatsAppButton } from "@/components/shop/conversion";
import { cn } from "@/lib/cn";
import type { Category } from "@/lib/data/types";

/**
 * Mobile navigation drawer.
 *
 * accessibility.md §2 requires all of the following, and each is a
 * separate thing that can be forgotten:
 *   - Esc closes it
 *   - focus is trapped inside while open
 *   - focus returns to the trigger on close
 *   - background scroll is locked
 *   - it is announced as a modal dialog
 *
 * Motion uses the tokens from motion.md: `slow` + `decelerate` opening,
 * `base` + `accelerate` closing. Reduced motion is handled globally in
 * globals.css, so there is no per-component check here.
 */
export function MobileDrawer({
  open,
  onClose,
  categories,
  triggerRef,
}: {
  open: boolean;
  onClose: () => void;
  categories: readonly Category[];
  /** Focus returns here on close. */
  triggerRef: React.RefObject<HTMLButtonElement | null>;
}) {
  const panelRef = useRef<HTMLDivElement>(null);

  // Esc to close, and trap Tab within the panel.
  useEffect(() => {
    if (!open) return;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }

      if (event.key !== "Tab") return;

      const focusable = panelRef.current?.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );
      if (!focusable || focusable.length === 0) return;

      const first = focusable[0];
      const last = focusable[focusable.length - 1];

      // Wrap at both ends so focus cannot escape behind the overlay.
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [open, onClose]);

  // Lock background scroll while open, and restore whatever was there
  // before rather than assuming it was empty.
  useEffect(() => {
    if (!open) return;
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, [open]);

  // Move focus in on open, and back to the trigger on close.
  //
  // The close branch is guarded on having actually been open. Without
  // that, this effect runs once on mount with `open` already false and
  // focuses the hamburger on every page load — which on a phone scrolls
  // the header into view and shows a focus ring nobody asked for.
  const wasOpen = useRef(false);
  useEffect(() => {
    if (open) {
      panelRef.current?.querySelector<HTMLElement>("a, button")?.focus();
    } else if (wasOpen.current) {
      triggerRef.current?.focus();
    }
    wasOpen.current = open;
    // triggerRef is a stable ref object.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  return (
    <>
      {/* Overlay. aria-hidden because the panel carries the semantics. */}
      <div
        aria-hidden="true"
        onClick={onClose}
        className={cn(
          "bg-surface/80 fixed inset-0 z-40 backdrop-blur-sm lg:hidden",
          "ease-standard transition-[opacity,visibility]",
          open
            ? "pointer-events-auto visible opacity-100 duration-[var(--sn-duration-slow)]"
            : "pointer-events-none invisible opacity-0 duration-[var(--sn-duration-base)]",
        )}
      />

      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-label="Menu"
        // Hidden from assistive tech and from Tab order when closed.
        //
        // `inert` must be a real boolean. Passing `""` renders NOTHING:
        // React drops empty-string values for boolean DOM properties, so
        // the closed drawer kept all eight of its links in the Tab order
        // and a keyboard user tabbed into an off-screen menu.
        inert={!open}
        className={cn(
          "bg-surface border-border fixed inset-y-0 right-0 z-50 w-[85vw] max-w-sm border-l lg:hidden",
          "flex flex-col overflow-y-auto",
          // `visibility` is what keeps the closed panel from widening the
          // page. `translate-x-full` only moves it — it still occupies
          // scrollable area one panel-width past the right edge, which is
          // horizontal scroll on every page at every mobile width. Hidden
          // is not focusable and not painted, and it is a transitionable
          // property, so the slide-out still plays before it applies.
          "transition-[transform,visibility]",
          open
            ? "ease-decelerate visible translate-x-0 duration-[var(--sn-duration-slow)]"
            : "ease-accelerate invisible translate-x-full duration-[var(--sn-duration-base)]",
        )}
      >
        <div className="border-border flex min-h-16 items-center justify-between border-b px-4">
          <span className="text-label text-text-muted">Menu</span>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close menu"
            className="text-text-primary hover:bg-surface-sunken -mr-2 inline-flex min-h-11 min-w-11 items-center justify-center rounded-md"
          >
            <CloseIcon />
          </button>
        </div>

        <nav aria-label="Categories" className="flex flex-col p-4">
          <Link
            href="/catalogue"
            onClick={onClose}
            className="text-heading-s text-text-primary hover:bg-surface-sunken flex min-h-11 items-center rounded-md px-2"
          >
            All jewellery
          </Link>

          {categories.map((category) => (
            <Link
              key={category.id}
              href={`/category/${category.slug}`}
              onClick={onClose}
              className="text-body-m text-text-secondary hover:bg-surface-sunken hover:text-text-primary flex min-h-11 items-center rounded-md px-2"
            >
              {category.name}
            </Link>
          ))}

          <hr className="border-border my-4" />

          <Link
            href="/about"
            onClick={onClose}
            className="text-body-m text-text-secondary hover:bg-surface-sunken hover:text-text-primary flex min-h-11 items-center rounded-md px-2"
          >
            About us
          </Link>
          <Link
            href="/contact"
            onClick={onClose}
            className="text-body-m text-text-secondary hover:bg-surface-sunken hover:text-text-primary flex min-h-11 items-center rounded-md px-2"
          >
            Contact
          </Link>
        </nav>

        {/* Conversion actions stay reachable from the menu — they are the
            site's purpose, not a footer afterthought (ux.md §2). */}
        <div className="border-border mt-auto flex flex-col gap-2 border-t p-4">
          <WhatsAppButton />
          <CallButton />
        </div>
      </div>
    </>
  );
}

function CloseIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 20 20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      aria-hidden="true"
    >
      <path d="M5 5l10 10M15 5L5 15" strokeLinecap="round" />
    </svg>
  );
}
