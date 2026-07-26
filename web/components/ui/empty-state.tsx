import { cn } from "@/lib/cn";

/**
 * Empty and error states. See docs/design/ux.md §3.
 *
 * The rule these encode: **never a dead end.** Every empty state offers a
 * next step, which is why `action` is part of the shape rather than
 * optional decoration.
 */
export function EmptyState({
  title,
  description,
  action,
  className,
}: {
  title: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("px-4 py-16 text-center", className)}>
      <p className="text-heading-m text-text-primary">{title}</p>
      {description ? (
        <p className="text-body-m text-text-secondary mx-auto mt-3 max-w-prose">
          {description}
        </p>
      ) : null}
      {action ? <div className="mt-8">{action}</div> : null}
    </div>
  );
}

/**
 * Distinct from EmptyState on purpose — ux.md §3 rule 3: "nothing here"
 * and "something broke" need different messages and different actions.
 */
export function ErrorState({
  title = "Something went wrong",
  description,
  action,
  className,
}: {
  title?: string;
  description?: string;
  action?: React.ReactNode;
  className?: string;
}) {
  return (
    <div className={cn("px-4 py-16 text-center", className)} role="alert">
      <p className="text-heading-m text-text-primary">{title}</p>
      {description ? (
        <p className="text-body-m text-text-secondary mx-auto mt-3 max-w-prose">
          {description}
        </p>
      ) : null}
      {action ? <div className="mt-8">{action}</div> : null}
    </div>
  );
}
