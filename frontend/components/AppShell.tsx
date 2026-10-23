"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  BarChart3,
  FileText,
  LogOut,
  MessageSquare,
  ShieldCheck,
} from "lucide-react";
import { useAuth } from "./AuthProvider";
import { Led, Logo, RoleBadge, Screws } from "./ui";
import type { Role } from "../lib/types";

interface NavItem {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  roles?: Role[];
}

const NAV_ITEMS: NavItem[] = [
  { href: "/", label: "Chat", icon: MessageSquare },
  { href: "/documents", label: "Documents", icon: FileText },
  { href: "/dashboard", label: "Dashboard", icon: BarChart3 },
  { href: "/admin", label: "Admin", icon: ShieldCheck, roles: ["ADMIN"] },
];

/**
 * The control panel shared by every signed-in page.
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
    <div className="flex h-screen gap-3 overflow-hidden p-3">
      <nav
        aria-label="Primary"
        className="surface hidden w-60 shrink-0 flex-col px-3 pb-8 pt-7 md:flex"
      >
        <Screws inset={9} />

        <div className="mb-5 flex items-center gap-3 px-2">
          <Logo size={44} />
          <div className="min-w-0">
            <p className="embossed text-sm font-bold leading-tight">
              Enterprise AI
            </p>
            <p className="engraved truncate text-xs">
              {organizationName ?? "Knowledge Base"}
            </p>
          </div>
        </div>

        <p className="label mb-2 px-3">Navigate</p>
        <div className="well mb-4 flex-1 space-y-1 p-2">
          {visibleItems.map((item) => {
            const Icon = item.icon;
            const active = isActive(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className="nav-key"
              >
                <Icon className="h-4 w-4 shrink-0" />
                <span className="flex-1">{item.label}</span>
                <Led on={active} color="amber" />
              </Link>
            );
          })}
        </div>

        <div className="plate p-3">
          <div className="flex items-center gap-3">
            <div className="medallion h-9 w-9 shrink-0 text-sm font-bold">
              {user?.name?.charAt(0).toUpperCase()}
            </div>
            <div className="min-w-0">
              <p className="embossed truncate text-sm font-semibold">
                {user?.name}
              </p>
              <p className="engraved truncate text-xs">{user?.email}</p>
            </div>
          </div>
          <div className="mt-3 flex items-center justify-between">
            {user && <RoleBadge role={user.role} />}
            <button onClick={logout} className="btn btn-sm">
              <LogOut className="h-3.5 w-3.5" />
              Sign out
            </button>
          </div>
        </div>
      </nav>

      <div className="flex min-w-0 flex-1 flex-col gap-3">
        {/* On small screens the rail collapses to a strip of keys, which keeps
            every destination reachable in one tap. */}
        <nav
          aria-label="Primary"
          className="surface flex items-center gap-1 overflow-x-auto p-2 md:hidden"
        >
          {visibleItems.map((item) => {
            const Icon = item.icon;
            const active = isActive(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className="nav-key shrink-0 !gap-2 !px-3"
              >
                <Icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
          <button
            onClick={logout}
            aria-label="Sign out"
            className="btn btn-sm btn-icon ml-auto shrink-0"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </nav>

        <main className="min-h-0 flex-1 overflow-hidden">{children}</main>
      </div>
    </div>
  );
}
