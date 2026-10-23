"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { LogOut } from "lucide-react";
import { useAuth } from "./AuthProvider";
import { Logo, RoleBadge } from "./ui";
import type { Role } from "../lib/types";

interface NavItem {
  href: string;
  label: string;
  roles?: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { href: "/", label: "Ask" },
  { href: "/documents", label: "Documents" },
  { href: "/dashboard", label: "Overview" },
  { href: "/admin", label: "Administration", roles: ["ADMIN"] },
];

const TICKER = [
  "Ask anything",
  "Every answer cited",
  "Role-aware access",
  "Your documents only",
  "Private by design",
  "Multi-tenant",
];

/**
 * The masthead shared by every signed-in page: wordmark on the left, numbered
 * sections in the middle, the signed-in person on the right, and a gold rule
 * underneath.
 *
 * Items a user's role cannot use are hidden rather than shown disabled, since
 * there is nothing they could do to gain access from here.
 */
export default function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { user, organizationName, logout } = useAuth();

  const visibleItems = NAV_ITEMS.filter(
    (item) => !item.roles || (user && item.roles.includes(user.role)),
  );

  const isActive = (href: string) =>
    href === "/" ? pathname === "/" : pathname.startsWith(href);

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      <header className="shrink-0 bg-[#0c0718]/90">
        <div className="ticker" aria-hidden>
          <div className="ticker-track">
            {[0, 1].map((copy) => (
              <div key={copy} className="flex">
                {TICKER.map((text) => (
                  <span key={`${copy}-${text}`}>{text} ✦</span>
                ))}
              </div>
            ))}
          </div>
        </div>
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-10 gap-y-0 px-5 sm:px-8">
          <Link href="/" className="flex items-center gap-3 py-4">
            <Logo size={34} />
            <div className="leading-tight">
              <p className="serif text-[1.05rem] tracking-wide">Knowledge Base</p>
              <p className="label !text-[0.6rem]">
                {organizationName ?? "Enterprise AI"}
              </p>
            </div>
          </Link>

          <nav
            aria-label="Primary"
            className="order-3 flex w-full gap-7 overflow-x-auto sm:order-none sm:w-auto sm:flex-1"
          >
            {visibleItems.map((item) => (
              <Link
                key={item.href}
                href={item.href}
                aria-current={isActive(item.href) ? "page" : undefined}
                className="nav-key shrink-0"
              >
                {item.label}
              </Link>
            ))}
          </nav>

          <div className="ml-auto flex items-center gap-4 py-3">
            <div className="hidden text-right leading-tight sm:block">
              <p className="text-sm font-medium">{user?.name}</p>
              <p className="mt-1">{user && <RoleBadge role={user.role} />}</p>
            </div>
            <button
              onClick={logout}
              aria-label="Sign out"
              className="btn btn-sm"
            >
              <LogOut className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Sign out</span>
            </button>
          </div>
        </div>
        <hr className="rule-gold" />
      </header>

      <main className="min-h-0 flex-1 overflow-hidden">{children}</main>
    </div>
  );
}
