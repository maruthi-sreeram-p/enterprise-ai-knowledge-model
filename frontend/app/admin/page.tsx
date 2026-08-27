"use client";

import { useCallback, useEffect, useState } from "react";
import { Trash2, UserPlus } from "lucide-react";
import AppShell from "../../components/AppShell";
import RequireAuth from "../../components/RequireAuth";
import { useAuth } from "../../components/AuthProvider";
import { api, errorMessage } from "../../lib/api";
import type { AuditLog, Page, Role, User } from "../../lib/types";

type Tab = "users" | "audit";

export default function AdminPage() {
  return (
    <RequireAuth roles={["ADMIN"]}>
      <AppShell>
        <AdminWorkspace />
      </AppShell>
    </RequireAuth>
  );
}

function AdminWorkspace() {
  const [tab, setTab] = useState<Tab>("users");

  return (
    <div className="hide-scrollbar h-full overflow-y-auto p-6 md:p-8">
      <div className="mx-auto max-w-5xl">
        <header className="mb-5">
          <h1 className="text-xl font-bold text-white">Administration</h1>
          <p className="mt-1 text-sm text-slate-500">
            Manage the people in your organization and review what has happened.
          </p>
        </header>

        <div className="mb-5 flex gap-1 border-b border-slate-800">
          <TabButton active={tab === "users"} onClick={() => setTab("users")}>
            Users
          </TabButton>
          <TabButton active={tab === "audit"} onClick={() => setTab("audit")}>
            Audit log
          </TabButton>
        </div>

        {tab === "users" ? <UsersPanel /> : <AuditPanel />}
      </div>
    </div>
  );
}

function TabButton({
  active,
  onClick,
  children,
}: {
  active: boolean;
  onClick: () => void;
  children: React.ReactNode;
}) {
  return (
    <button
      onClick={onClick}
      className={`-mb-px border-b-2 px-4 py-2.5 text-sm transition-colors ${
        active
          ? "border-[#2563eb] font-medium text-[#60a5fa]"
          : "border-transparent text-slate-500 hover:text-slate-300"
      }`}
    >
      {children}
    </button>
  );
}

function UsersPanel() {
  const { user: currentUser } = useAuth();

  const [users, setUsers] = useState<User[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<Role>("EMPLOYEE");

  const loadUsers = useCallback(async () => {
    try {
      const response = await api.get<Page<User>>("/api/users", {
        params: { size: 100 },
      });
      setUsers(response.data.content);
      setError(null);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }, []);

  useEffect(() => {
    void loadUsers();
  }, [loadUsers]);

  const handleCreate = async (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);

    try {
      await api.post("/api/users", { name, email, password, role });
      setName("");
      setEmail("");
      setPassword("");
      setRole("EMPLOYEE");
      setShowForm(false);
      await loadUsers();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSubmitting(false);
    }
  };

  const handleRoleChange = async (target: User, nextRole: Role) => {
    try {
      await api.put(`/api/users/${target.id}`, { role: nextRole });
      await loadUsers();
    } catch (caught) {
      setError(errorMessage(caught));
    }
  };

  const handleDelete = async (target: User) => {
    if (!window.confirm(`Remove ${target.name} from this organization?`)) {
      return;
    }
    try {
      await api.delete(`/api/users/${target.id}`);
      await loadUsers();
    } catch (caught) {
      setError(errorMessage(caught));
    }
  };

  return (
    <div>
      {error && (
        <div
          role="alert"
          className="mb-4 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-300"
        >
          {error}
        </div>
      )}

      <div className="mb-4">
        <button
          onClick={() => setShowForm((previous) => !previous)}
          className="btn-primary px-4 py-2.5 text-sm"
        >
          <UserPlus className="h-4 w-4" />
          {showForm ? "Cancel" : "Add user"}
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="glass-panel mb-5 space-y-4 p-5">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field label="Name">
              <input
                type="text"
                required
                value={name}
                onChange={(event) => setName(event.target.value)}
                className={inputClass}
              />
            </Field>
            <Field label="Email">
              <input
                type="email"
                required
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                className={inputClass}
              />
            </Field>
            <Field label="Temporary password">
              <input
                type="password"
                required
                minLength={8}
                maxLength={72}
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                placeholder="At least 8 characters"
                className={inputClass}
              />
            </Field>
            <Field label="Role">
              <select
                value={role}
                onChange={(event) => setRole(event.target.value as Role)}
                className={inputClass}
              >
                <option value="EMPLOYEE">Employee</option>
                <option value="MANAGER">Manager</option>
                <option value="ADMIN">Admin</option>
              </select>
            </Field>
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="btn-primary px-5 py-2.5 text-sm disabled:opacity-60"
          >
            {submitting ? "Creating…" : "Create user"}
          </button>
        </form>
      )}

      <div className="glass-panel overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-700/60 text-xs uppercase tracking-wider text-slate-500">
              <tr>
                <th className="px-4 py-3 font-semibold">Name</th>
                <th className="px-4 py-3 font-semibold">Email</th>
                <th className="px-4 py-3 font-semibold">Role</th>
                <th className="px-4 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {users.map((entry) => {
                const isSelf = entry.id === currentUser?.id;
                return (
                  <tr key={entry.id} className="hover:bg-slate-800/30">
                    <td className="px-4 py-3 font-medium text-slate-200">
                      {entry.name}
                      {isSelf && (
                        <span className="ml-2 text-xs text-slate-600">
                          (you)
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 text-slate-400">{entry.email}</td>
                    <td className="px-4 py-3">
                      <select
                        value={entry.role}
                        onChange={(event) =>
                          handleRoleChange(entry, event.target.value as Role)
                        }
                        className="rounded border border-slate-700 bg-slate-900/60 px-2 py-1 text-xs text-slate-300 focus:outline-none focus:ring-1 focus:ring-[#2563eb]"
                      >
                        <option value="EMPLOYEE">Employee</option>
                        <option value="MANAGER">Manager</option>
                        <option value="ADMIN">Admin</option>
                      </select>
                    </td>
                    <td className="px-4 py-3 text-right">
                      {/* The server refuses this too; hiding it just avoids a
                          pointless round trip and a confusing error. */}
                      {!isSelf && (
                        <button
                          onClick={() => handleDelete(entry)}
                          aria-label={`Remove ${entry.name}`}
                          className="rounded-md p-1.5 text-slate-500 transition-colors hover:bg-slate-700 hover:text-red-400"
                        >
                          <Trash2 className="h-4 w-4" />
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function AuditPanel() {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [action, setAction] = useState("");

  useEffect(() => {
    api
      .get<Page<AuditLog>>("/api/audit-logs", {
        params: { size: 100, ...(action ? { action } : {}) },
      })
      .then((response) => {
        setLogs(response.data.content);
        setError(null);
      })
      .catch((caught) => setError(errorMessage(caught)));
  }, [action]);

  return (
    <div>
      {error && (
        <div
          role="alert"
          className="mb-4 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-300"
        >
          {error}
        </div>
      )}

      <div className="mb-4 flex items-center gap-3">
        <label htmlFor="action-filter" className="text-sm text-slate-400">
          Filter
        </label>
        <select
          id="action-filter"
          value={action}
          onChange={(event) => setAction(event.target.value)}
          className="rounded-lg border border-slate-600 bg-slate-900/50 px-3 py-2 text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-[#2563eb]"
        >
          <option value="">All actions</option>
          <option value="LOGIN">Login</option>
          <option value="LOGIN_FAILED">Failed login</option>
          <option value="USER_REGISTERED">User created</option>
          <option value="USER_UPDATED">User updated</option>
          <option value="USER_DELETED">User deleted</option>
          <option value="DOCUMENT_UPLOAD">Document uploaded</option>
          <option value="DOCUMENT_ACCESS">Document accessed</option>
          <option value="DOCUMENT_DELETE">Document deleted</option>
          <option value="AI_QUERY">AI question</option>
        </select>
      </div>

      <p className="mb-3 text-xs text-slate-600">
        Question and answer text is not recorded by default, because it can
        contain confidential document content. What is recorded is who asked,
        when, and which documents were cited.
      </p>

      <div className="glass-panel overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-700/60 text-xs uppercase tracking-wider text-slate-500">
              <tr>
                <th className="px-4 py-3 font-semibold">When</th>
                <th className="px-4 py-3 font-semibold">Who</th>
                <th className="px-4 py-3 font-semibold">Action</th>
                <th className="px-4 py-3 font-semibold">Details</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800">
              {logs.length === 0 && (
                <tr>
                  <td
                    colSpan={4}
                    className="px-4 py-10 text-center text-sm text-slate-600"
                  >
                    No audit records match this filter.
                  </td>
                </tr>
              )}
              {logs.map((entry) => (
                <tr key={entry.id} className="hover:bg-slate-800/30">
                  <td className="whitespace-nowrap px-4 py-3 text-xs text-slate-400">
                    {new Date(entry.createdAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-slate-300">
                    {entry.userName ?? (
                      <span className="text-slate-600">unknown</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    <span className="rounded bg-slate-800 px-2 py-0.5 font-mono text-xs text-slate-300">
                      {entry.action}
                    </span>
                  </td>
                  <td className="max-w-md px-4 py-3">
                    <span className="block truncate font-mono text-xs text-slate-500">
                      {entry.metadata ?? "—"}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

const inputClass =
  "w-full rounded-lg border border-slate-600 bg-slate-900/50 px-3 py-2.5 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-[#2563eb]";

function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}) {
  return (
    <div>
      <label className="mb-1.5 block text-sm font-medium text-slate-300">
        {label}
      </label>
      {children}
    </div>
  );
}
