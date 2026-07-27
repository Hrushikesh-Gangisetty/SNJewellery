import { Button } from "./button";
import { cn } from "@/lib/cn";

/**
 * Load-more pagination. See docs/design/components.md — `Pagination`,
 * load-more variant, states: default, loading, last-page.
 *
 * Load-more rather than numbered pages because the catalogue is a grid of
 * photographs browsed on a phone: numbered pages ask a customer to decide
 * where to go next, and a "page 7" of jewellery means nothing to them.
 * Numbered remains the documented second variant for the admin list.
 *
 * The spinner is not the only signal. `prefers-reduced-motion` freezes it
 * globally (globals.css), so the label carries the state on its own and
 * `aria-busy` carries it for assistive technology.
 */
export function LoadMore({
  onLoadMore,
  pending,
  hasMore,
  /** Shown in place of the button once everything is loaded. */
  exhaustedLabel = "That is everything",
  className,
}: {
  onLoadMore: () => void;
  pending: boolean;
  hasMore: boolean;
  exhaustedLabel?: string;
  className?: string;
}) {
  if (!hasMore) {
    return (
      <p
        className={cn("text-body-s text-text-muted text-center", className)}
        // Reached by loading, so announce it — otherwise a screen-reader
        // user presses the button and it silently disappears.
        role="status"
      >
        {exhaustedLabel}
      </p>
    );
  }

  return (
    <div className={cn("flex justify-center", className)}>
      <Button
        type="button"
        variant="secondary"
        onClick={onLoadMore}
        disabled={pending}
        aria-busy={pending}
      >
        {pending ? (
          <>
            <Spinner />
            Loading
          </>
        ) : (
          "Load more"
        )}
      </Button>
    </div>
  );
}

function Spinner() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      className="animate-spin"
      aria-hidden="true"
    >
      <circle cx="8" cy="8" r="6" className="opacity-25" />
      <path d="M14 8a6 6 0 00-6-6" strokeLinecap="round" />
    </svg>
  );
}
