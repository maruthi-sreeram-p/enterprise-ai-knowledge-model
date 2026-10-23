"use client";

import { useCallback, useEffect, useState } from "react";
import { Trash2, UserPlus } from "lucide-react";
import AppShell from "../../components/AppShell";
import RequireAuth from "../../components/RequireAuth";
import { Alert, PageHeader } from "../../components/ui";
import { PasswordField, TextField } from "../../components/fields";
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
    <div className="h-full overflow-y-auto pr-1">
      <div className="mx-auto max-w-5xl p-3 md:p-5">
        <PageHeader
          title="Administration"
          subtitle="Manage the people in your organization and review what has happened."
        />

        <div role="tablist" className="well switch mb-6">
          <button
            role="tab"
            aria-selected={tab === "users"}
            onClick={() => setTab("users")}
            className="switch-key"
          >
            Users
          </button>
          <button
            role="tab"
            aria-selected={tab === "audit"}
            onClick={() => setTab("audit")}
            className="switch-key"
          >
            Audit log
          </button>
        </div>

        {tab === "users" ? <UsersPanel /> : <AuditPanel />}
      </div>
    </div>
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
    api
      .get<Page<User>>("/api/users", { params: { size: 100 } })
      .then((response) => setUsers(response.data.content))
      .catch((caught) => setError(errorMessage(caught)));
  }, []);

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
    <div className="space-y-5">
      {error && <Alert>{error}</Alert>}

      <div>
        <button
          onClick={() => setShowForm((previous) => !previous)}
          className={`btn ${showForm ? "" : "btn-primary"}`}
        >
          <UserPlus className="h-4 w-4" />
          {showForm ? "Cancel" : "Add user"}
        </button>
      </div>

      {showForm && (
        <form onSubmit={handleCreate} className="surface rise space-y-5 p-6">
          <div className="grid gap-5 sm:grid-cols-2">
            <TextField
              label="Name"
              type="text"
              required
              value={name}
              onChange={(event) => setName(event.target.value)}
            />
            <TextField
              label="Email"
              type="email"
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
            <PasswordField
              label="Temporary password"
              required
              minLength={8}
              maxLength={72}
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              placeholder="At least 8 characters"
            />
            <div>
              <label htmlFor="new-user-role" className="label mb-2 ml-1 block">
                Role
              </label>
              <select
                id="new-user-role"
                value={role}
                onChange={(event) => setRole(event.target.value as Role)}
                className="input"
              >
                <option value="EMPLOYEE">Employee</option>
                <option value="MANAGER">Manager</option>
                <option value="ADMIN">Admin</option>
              </select>
            </div>
          </div>

          <button
            type="submit"
            disabled={submitting}
            className="btn btn-primary"
          >
            {submitting ? "Creating…" : "Create user"}
          </button>
        </form>
      )}

      <div className="surface p-3">
        <div className="well overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-black/70 bg-white/[0.02]">
                  <th className="label px-4 py-3">Name</th>
                  <th className="label px-4 py-3">Email</th>
                  <th className="label px-4 py-3">Role</th>
                  <th className="px-4 py-3" />
                </tr>
              </thead>
              <tbody>
                {users.map((entry) => {
                  const isSelf = entry.id === currentUser?.id;
                  return (
                    <tr
                      key={entry.id}
                      className="border-b border-white/[0.04] transition-colors last:border-0 hover:bg-white/[0.03]"
                    >
                      <td className="px-4 py-3 font-semibold text-ink">
                        {entry.name}
                        {isSelf && (
                          <span className="engraved ml-2 text-xs font-normal">
                            (you)
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-ink-muted">{entry.email}</td>
                      <td className="px-4 py-3">
                        <select
                          aria-label={`Role for ${entry.name}`}
                          value={entry.role}
                          onChange={(event) =>
                            handleRoleChange(entry, event.target.value as Role)
                          }
                          className="input !w-auto !py-1.5 !text-xs"
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
                            className="btn btn-sm btn-icon btn-danger"
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
    <div className="space-y-4">
      {error && <Alert>{error}</Alert>}

      <div className="flex items-center gap-3">
        <label htmlFor="action-filter" className="label">
          Filter
        </label>
        <select
          id="action-filter"
          value={action}
          onChange={(event) => setAction(event.target.value)}
          className="input !w-auto !py-2 text-sm"
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

      <p className="engraved text-xs">
        Question and answer text is not recorded by default, because it can
        contain confidential document content. What is recorded is who asked,
        when, and which documents were cited.
      </p>

      <div className="surface p-3">
        <div className="well display overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-black/70 bg-white/[0.02]">
                  <th className="label px-4 py-3">When</th>
                  <th className="label px-4 py-3">Who</th>
                  <th className="label px-4 py-3">Action</th>
                  <th className="label px-4 py-3">Details</th>
                </tr>
              </thead>
              <tbody>
                {logs.length === 0 && (
                  <tr>
                    <td
                      colSpan={4}
                      className="engraved px-4 py-10 text-center text-sm"
                    >
                      No audit records match this filter.
                    </td>
                  </tr>
                )}
                {logs.map((entry) => (
                  <tr
                    key={entry.id}
                    className="border-b border-white/[0.04] transition-colors last:border-0 hover:bg-white/[0.03]"
                  >
                    <td className="whitespace-nowrap px-4 py-3 font-mono text-xs text-ink-muted">
                      {new Date(entry.createdAt).toLocaleString()}
                    </td>
                    <td className="px-4 py-3 text-ink">
                      {entry.userName ?? (
                        <span className="text-ink-dim">unknown</span>
                      )}
                    </td>
                    <td className="px-4 py-3">
                      <span className="plate inline-block rounded-md px-2 py-0.5 font-mono text-xs text-brass-hi">
                        {entry.action}
                      </span>
                    </td>
                    <td className="max-w-md px-4 py-3">
                      <span className="block truncate font-mono text-xs text-ink-dim">
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
    </div>
  );
}
