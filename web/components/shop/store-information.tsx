import { ButtonLink } from "@/components/ui/button";
import { cn } from "@/lib/cn";
import { directionsHref, site } from "@/lib/config/site";

/**
 * Where the shop is and when it is open.
 *
 * Every value comes from lib/config/site.ts (ADR-0010) — nothing here is
 * hard-coded, which is what M4.9's acceptance criterion checks. The
 * directions button hides itself entirely until a Maps location is
 * supplied: a dead "Get directions" is worse than no button at all.
 *
 * Deliberately headless — the caller supplies the heading — so the home
 * page (M4.2) and the contact page (M4.10) can present the same details
 * at different prominence without the shop's address being written twice.
 */
export function StoreInformation({ className }: { className?: string }) {
  const directions = directionsHref();

  return (
    <div className={cn("flex flex-col items-start gap-6", className)}>
      <address className="text-body-l text-text-primary not-italic">
        {site.address.lines.map((line) => (
          <span key={line} className="block">
            {line}
          </span>
        ))}
      </address>

      <p className="text-body-m text-text-secondary">
        <span className="text-text-primary">Open</span> {site.hours.display}
      </p>

      {directions ? (
        <ButtonLink href={directions} variant="secondary">
          Get directions
        </ButtonLink>
      ) : null}
    </div>
  );
}
