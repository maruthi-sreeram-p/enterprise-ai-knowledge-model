"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "./AuthProvider";
import type { Role } from "../lib/types";

/**
 * Guards a page behind sign-in and, optionally, a role.
 *
 * This is a usability measure, not a security boundary. Hiding a screen stops a
 * user stumbling into something they cannot use; the actual enforcement is on
 * the server, where every endpoint checks the role and the tenant on the token.
 * A user who edits their way past this guard sees an empty page and a 403.
 */
export default function RequireAuth({
  children,
  roles,
}: {
  children: React.ReactNode;
  roles?: Role[];
}) {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) {
      router.replace("/login");
    }
  }, [loading, user, router]);

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#0a0f1c]">
        <div className="flex items-center gap-3 text-slate-400">
          <span className="h-2 w-2 animate-bounce rounded-full bg-[#2563eb]" />
          <span
            className="h-2 w-2 animate-bounce rounded-full bg-[#2563eb]"
            style={{ animationDelay: "0.15s" }}
          />
          <span
            className="h-2 w-2 animate-bounce rounded-full bg-[#2563eb]"
            style={{ animationDelay: "0.3s" }}
          />
        </div>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  if (roles && !roles.includes(user.role)) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#0a0f1c] p-6">
        <div className="glass-panel max-w-md p-8 text-center">
          <h2 className="mb-2 text-lg font-semibold text-white">
            Not available for your role
          </h2>
          <p className="text-sm text-slate-400">
            This section is limited to {roles.join(" and ").toLowerCase()} users.
            You are signed in as {user.role.toLowerCase()}.
          </p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
