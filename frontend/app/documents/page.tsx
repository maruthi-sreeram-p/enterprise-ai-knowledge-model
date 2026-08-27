"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { AlertCircle, CheckCircle2, Clock, Trash2, Upload } from "lucide-react";
import AppShell from "../../components/AppShell";
import RequireAuth from "../../components/RequireAuth";
import { useAuth } from "../../components/AuthProvider";
import { api, errorMessage } from "../../lib/api";
import type {
  DocumentStatus,
  DocumentVisibility,
  KnowledgeDocument,
  Page,
} from "../../lib/types";

const VISIBILITY_LABELS: Record<DocumentVisibility, string> = {
  ALL_EMPLOYEES: "All employees",
  MANAGERS_AND_ADMINS: "Managers and admins",
  ADMINS_ONLY: "Admins only",
};

export default function DocumentsPage() {
  return (
    <RequireAuth>
      <AppShell>
        <DocumentsWorkspace />
      </AppShell>
    </RequireAuth>
  );
}

function DocumentsWorkspace() {
  const { hasRole } = useAuth();
  const isAdmin = hasRole("ADMIN");

  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [visibility, setVisibility] =
    useState<DocumentVisibility>("ALL_EMPLOYEES");

  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadDocuments = useCallback(async () => {
    try {
      const response = await api.get<Page<KnowledgeDocument>>(
        "/api/documents",
        { params: { size: 100 } },
      );
      setDocuments(response.data.content);
      setError(null);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadDocuments();
  }, [loadDocuments]);

  // Indexing happens in the background, so the list is refreshed while anything
  // is still pending. Polling stops as soon as nothing is in flight, rather than
  // running forever.
  useEffect(() => {
    const stillWorking = documents.some(
      (document) =>
        document.status === "PENDING" || document.status === "PROCESSING",
    );
    if (!stillWorking) return;

    const timer = setInterval(() => void loadDocuments(), 3000);
    return () => clearInterval(timer);
  }, [documents, loadDocuments]);

  const handleUpload = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    setUploading(true);
    setError(null);

    const formData = new FormData();
    formData.append("file", file);

    try {
      await api.post("/api/documents/upload", formData, {
        params: { visibility },
        headers: { "Content-Type": "multipart/form-data" },
      });
      await loadDocuments();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setUploading(false);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
    }
  };

  const handleDelete = async (document: KnowledgeDocument) => {
    if (
      !window.confirm(
        `Delete "${document.fileName}"? Its file and search index entries are removed too.`,
      )
    ) {
      return;
    }
    try {
      await api.delete(`/api/documents/${document.id}`);
      setDocuments((previous) =>
        previous.filter((entry) => entry.id !== document.id),
      );
    } catch (caught) {
      setError(errorMessage(caught));
    }
  };

  return (
    <div className="hide-scrollbar h-full overflow-y-auto p-6 md:p-8">
      <div className="mx-auto max-w-5xl">
        <header className="mb-6">
          <h1 className="text-xl font-bold text-white">Documents</h1>
          <p className="mt-1 text-sm text-slate-500">
            {isAdmin
              ? "Upload PDF, DOCX, or TXT files. Text is extracted, split, and indexed for search."
              : "Documents your role has access to."}
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

        {isAdmin && (
          <div className="glass-panel mb-6 p-5">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
              <div className="flex-1">
                <label
                  htmlFor="visibility"
                  className="mb-1.5 block text-sm font-medium text-slate-300"
                >
                  Who can read this document
                </label>
                <select
                  id="visibility"
                  value={visibility}
                  onChange={(event) =>
                    setVisibility(event.target.value as DocumentVisibility)
                  }
                  className="w-full rounded-lg border border-slate-600 bg-slate-900/50 px-3 py-2.5 text-sm text-slate-200 focus:outline-none focus:ring-2 focus:ring-[#2563eb]"
                >
                  {Object.entries(VISIBILITY_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
                <p className="mt-1.5 text-xs text-slate-600">
                  This also limits which questions can retrieve it.
                </p>
              </div>

              <div>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".pdf,.docx,.txt"
                  onChange={handleUpload}
                  disabled={uploading}
                  className="hidden"
                  id="file-upload"
                />
                <label
                  htmlFor="file-upload"
                  className={`btn-primary cursor-pointer px-5 py-2.5 ${
                    uploading ? "pointer-events-none opacity-60" : ""
                  }`}
                >
                  <Upload className="h-4 w-4" />
                  {uploading ? "Uploading…" : "Upload document"}
                </label>
              </div>
            </div>
          </div>
        )}

        {loading ? (
          <p className="py-12 text-center text-sm text-slate-500">Loading…</p>
        ) : documents.length === 0 ? (
          <div className="glass-panel p-10 text-center">
            <p className="text-sm text-slate-400">No documents yet.</p>
            <p className="mt-1 text-xs text-slate-600">
              {isAdmin
                ? "Upload one above to start answering questions from it."
                : "An administrator needs to upload documents before you can ask about them."}
            </p>
          </div>
        ) : (
          <div className="glass-panel overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-slate-700/60 text-xs uppercase tracking-wider text-slate-500">
                  <tr>
                    <th className="px-4 py-3 font-semibold">File</th>
                    <th className="px-4 py-3 font-semibold">Status</th>
                    <th className="px-4 py-3 font-semibold">Visibility</th>
                    <th className="px-4 py-3 font-semibold">Indexed</th>
                    <th className="px-4 py-3 font-semibold">Uploaded by</th>
                    {isAdmin && <th className="px-4 py-3" />}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {documents.map((document) => (
                    <tr key={document.id} className="hover:bg-slate-800/30">
                      <td className="px-4 py-3">
                        <p className="font-medium text-slate-200">
                          {document.fileName}
                        </p>
                        <p className="text-xs text-slate-600">
                          {formatBytes(document.sizeBytes)}
                          {/* Only shown when the format actually has pages.
                              Loose `!= null` covers both null and undefined: the
                              API omits pageCount entirely for DOCX and TXT. */}
                          {document.pageCount != null &&
                            ` · ${document.pageCount} page${
                              document.pageCount === 1 ? "" : "s"
                            }`}
                        </p>
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge
                          status={document.status}
                          reason={document.failureReason}
                        />
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-400">
                        {VISIBILITY_LABELS[document.visibility]}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-400">
                        {document.status === "PROCESSED"
                          ? `${document.chunkCount} passage${
                              document.chunkCount === 1 ? "" : "s"
                            }`
                          : "—"}
                      </td>
                      <td className="px-4 py-3 text-xs text-slate-400">
                        {document.uploadedByName ?? "—"}
                      </td>
                      {isAdmin && (
                        <td className="px-4 py-3 text-right">
                          <button
                            onClick={() => handleDelete(document)}
                            aria-label={`Delete ${document.fileName}`}
                            className="rounded-md p-1.5 text-slate-500 transition-colors hover:bg-slate-700 hover:text-red-400"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function StatusBadge({
  status,
  reason,
}: {
  status: DocumentStatus;
  reason?: string;
}) {
  if (status === "PROCESSED") {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-500/15 px-2.5 py-1 text-xs font-medium text-emerald-400">
        <CheckCircle2 className="h-3.5 w-3.5" />
        Indexed
      </span>
    );
  }
  if (status === "FAILED") {
    return (
      <span
        title={reason}
        className="inline-flex items-center gap-1.5 rounded-full bg-red-500/15 px-2.5 py-1 text-xs font-medium text-red-400"
      >
        <AlertCircle className="h-3.5 w-3.5" />
        Failed
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-500/15 px-2.5 py-1 text-xs font-medium text-amber-400">
      <Clock className="h-3.5 w-3.5 animate-pulse" />
      {status === "PROCESSING" ? "Indexing" : "Queued"}
    </span>
  );
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
