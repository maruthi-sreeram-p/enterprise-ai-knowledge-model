"use client";

import { useEffect, useState } from "react";
import {
  AlertCircle,
  CheckCircle2,
  FileText,
  MessageSquare,
  Sparkles,
  Users,
} from "lucide-react";
import AppShell from "../../components/AppShell";
import RequireAuth from "../../components/RequireAuth";
import { useAuth } from "../../components/AuthProvider";
import { api, errorMessage } from "../../lib/api";
import type { DashboardSummary } from "../../lib/types";

export default function DashboardPage() {
  return (
    <RequireAuth>
      <AppShell>
        <DashboardWorkspace />
      </AppShell>
    </RequireAuth>
  );
}

function DashboardWorkspace() {
  const { user, organizationName } = useAuth();
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<DashboardSummary>("/api/dashboard/summary")
      .then((response) => setSummary(response.data))
      .catch((caught) => setError(errorMessage(caught)));
  }, []);

  return (
    <div className="hide-scrollbar h-full overflow-y-auto p-6 md:p-8">
      <div className="mx-auto max-w-5xl">
        <header className="mb-6">
          <h1 className="text-xl font-bold text-white">
            {organizationName ?? "Dashboard"}
          </h1>
          <p className="mt-1 text-sm text-slate-500">
            Signed in as {user?.name} · {user?.role?.toLowerCase()}
          </p>
        </header>

        {error && (
          <div
            role="alert"
            className="mb-5 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-300"
          >
            {error}
          </div>
        )}

        {summary && (
          <>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <MetricCard
                icon={FileText}
                label="Documents"
                value={summary.totalDocuments}
                hint="Visible to your role"
              />
              <MetricCard
                icon={CheckCircle2}
                label="Indexed"
                value={summary.processedDocuments}
                hint="Searchable right now"
              />
              <MetricCard
                icon={AlertCircle}
                label="Failed to index"
                value={summary.failedDocuments}
                hint={
                  summary.failedDocuments > 0
                    ? "See the Documents page for the reason"
                    : "Nothing failed"
                }
                warning={summary.failedDocuments > 0}
              />
              <MetricCard
                icon={Users}
                label="People"
                value={summary.totalUsers}
                hint="In this organization"
              />
              <MetricCard
                icon={MessageSquare}
                label="Your conversations"
                value={summary.myConversations}
                hint="Private to you"
              />
              <MetricCard
                icon={Sparkles}
                label="Questions asked"
                value={summary.totalAiQueries}
                hint="Across the organization"
              />
            </div>

            <p className="mt-6 text-xs text-slate-600">
              Every figure is a live count from your organization&apos;s data.
            </p>
          </>
        )}

        {!summary && !error && (
          <p className="py-12 text-center text-sm text-slate-500">Loading…</p>
        )}
      </div>
    </div>
  );
}

function MetricCard({
  icon: Icon,
  label,
  value,
  hint,
  warning = false,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  value: number;
  hint: string;
  warning?: boolean;
}) {
  return (
    <div className="glass-panel p-5">
      <div className="mb-3 flex items-center gap-2.5">
        <Icon
          className={`h-4 w-4 ${warning ? "text-red-400" : "text-[#60a5fa]"}`}
        />
        <span className="text-xs font-semibold uppercase tracking-wider text-slate-500">
          {label}
        </span>
      </div>
      <p className="text-3xl font-bold text-white">{value}</p>
      <p className="mt-1 text-xs text-slate-600">{hint}</p>
    </div>
  );
}
