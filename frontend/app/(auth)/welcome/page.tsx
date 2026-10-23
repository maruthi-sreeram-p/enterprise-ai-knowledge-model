"use client";

import { Suspense, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import {
  ArrowRight,
  FileUp,
  Library,
  MessageSquareQuote,
  ShieldCheck,
  Users,
} from "lucide-react";
import { useAuth } from "../../../components/AuthProvider";
import { StageLink } from "../../../components/AuthStage";
import { Logo } from "../../../components/ui";

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
  { icon: FileUp, title: "Upload", text: "PDF, DOCX or TXT files, each with a visibility level." },
  { icon: Library, title: "Index", text: "Text is split into passages and made searchable." },
  { icon: MessageSquareQuote, title: "Ask", text: "Plain-language questions, answered with sources." },
];

const ROLES = [
  { role: "Admin", access: "everything, plus users and the audit log", color: "var(--pink)" },
  { role: "Manager", access: "all-staff and manager documents", color: "var(--cyan)" },
  { role: "Employee", access: "all-staff documents", color: "var(--lime)" },
];

export default function WelcomePage() {
  return (
    <Suspense fallback={null}>
      <Welcome />
    </Suspense>
  );
}

function Welcome() {
  const router = useRouter();
  const params = useSearchParams();
  const { user, loading } = useAuth();

  // Keeps a deep link alive: RequireAuth sends people here with ?next=.
  const next = params.get("next");
  const loginHref = next ? `/login?next=${encodeURIComponent(next)}` : "/login";

  useEffect(() => {
    if (!loading && user) {
      router.replace("/");
    }
  }, [loading, user, router]);

  return (
    <main className="mx-auto max-w-5xl px-5 pb-20 sm:px-8">
      <header className="flex items-center justify-between py-6">
        <div className="flex items-center gap-3">
          <Logo size={34} />
          <p className="label">Enterprise AI Knowledge Base</p>
        </div>
        <StageLink href={loginHref} className="btn btn-sm">
          Sign in
        </StageLink>
      </header>

      <section className="py-12 sm:py-20">
        <p className="eyebrow mb-6">Knowledge, answered</p>
        <h1 className="serif max-w-3xl text-5xl font-extrabold italic leading-[1.02] tracking-tight sm:text-7xl">
          Your organization&apos;s knowledge,{" "}
          <em className="text-brass-hi">cited</em> and kept private.
        </h1>
        <p className="engraved mt-7 max-w-xl text-lg leading-relaxed">
          Upload your handbooks, policies and reports once. Anyone on your team
          can then ask questions in plain language and get an answer grounded
          in the documents they are allowed to read, with the source passages
          shown alongside.
        </p>
        <div className="mt-9 flex flex-wrap items-center gap-4">
          <StageLink href={loginHref} className="btn btn-primary px-7 py-3.5 text-sm">
            Get started
            <ArrowRight className="h-4 w-4" />
          </StageLink>
          <StageLink href="/register" className="engraved text-sm hover:text-ink">
            New here? <span className="text-brass">Create a workspace</span>
          </StageLink>
        </div>
      </section>

      <section className="grid gap-5 sm:grid-cols-3">
        {FEATURES.map(({ icon: Icon, title, text }) => (
          <div key={title} className="surface p-6">
            <Icon className="mb-4 h-7 w-7 text-cyan" />
            <h2 className="mb-2 text-sm font-extrabold uppercase tracking-wider">
              {title}
            </h2>
            <p className="engraved text-sm leading-relaxed">{text}</p>
          </div>
        ))}
      </section>

      <section className="mt-20">
        <p className="eyebrow mb-7 !bg-lime">How it works</p>
        <ol className="grid gap-8 sm:grid-cols-3">
          {STEPS.map(({ icon: Icon, title, text }, index) => (
            <li key={title} className="flex gap-4">
              <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full border-2 border-[#14081f] bg-brass font-extrabold text-[#14081f]">
                {index + 1}
              </span>
              <div>
                <p className="flex items-center gap-2 font-bold">
                  <Icon className="h-4 w-4 text-cyan" />
                  {title}
                </p>
                <p className="engraved mt-1 text-sm leading-relaxed">{text}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className="mt-20">
        <p className="label mb-4">Who sees what</p>
        <div className="flex flex-wrap gap-3">
          {ROLES.map(({ role, access, color }) => (
            <span
              key={role}
              className="plate flex items-center gap-2.5 rounded-full px-4 py-2 text-sm"
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
      </section>

      <section className="surface mt-20 flex flex-wrap items-center justify-between gap-5 p-7">
        <p className="serif text-2xl font-extrabold italic">
          Ready to ask your documents something?
        </p>
        <StageLink href={loginHref} className="btn btn-primary">
          Get started
          <ArrowRight className="h-4 w-4" />
        </StageLink>
      </section>
    </main>
  );
}
