import { AlertTriangle, BookOpenText, Info } from "lucide-react";

/** The four corner fasteners that make a panel look bolted down. */
export function Screws({ inset = 10 }: { inset?: number }) {
  const turns = ["20deg", "75deg", "140deg", "55deg"];
  const positions = [
    { top: inset, left: inset },
    { top: inset, right: inset },
    { bottom: inset, left: inset },
    { bottom: inset, right: inset },
  ];
  return (
    <>
      {positions.map((position, index) => (
        <span
          key={index}
          aria-hidden
          className="screw"
          style={{ ...position, ["--turn" as string]: turns[index] }}
        />
      ))}
    </>
  );
}

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

export function PageHeader({
  title,
  subtitle,
  action,
}: {
  title: string;
  subtitle?: string;
  action?: React.ReactNode;
}) {
  return (
    <header className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="embossed text-2xl font-bold tracking-tight">{title}</h1>
        {subtitle && (
          <p className="engraved mt-1 text-sm">{subtitle}</p>
        )}
      </div>
      {action}
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
    role === "ADMIN" ? "text-brass-hi" : role === "MANAGER" ? "text-led-blue" : "text-ink-muted";
  return (
    <span
      className={`well inline-block px-2 py-0.5 font-mono text-[10px] font-bold tracking-[0.14em] ${tone}`}
      style={{ borderRadius: 6 }}
    >
      {role}
    </span>
  );
}
