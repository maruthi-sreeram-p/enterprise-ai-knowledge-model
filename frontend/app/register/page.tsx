"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Building2, Lock, Mail, User as UserIcon } from "lucide-react";
import { useAuth } from "../../components/AuthProvider";
import { errorMessage } from "../../lib/api";

/**
 * Registers a new organization together with its first administrator.
 *
 * This is the only way a tenant is created. Colleagues are added afterwards by
 * that administrator from the Admin screen, so nobody can join an existing
 * organization by guessing its name.
 */
export default function RegisterPage() {
  const router = useRouter();
  const { register, user, loading } = useAuth();

  const [organizationName, setOrganizationName] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!loading && user) {
      router.replace("/");
    }
  }, [loading, user, router]);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);

    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }

    setSubmitting(true);
    try {
      await register(organizationName, name, email, password);
    } catch (caught) {
      setError(errorMessage(caught));
      setSubmitting(false);
    }
  };

  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden bg-[#0a0f1c] p-4">
      <div className="animate-pulse-glow pointer-events-none absolute right-1/4 top-1/4 h-[500px] w-[500px] rounded-full bg-[#2563eb]/20 blur-[120px]" />
      <div
        className="animate-pulse-glow pointer-events-none absolute bottom-1/4 left-1/4 h-[400px] w-[400px] rounded-full bg-blue-900/20 blur-[100px]"
        style={{ animationDelay: "1s" }}
      />

      <div className="glass-panel animate-slide-up relative z-10 w-full max-w-md border-slate-700/50 p-8 md:p-10">
        <div className="mb-8 text-center">
          <h1 className="mb-2 text-2xl font-bold tracking-tight text-white">
            Create a workspace
          </h1>
          <p className="text-sm text-slate-400">
            You will be the first administrator of this organization
          </p>
        </div>

        {error && (
          <div
            role="alert"
            className="mb-5 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-300"
          >
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label
              htmlFor="organization"
              className="mb-1 ml-1 block text-sm font-medium text-slate-300"
            >
              Organization name
            </label>
            <div className="relative">
              <Building2 className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-500" />
              <input
                id="organization"
                type="text"
                required
                value={organizationName}
                onChange={(event) => setOrganizationName(event.target.value)}
                placeholder="Acme Corporation"
                className="w-full rounded-lg border border-slate-600 bg-slate-900/50 py-3 pl-10 pr-4 text-slate-200 placeholder-slate-500 transition-all focus:outline-none focus:ring-2 focus:ring-[#2563eb]"
              />
            </div>
          </div>

          <div>
            <label
              htmlFor="name"
              className="mb-1 ml-1 block text-sm font-medium text-slate-300"
            >
              Your name
            </label>
            <div className="relative">
              <UserIcon className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-500" />
              <input
                id="name"
                type="text"
                required
                autoComplete="name"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Ada Lovelace"
                className="w-full rounded-lg border border-slate-600 bg-slate-900/50 py-3 pl-10 pr-4 text-slate-200 placeholder-slate-500 transition-all focus:outline-none focus:ring-2 focus:ring-[#2563eb]"
              />
            </div>
          </div>

          <div>
            <label
              htmlFor="email"
              className="mb-1 ml-1 block text-sm font-medium text-slate-300"
            >
              Work email
            </label>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-500" />
              <input
                id="email"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="name@company.com"
                className="w-full rounded-lg border border-slate-600 bg-slate-900/50 py-3 pl-10 pr-4 text-slate-200 placeholder-slate-500 transition-all focus:outline-none focus:ring-2 focus:ring-[#2563eb]"
              />
            </div>
          </div>

          <div>
            <label
              htmlFor="password"
              className="mb-1 ml-1 block text-sm font-medium text-slate-300"
            >
              Password
            </label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-slate-500" />
              <input
                id="password"
                type="password"
                required
                minLength={8}
                maxLength={72}
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="At least 8 characters"
                className="w-full rounded-lg border border-slate-600 bg-slate-900/50 py-3 pl-10 pr-4 text-slate-200 placeholder-slate-500 transition-all focus:outline-none focus:ring-2 focus:ring-[#2563eb]"
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="btn-primary mt-6 w-full py-3 shadow-[0_0_15px_rgba(37,99,235,0.3)] disabled:cursor-not-allowed disabled:opacity-60"
          >
            {submitting ? "Creating workspace…" : "Create workspace"}
          </button>
        </form>

        <p className="mt-8 text-center text-sm text-slate-400">
          Already have an account?{" "}
          <Link
            href="/login"
            className="font-medium text-[#2563eb] transition-colors hover:text-[#60a5fa]"
          >
            Sign in
          </Link>
        </p>
      </div>
    </main>
  );
}
