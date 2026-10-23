import {
  FileUp,
  Library,
  MessageSquareQuote,
  ShieldCheck,
  Users,
} from "lucide-react";
import { Logo } from "./ui";

const FEATURES = [
  {
    icon: MessageSquareQuote,
    title: "Cited answers",
    text: "Every answer shows the exact passages and pages it was drawn from.",
  },
  {
    icon: ShieldCheck,
    title: "Role-aware access",
    text: "Admins, managers and employees only ever retrieve what their role may read.",
  },
  {
    icon: Users,
    title: "Private per organization",
    text: "Each organization is isolated. No one can see another tenant's data.",
  },
];

const STEPS = [
  { icon: FileUp, title: "Upload", text: "PDF, DOCX or TXT files with a visibility level." },
  { icon: Library, title: "Index", text: "Text is split into passages and made searchable." },
  { icon: MessageSquareQuote, title: "Ask", text: "Plain-language questions, answered with sources." },
];

const ROLES = [
  { role: "Admin", access: "everything, plus users and audit log", color: "var(--pink)" },
  { role: "Manager", access: "all-staff and manager documents", color: "var(--cyan)" },
  { role: "Employee", access: "all-staff documents", color: "var(--lime)" },
];

/**
 * The frame shared by sign-in and registration. It doubles as the public
 * introduction to the product: what it is, what it does and how it works sit
 * beside the form on wide screens and below it on narrow ones.
 */
export default function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
}) {
  return (
    <main className="mx-auto grid min-h-screen max-w-7xl lg:grid-cols-[1.25fr_1fr]">
      <section className="order-2 flex flex-col gap-12 px-6 py-12 sm:px-12 lg:order-1 lg:border-r lg:border-line-strong/60 lg:py-14">
        <div className="hidden items-center gap-3 lg:flex">
          <Logo size={38} />
          <p className="label">Enterprise AI Knowledge Base</p>
        </div>

        <div>
          <p className="eyebrow mb-5">Knowledge, answered</p>
          <h2 className="serif max-w-xl text-4xl font-extrabold italic leading-[1.02] tracking-tight sm:text-[3.4rem]">
            Your organization&apos;s knowledge,{" "}
            <em className="text-brass-hi">cited</em> and kept private.
          </h2>
          <p className="engraved mt-6 max-w-lg leading-relaxed">
            Upload your handbooks, policies and reports once. Then anyone on
            your team can ask questions in plain language and get an answer
            grounded in the documents they are allowed to read, with the source
            passages shown alongside.
          </p>
        </div>

        <div className="grid gap-4 sm:grid-cols-3">
          {FEATURES.map(({ icon: Icon, title: featureTitle, text }) => (
            <div key={featureTitle} className="surface p-5">
              <Icon className="mb-3 h-6 w-6 text-cyan" />
              <h3 className="mb-1.5 text-sm font-extrabold uppercase tracking-wider">
                {featureTitle}
              </h3>
              <p className="engraved text-sm leading-relaxed">{text}</p>
            </div>
          ))}
        </div>

        <div>
          <p className="eyebrow mb-5 !bg-lime">How it works</p>
          <ol className="grid gap-5 sm:grid-cols-3">
            {STEPS.map(({ icon: Icon, title: stepTitle, text }, index) => (
              <li key={stepTitle} className="flex gap-3">
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-full border-2 border-[#14081f] bg-brass text-sm font-extrabold text-[#14081f]">
                  {index + 1}
                </span>
                <div>
                  <p className="flex items-center gap-2 font-bold">
                    <Icon className="h-4 w-4 text-cyan" />
                    {stepTitle}
                  </p>
                  <p className="engraved mt-1 text-sm leading-relaxed">{text}</p>
                </div>
              </li>
            ))}
          </ol>
        </div>

        <div>
          <p className="label mb-3">Who sees what</p>
          <div className="flex flex-wrap gap-2.5">
            {ROLES.map(({ role, access, color }) => (
              <span
                key={role}
                className="plate flex items-center gap-2 rounded-full px-3.5 py-1.5 text-xs"
              >
                <span
                  className="h-2.5 w-2.5 rounded-full"
                  style={{ background: color }}
                />
                <b>{role}</b>
                <span className="text-ink-muted">{access}</span>
              </span>
            ))}
          </div>
        </div>
      </section>

      <section className="order-1 flex items-start px-6 py-12 sm:px-12 lg:sticky lg:top-0 lg:order-2 lg:h-screen lg:overflow-y-auto">
        <div className="auth-card rise surface mx-auto my-auto w-full max-w-sm p-6">
          <div className="mb-5 flex items-center gap-3 lg:hidden">
            <Logo size={34} />
            <p className="label">Enterprise AI Knowledge Base</p>
          </div>

          <p className="eyebrow mb-2">Account</p>
          <h1 className="serif text-3xl font-extrabold italic leading-tight tracking-tight">
            {title}
          </h1>
          <p className="engraved mb-4 mt-1 text-sm">{subtitle}</p>
          <hr className="rule-gold mb-5" />

          {children}

          <div className="engraved mt-5 border-t border-line pt-4 text-sm">
            {footer}
          </div>
        </div>
      </section>
    </main>
  );
}
