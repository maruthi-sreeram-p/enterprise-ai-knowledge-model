"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Building2, KeyRound, Mail, Rocket, User as UserIcon } from "lucide-react";
import AuthShell from "../../components/AuthShell";
import {
  PasswordField,
  StrengthMeter,
  TextField,
} from "../../components/fields";
import { Alert } from "../../components/ui";
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
  const [confirmation, setConfirmation] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!loading && user) {
      router.replace("/");
    }
  }, [loading, user, router]);

  const mismatch = confirmation.length > 0 && confirmation !== password;

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);

    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }
    if (password !== confirmation) {
      setError("The two passwords do not match.");
      return;
    }

    setSubmitting(true);
    try {
      await register(organizationName.trim(), name.trim(), email, password);
    } catch (caught) {
      setError(errorMessage(caught));
      setSubmitting(false);
    }
  };

  return (
    <AuthShell
      title="Create a workspace"
      subtitle="You will be this organization's first administrator"
      footer={
        <>
          Already have an account?{" "}
          <Link
            href="/login"
            className="font-semibold text-brass transition-colors hover:text-brass-hi"
          >
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} className="space-y-3">
        {error && <Alert>{error}</Alert>}

        <TextField
          label="Organization name"
          icon={Building2}
          type="text"
          required
          maxLength={200}
          autoFocus
          value={organizationName}
          onChange={(event) => setOrganizationName(event.target.value)}
          placeholder="Acme Corporation"
        />

        <TextField
          label="Your name"
          icon={UserIcon}
          type="text"
          required
          maxLength={200}
          autoComplete="name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          placeholder="Ada Lovelace"
        />

        <TextField
          label="Work email"
          icon={Mail}
          type="email"
          required
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          placeholder="name@company.com"
        />

        <div className="space-y-3">
          <PasswordField
            label="Password"
            icon={KeyRound}
            required
            minLength={8}
            maxLength={72}
            autoComplete="new-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            placeholder="At least 8 characters"
          />
          <StrengthMeter password={password} />
        </div>

        <PasswordField
          label="Confirm password"
          icon={KeyRound}
          required
          maxLength={72}
          autoComplete="new-password"
          value={confirmation}
          onChange={(event) => setConfirmation(event.target.value)}
          placeholder="Repeat the password"
          warnCapsLock={false}
          hint={mismatch ? "Passwords do not match yet." : undefined}
          aria-invalid={mismatch}
        />

        <button
          type="submit"
          disabled={submitting}
          className="btn btn-primary mt-2 w-full"
        >
          <Rocket className="h-4 w-4" />
          {submitting ? "Creating workspace…" : "Create workspace"}
        </button>
      </form>
    </AuthShell>
  );
}
