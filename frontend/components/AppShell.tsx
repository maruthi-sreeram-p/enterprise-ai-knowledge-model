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
 * The navigation rail shared by every signed-in page.
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

  return (
    <div className="flex h-screen overflow-hidden bg-[#0a0f1c]">
      <nav className="hidden w-56 flex-col border-r border-slate-800 bg-[#0d1424] md:flex">
        <div className="border-b border-slate-800 px-5 py-5">
          <p className="text-sm font-bold tracking-tight text-white">
            Enterprise AI
          </p>
          <p className="mt-0.5 truncate text-xs text-slate-500">
            {organizationName ?? "Knowledge Base"}
          </p>
        </div>

        <ul className="flex-1 space-y-1 p-3">
          {visibleItems.map((item) => {
            const active =
              item.href === "/"
                ? pathname === "/"
                : pathname.startsWith(item.href);
            const Icon = item.icon;

            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  className={`flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm transition-colors ${
                    active
                      ? "bg-[#2563eb]/15 font-medium text-[#60a5fa]"
                      : "text-slate-400 hover:bg-slate-800/60 hover:text-slate-200"
                  }`}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>

        <div className="border-t border-slate-800 p-3">
          <div className="mb-2 px-2">
            <p className="truncate text-sm font-medium text-slate-200">
              {user?.name}
            </p>
            <p className="truncate text-xs text-slate-500">{user?.email}</p>
            <span className="mt-1.5 inline-block rounded bg-slate-800 px-2 py-0.5 text-[10px] font-semibold tracking-wide text-slate-400">
              {user?.role}
            </span>
          </div>
          <button
            onClick={logout}
            className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-sm text-slate-400 transition-colors hover:bg-slate-800/60 hover:text-red-400"
          >
            <LogOut className="h-4 w-4" />
            Sign out
          </button>
        </div>
      </nav>

      {/* On small screens the rail collapses to a horizontal bar rather than a
          drawer, which keeps every destination reachable in one tap. */}
      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-center gap-1 overflow-x-auto border-b border-slate-800 bg-[#0d1424] px-2 py-2 md:hidden">
          {visibleItems.map((item) => {
            const active =
              item.href === "/"
                ? pathname === "/"
                : pathname.startsWith(item.href);
            const Icon = item.icon;

            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex shrink-0 items-center gap-2 rounded-lg px-3 py-2 text-xs ${
                  active
                    ? "bg-[#2563eb]/15 text-[#60a5fa]"
                    : "text-slate-400"
                }`}
              >
                <Icon className="h-4 w-4" />
                {item.label}
              </Link>
            );
          })}
          <button
            onClick={logout}
            className="ml-auto shrink-0 rounded-lg px-3 py-2 text-xs text-slate-400"
          >
            <LogOut className="h-4 w-4" />
          </button>
        </div>

        <main className="min-h-0 flex-1 overflow-hidden">{children}</main>
      </div>
    </div>
  );
}
