import { cn } from "@/lib/cn";
import type { MetalRate } from "@/lib/data/types";

/**
 * Today's gold and silver rates.
 *
 * Added 2026-07-27 in place of per-piece purity and weight, which the
 * owner removed from the customer-facing site. One number a customer can
 * check, rather than a specification on every card.
 *
 * ── Hides rather than substitutes ────────────────────────────────────
 * Renders NOTHING until every metal has a published rate. A rates panel
 * is only worth anything if the number is real, and half a panel — or a
 * placeholder — is worse than no panel, because a customer would act on
 * it. This is the same rule the rest of the site's configuration follows
 * (ADR-0010): null means hide the section cleanly.
 *
 * ── The timestamp is not decoration ──────────────────────────────────
 * A rate without a date is a rate a customer cannot trust. It is
 * formatted in Asia/Kolkata explicitly rather than in the viewer's zone:
 * the shop is in Markapur and "updated 9:12 AM" must mean the shop's
 * morning, not the reader's. Fixing the zone also keeps the server and
 * client renders identical.
 *
 * ── Restraint ────────────────────────────────────────────────────────
 * brand.md §6 bans banner stacking, so this is a quiet panel in the page
 * flow, not a strip pinned under the header. It states a fact and invites
 * a question; it does not sell.
 */

const LABELS: Record<MetalRate["metal"], string> = {
  gold: "Gold",
  silver: "Silver",
};

const rupees = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  // Rates are quoted to the rupee in the shop. Silver at 92.50 would
  // display as ₹93, which is the number a customer is actually told.
  maximumFractionDigits: 0,
});

const timestamp = new Intl.DateTimeFormat("en-IN", {
  timeZone: "Asia/Kolkata",
  day: "numeric",
  month: "short",
  hour: "numeric",
  minute: "2-digit",
});

/** Both a real rate and a real timestamp, or this metal is unpublished. */
function isPublished(
  rate: MetalRate,
): rate is MetalRate & { ratePerGram: number; updatedAt: string } {
  return rate.ratePerGram !== null && rate.updatedAt !== null;
}

export function MetalRates({
  rates,
  className,
}: {
  rates: readonly MetalRate[];
  className?: string;
}) {
  if (rates.length === 0 || !rates.every(isPublished)) return null;

  // The panel carries one date, so use the oldest — claiming the fresher
  // of the two would overstate how current the other one is.
  const asOf = rates.reduce(
    (oldest, rate) =>
      Date.parse(rate.updatedAt) < Date.parse(oldest) ? rate.updatedAt : oldest,
    rates[0].updatedAt,
  );

  return (
    <div className={cn("border-border rounded-sm border p-6", className)}>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="text-label text-accent-text">Today&rsquo;s rates</h2>
        <p className="text-caption text-text-muted">
          Updated{" "}
          <time dateTime={asOf}>{timestamp.format(new Date(asOf))}</time>
        </p>
      </div>

      <dl className="mt-4 flex flex-wrap gap-x-12 gap-y-4">
        {rates.map((rate) => (
          <div key={rate.metal}>
            <dt className="text-body-s text-text-secondary">
              {LABELS[rate.metal]}
            </dt>
            <dd className="text-heading-m text-text-primary mt-1">
              {rupees.format(rate.ratePerGram)}
              <span className="text-body-s text-text-muted ml-1">per gram</span>
            </dd>
          </div>
        ))}
      </dl>

      <p className="text-caption text-text-muted mt-4">
        Indicative. Ask us to confirm the rate for a piece.
      </p>
    </div>
  );
}
