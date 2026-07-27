import Image from "next/image";
import Link from "next/link";
import { AspectBox } from "@/components/ui/aspect-box";
import { ButtonLink } from "@/components/ui/button";
import { Container } from "@/components/ui/container";
import { cn } from "@/lib/cn";
import { site, whatsAppHref } from "@/lib/config/site";
import { productImageAlt, type Product } from "@/lib/data";

/**
 * Home page hero.
 *
 * Photography-first per brand.md §2 — but the photograph sits *beside* the
 * type rather than behind it, for two reasons. A product shot is not a
 * controlled background, so text laid over it has unpredictable contrast;
 * and cropping a necklace into a banner ratio is exactly what
 * responsive.md §2 warns against. Beside the type, the piece is shown
 * whole and the words stay legible.
 *
 * Nothing here animates in. motion.md §4 forbids entrance animation above
 * the fold: it delays first sight of the merchandise and harms LCP.
 *
 * `showcase` is the piece pictured, chosen by the page from live data.
 * A shop whose catalogue has no photograph yet is a real state, not a
 * hypothetical — so the hero then renders as type alone across the full
 * width rather than reserving an empty frame.
 */
export function Hero({ showcase }: { showcase: Product | null }) {
  const cover = showcase?.images[0] ?? null;

  return (
    <Container
      className={cn(
        "grid items-center gap-10 py-16 md:gap-16 md:py-24",
        cover ? "md:grid-cols-2" : null,
      )}
    >
      <div>
        <h1 className="font-display text-display-xl text-text-primary">
          {site.name}
        </h1>
        <p className="text-body-l text-text-secondary mt-6 max-w-prose">
          {site.description}
        </p>
        <div className="mt-8 flex flex-wrap gap-3">
          <ButtonLink href="/catalogue">Browse the catalogue</ButtonLink>
          <ButtonLink href={whatsAppHref()} variant="secondary">
            Ask on WhatsApp
          </ButtonLink>
        </div>
      </div>

      {showcase && cover ? (
        <Link href={`/product/${showcase.slug}`} className="group block">
          <AspectBox aspect={cover.aspect} className="rounded-sm">
            <Image
              src={cover.url}
              alt={productImageAlt(showcase)}
              fill
              // The one image on the page that is above the fold on every
              // breakpoint, and the desktop LCP element. It is the only
              // priority image on the home page — marking more would make
              // them compete and slow this one down.
              priority
              sizes="(min-width: 768px) 50vw, 100vw"
              className="object-contain"
            />
          </AspectBox>
          <p className="text-body-s text-text-secondary mt-3">
            Pictured:{" "}
            <span className="text-text-primary group-hover:text-accent-text">
              {showcase.name}
            </span>
          </p>
        </Link>
      ) : null}
    </Container>
  );
}
