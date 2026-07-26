import { cn } from "@/lib/cn";

/**
 * Section heading with an optional eyebrow label and trailing action.
 *
 * `as` exists so heading level is chosen by document structure rather than
 * by appearance — accessibility.md §3 requires one h1 per page and no
 * skipped levels.
 *
 * Cormorant is used here at heading-l (28px), which is exactly its floor.
 * Never below — see typography.md §1.
 */
export function SectionHeading({
  eyebrow,
  title,
  as: Tag = "h2",
  action,
  className,
}: {
  eyebrow?: string;
  title: string;
  as?: "h1" | "h2" | "h3";
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        "flex flex-wrap items-end justify-between gap-4",
        className,
      )}
    >
      <div>
        {eyebrow ? (
          <p className="text-label text-accent-text mb-2">{eyebrow}</p>
        ) : null}
        <Tag className="font-display text-heading-l text-text-primary">
          {title}
        </Tag>
      </div>
      {action}
    </div>
  );
}
