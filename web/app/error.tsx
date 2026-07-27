"use client";

import { useEffect } from "react";
import { Button, ButtonLink } from "@/components/ui/button";
import { Container } from "@/components/ui/container";
import { ErrorState } from "@/components/ui/empty-state";

/**
 * The route error boundary.
 *
 * The data layer throws rather than returning empty when a query fails,
 * precisely so an outage cannot masquerade as an empty catalogue (see
 * `unwrap` in lib/data/supabase-source.ts). This is where that throw
 * lands, and it is the honest outcome — a customer told the shop has no
 * jewellery would simply leave.
 *
 * ux.md §3 rule 3: "nothing here" and "something broke" are different
 * messages with different actions, which is why this uses ErrorState and
 * not EmptyState. Rule 2: plain words, and no status code — a customer
 * cannot act on one.
 *
 * `reset` retries the render without a full page load, so a transient
 * failure costs one tap. The contact route is offered alongside it
 * because the shop being reachable does not depend on this site working.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Next reports this to the server logs already; logging the digest
    // here is what makes a customer report traceable to a specific
    // failure. Never log the error object itself — it can carry a query.
    console.error("Route error", error.digest ?? "(no digest)");
  }, [error]);

  return (
    <main id="main" className="flex-1">
      <Container as="section" className="py-20">
        <ErrorState
          title="We could not load this page"
          description="This is a problem on our side, not with your connection. Please try again, or contact the shop directly."
          action={
            <div className="flex flex-wrap justify-center gap-3">
              <Button onClick={reset}>Try again</Button>
              <ButtonLink href="/contact" variant="secondary">
                Contact the shop
              </ButtonLink>
            </div>
          }
        />
      </Container>
    </main>
  );
}
