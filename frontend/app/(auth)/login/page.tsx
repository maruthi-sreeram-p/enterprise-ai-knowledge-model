"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { KeyRound, LogIn, Mail } from "lucide-react";
import AuthShell from "../../../components/AuthShell";
import { StageLink } from "../../../components/AuthStage";
import { PasswordField, TextField } from "../../../components/fields";
import { Alert } from "../../../components/ui";
import { useAuth } from "../../../components/AuthProvider";
import { errorMessage } from "../../../lib/api";

export default function LoginPage() {
  const router = useRouter();
  const { login, user, loading } = useAuth();

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
    setSubmitting(true);

    try {
      await login(email, password);
    } catch (caught) {
      setError(errorMessage(caught));
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Welcome back"
      subtitle="Sign in to your workspace"
      footer={
        <>
          Need a workspace for your organization?{" "}
          <StageLink
            href="/register"
            className="font-semibold text-brass transition-colors hover:text-brass-hi"
          >
            Create one
          </StageLink>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <Alert>{error}</Alert>}

        <TextField
          label="Email address"
          icon={Mail}
          type="email"
          required
          autoComplete="email"
          autoFocus
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="name@company.com"
        />

        <PasswordField
          label="Password"
          icon={KeyRound}
          required
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          placeholder="Enter your password"
        />

        <button
          type="submit"
          disabled={submitting}
          className="btn btn-primary mt-2 w-full"
        >
          <LogIn className="h-4 w-4" />
          {submitting ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </AuthShell>
  );
}
