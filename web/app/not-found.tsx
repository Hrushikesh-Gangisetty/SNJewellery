import { ButtonLink } from "@/components/ui/button";
import { Container } from "@/components/ui/container";
import { EmptyState } from "@/components/ui/empty-state";

/**
 * 404. Reached by an unknown product or category slug, and by any
 * mistyped URL.
 *
 * ux.md §3 rule 1 — never a dead end. A customer who followed a stale
 * WhatsApp link to a piece that has since been archived arrives here, and
 * that is a likely route to this page rather than a hypothetical one:
 * these URLs get forwarded, and the catalogue changes underneath them. So
 * the page offers the catalogue rather than only apologising.
 *
 * Rule 5 — no error blames the user. "This page does not exist", not
 * "invalid URL".
 */
export default function NotFound() {
  return (
    <main id="main" className="flex-1">
      <Container as="section" className="py-20">
        <EmptyState
          title="This page does not exist"
          description="The piece may have been sold or the link may be out of date. The rest of the collection is still here."
          action={
            <div className="flex flex-wrap justify-center gap-3">
              <ButtonLink href="/catalogue">Browse the catalogue</ButtonLink>
              <ButtonLink href="/" variant="secondary">
                Go to the home page
              </ButtonLink>
            </div>
          }
        />
      </Container>
    </main>
  );
}
