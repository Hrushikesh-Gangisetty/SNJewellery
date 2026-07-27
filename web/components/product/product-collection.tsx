"use client";

import { useState, useTransition } from "react";
import { ProductGrid } from "./product-grid";
import { LoadMore } from "@/components/ui/pagination";
import { fetchMoreProducts } from "@/lib/data/actions";
import { cn } from "@/lib/cn";
// Types only, and from ./types rather than the barrel — see product-card.
import type { Page, Product } from "@/lib/data/types";

/**
 * A product grid that can grow.
 *
 * The first page is rendered on the server — so the catalogue is complete
 * and indexable with JavaScript disabled — and this component only takes
 * over when the customer asks for more.
 *
 * ux.md §3 governs the loading behaviour, and both halves of its rule
 * matter: existing results stay on screen and dim, rather than the page
 * blanking. Replacing a grid of photographs with skeletons on every page
 * of a catalogue is the single most jarring thing this page could do.
 *
 * Callers must pass `key={categorySlug}` when the slug can change, or
 * React reuses this instance across two category routes and the second
 * category renders the first one's accumulated products.
 */
export function ProductCollection({
  initial,
  categorySlug = null,
  priorityCount = 0,
  emptyState,
}: {
  initial: Page<Product>;
  /** `null` for the whole catalogue. */
  categorySlug?: string | null;
  priorityCount?: number;
  /** Replaces ProductGrid's generic empty state where the page has better copy. */
  emptyState?: React.ReactNode;
}) {
  /**
   * The items and the point the newest batch starts are one piece of
   * state, not two. Deriving `enterFrom` from a separate `items.length`
   * read would be a stale closure the moment two appends ever overlap.
   *
   * `enterFrom` is undefined on the first render: those cards came from
   * the server and must not animate (motion.md §4).
   */
  const [collection, setCollection] = useState<{
    items: readonly Product[];
    enterFrom?: number;
  }>(() => ({ items: initial.items }));
  const { items, enterFrom } = collection;

  const [cursor, setCursor] = useState(initial.nextCursor);
  const [pending, startTransition] = useTransition();
  const [failed, setFailed] = useState(false);

  function loadMore() {
    if (!cursor) return;
    setFailed(false);

    startTransition(async () => {
      try {
        const next = await fetchMoreProducts({ categorySlug, cursor });
        setCollection((current) => ({
          items: [...current.items, ...next.items],
          enterFrom: current.items.length,
        }));
        setCursor(next.nextCursor);
      } catch {
        // The pieces already loaded stay on screen; the customer can
        // retry. ux.md §3: "nothing here" and "something broke" are
        // different messages.
        setFailed(true);
      }
    });
  }

  if (items.length === 0 && emptyState) {
    return <>{emptyState}</>;
  }

  // A catalogue that fits on one page never had pagination, so it gets no
  // control and no "that is everything" — that message is only meaningful
  // as the answer to having pressed the button.
  const paginated = initial.nextCursor !== null;

  return (
    <div>
      <ProductGrid
        products={items}
        priorityCount={priorityCount}
        enterFrom={enterFrom}
        className={cn(
          "ease-standard transition-opacity duration-[var(--sn-duration-base)]",
          pending && "opacity-60",
        )}
      />

      {/* Announces the appended pieces — without it, pressing "Load more"
          changes nothing that a screen reader reports. */}
      <p role="status" className="sr-only">
        Showing {items.length} {items.length === 1 ? "piece" : "pieces"}
      </p>

      {failed ? (
        <p role="alert" className="text-body-s text-danger mt-8 text-center">
          Could not load more pieces. Please try again.
        </p>
      ) : null}

      {paginated ? (
        <LoadMore
          onLoadMore={loadMore}
          pending={pending}
          hasMore={cursor !== null}
          className="mt-10"
        />
      ) : null}
    </div>
  );
}
