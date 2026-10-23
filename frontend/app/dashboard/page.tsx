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
import { Alert, Led, LoadingDots, PageHeader } from "../../components/ui";
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
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-6xl px-5 py-10 sm:px-8">
        <PageHeader
          eyebrow="Overview"
          title={organizationName ?? "Dashboard"}
          subtitle={`Signed in as ${user?.name} · ${user?.role?.toLowerCase()}`}
        />

        {error && (
          <div className="mb-5">
            <Alert>{error}</Alert>
          </div>
        )}

        {summary && (
          <>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
              <Meter
                icon={FileText}
                label="Documents"
                value={summary.totalDocuments}
                hint="Visible to your role"
              />
              <Meter
                icon={CheckCircle2}
                label="Indexed"
                value={summary.processedDocuments}
                hint="Searchable right now"
              />
              <Meter
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
              <Meter
                icon={Users}
                label="People"
                value={summary.totalUsers}
                hint="In this organization"
              />
              <Meter
                icon={MessageSquare}
                label="Your conversations"
                value={summary.myConversations}
                hint="Private to you"
              />
              <Meter
                icon={Sparkles}
                label="Questions asked"
                value={summary.totalAiQueries}
                hint="Across the organization"
              />
            </div>

            <p className="engraved mt-6 text-xs">
              Every figure is a live count from your organization&apos;s data.
            </p>
          </>
        )}

        {!summary && !error && <LoadingDots label="Loading figures" />}
      </div>
    </div>
  );
}

/**
 * One figure, set like a ledger entry: a hairline above, a small-caps label,
 * a large light serif number, and a line of context.
 */
function Meter({
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
    <div className="rise surface p-6">
      <div className="mb-5 flex items-center justify-between">
        <span className="label flex items-center gap-2">
          <Icon className="h-3.5 w-3.5" />
          {label}
        </span>
        <Led color={warning ? "red" : "green"} pulse={warning} />
      </div>

      <p
        className={`serif text-5xl font-semibold leading-none tracking-tight ${
          warning ? "text-led-red" : "text-ink"
        }`}
      >
        {value}
      </p>

      <p className="engraved mt-4 text-xs">{hint}</p>
    </div>
  );
}
