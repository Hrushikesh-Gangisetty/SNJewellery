import { AspectBox } from "@/components/ui/aspect-box";
import { Container } from "@/components/ui/container";
import { Skeleton } from "@/components/ui/skeleton";

/**
 * Product detail loading state.
 *
 * ux.md §3: a skeleton for the image at the CORRECT aspect ratio, plus
 * text lines. The gallery frame is the thing that must not move — it is
 * the largest element on the page, so a wrong ratio here would shift
 * everything below it.
 *
 * `product` (1/1) is the default aspect and the right guess: the real
 * ratio is not known until the data arrives, and a portrait piece
 * settling from square is a smaller correction than the reverse.
 */
export default function Loading() {
  return (
    <main id="main" className="flex-1">
      <Container as="section" className="py-8 md:py-12">
        <div className="grid gap-10 lg:grid-cols-2 lg:gap-14">
          <div>
            <AspectBox aspect="product" />
            <div className="mt-3 flex gap-2">
              {Array.from({ length: 4 }, (_, i) => (
                <Skeleton key={i} className="size-18 shrink-0" />
              ))}
            </div>
          </div>

          <div className="flex flex-col">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="mt-3 h-10 w-4/5" />
            <Skeleton className="mt-6 h-5 w-full max-w-prose" />
            <Skeleton className="mt-2 h-5 w-3/5" />

            <div className="mt-8 flex flex-col gap-3">
              <Skeleton className="h-11 w-full sm:w-56" />
              <Skeleton className="h-11 w-full sm:w-56" />
            </div>
          </div>
        </div>
      </Container>
    </main>
  );
}
