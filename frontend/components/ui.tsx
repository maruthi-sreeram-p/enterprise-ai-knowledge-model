import { AlertTriangle, BookOpenText, Info } from "lucide-react";

type LedColor = "green" | "red" | "amber" | "blue";

export function Led({
  color = "green",
  on = true,
  pulse = false,
  title,
}: {
  color?: LedColor;
  on?: boolean;
  pulse?: boolean;
  title?: string;
}) {
  return (
    <span
      title={title}
      aria-hidden
      className={`led led-${color} ${on ? "" : "led-off"} ${
        on && pulse ? "led-pulse" : ""
      }`}
    />
  );
}

export function Logo({ size = 56 }: { size?: number }) {
  return (
    <div
      className="medallion"
      style={{ height: size, width: size }}
      aria-hidden
    >
      <BookOpenText
        style={{ height: size * 0.48, width: size * 0.48 }}
        strokeWidth={2.2}
      />
    </div>
  );
}

export function Alert({
  children,
  tone = "error",
}: {
  children: React.ReactNode;
  tone?: "error" | "info";
}) {
  const Icon = tone === "error" ? AlertTriangle : Info;
  return (
    <div
      role={tone === "error" ? "alert" : "status"}
      className={`alert ${tone === "error" ? "alert-error" : "alert-info"}`}
    >
      <Icon
        className={`mt-0.5 h-4 w-4 shrink-0 ${
          tone === "error" ? "text-led-red" : "text-led-blue"
        }`}
      />
      <span>{children}</span>
    </div>
  );
}

/**
 * Section opener: a tilted eyebrow tag, a big italic serif title
 * and a line of context, closed by a gold hairline.
 */
export function PageHeader({
  title,
  subtitle,
  eyebrow,
  action,
}: {
  title: string;
  subtitle?: string;
  eyebrow?: string;
  action?: React.ReactNode;
}) {
  return (
    <header className="mb-10">
      <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
        <div className="flex items-end gap-5 sm:gap-7">
          <div className="pb-1">
            {eyebrow && <p className="eyebrow mb-2">{eyebrow}</p>}
            <h1 className="serif text-4xl font-extrabold italic leading-none tracking-tight sm:text-6xl">
              {title}
            </h1>
            {subtitle && (
              <p className="engraved mt-2 max-w-xl text-sm leading-relaxed">
                {subtitle}
              </p>
            )}
          </div>
        </div>
        {action}
      </div>
      <hr className="rule-gold mt-8" />
    </header>
  );
}

export function LoadingDots({ label = "Loading" }: { label?: string }) {
  return (
    <div
      role="status"
      aria-label={label}
      className="flex items-center justify-center gap-2.5 py-14"
    >
      {[0, 1, 2].map((index) => (
        <span
          key={index}
          className="led led-amber led-pulse"
          style={{ animationDelay: `${index * 0.2}s` }}
        />
      ))}
    </div>
  );
}

export function RoleBadge({ role }: { role: string }) {
  const tone =
    role === "ADMIN" ? "text-brass" : role === "MANAGER" ? "text-led-blue" : "text-ink-dim";
  return (
    <span className={`text-[0.62rem] font-semibold tracking-[0.2em] ${tone}`}>
      {role}
    </span>
  );
}
