"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { Lock } from "lucide-react";
import { useAuth } from "./AuthProvider";
import { LoadingDots, Screws } from "./ui";
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
  const pathname = usePathname();

  useEffect(() => {
    if (!loading && !user) {
      const next = pathname && pathname !== "/" ? `?next=${encodeURIComponent(pathname)}` : "";
      router.replace(`/login${next}`);
    }
  }, [loading, user, router, pathname]);

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <LoadingDots label="Checking your session" />
      </div>
    );
  }

  if (!user) {
    return null;
  }

  if (roles && !roles.includes(user.role)) {
    return (
      <div className="flex h-screen items-center justify-center p-6">
        <div className="surface rise relative max-w-md px-10 py-10 text-center">
          <Screws />
          <div className="well mx-auto mb-5 grid h-14 w-14 place-items-center rounded-full">
            <Lock className="h-6 w-6 text-led-amber" />
          </div>
          <h2 className="embossed mb-2 text-lg font-semibold">
            Not available for your role
          </h2>
          <p className="engraved text-sm">
            This section is limited to {roles.join(" and ").toLowerCase()}{" "}
            users. You are signed in as {user.role.toLowerCase()}.
          </p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}
