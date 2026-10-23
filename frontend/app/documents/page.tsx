"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { FileText, Trash2, Upload } from "lucide-react";
import AppShell from "../../components/AppShell";
import RequireAuth from "../../components/RequireAuth";
import { Alert, Led, LoadingDots, PageHeader } from "../../components/ui";
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

  // The first load and the polling are one effect. Indexing happens in the
  // background, so the list is re-fetched every few seconds while anything is
  // still pending, and the timer is not started once nothing is in flight.
  const stillWorking = documents.some(
    (document) =>
      document.status === "PENDING" || document.status === "PROCESSING",
  );

  useEffect(() => {
    if (!loading && !stillWorking) return;
    const timer = setTimeout(() => void loadDocuments(), loading ? 0 : 3000);
    return () => clearTimeout(timer);
    // `documents` is a dependency on purpose: each fetch yields a new array,
    // which re-arms the timer for the next poll.
  }, [loading, stillWorking, documents, loadDocuments]);

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
    <div className="h-full overflow-y-auto">
      <div className="mx-auto max-w-6xl px-5 py-10 sm:px-8">
        <PageHeader
          eyebrow="The library"
          title="Documents"
          subtitle={
            isAdmin
              ? "Upload PDF, DOCX, or TXT files. Text is extracted, split, and indexed for search."
              : "Documents your role has access to."
          }
        />

        {error && (
          <div className="mb-5">
            <Alert>{error}</Alert>
          </div>
        )}

        {isAdmin && (
          <div className="surface mb-6 p-5">
            <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
              <div className="flex-1">
                <label htmlFor="visibility" className="label mb-2 ml-1 block">
                  Who can read this document
                </label>
                <select
                  id="visibility"
                  value={visibility}
                  onChange={(event) =>
                    setVisibility(event.target.value as DocumentVisibility)
                  }
                  className="input"
                >
                  {Object.entries(VISIBILITY_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
                <p className="engraved mt-1.5 ml-1 text-xs">
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
                  className="peer sr-only"
                  id="file-upload"
                />
                <label
                  htmlFor="file-upload"
                  className={`btn btn-primary px-6 py-3 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-3 peer-focus-visible:outline-brass-hi ${
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
          <LoadingDots label="Loading documents" />
        ) : documents.length === 0 ? (
          <div className="surface p-10 text-center">
            <div className="well mx-auto mb-4 grid h-14 w-14 place-items-center rounded-full">
              <FileText className="h-6 w-6 text-ink-dim" />
            </div>
            <p className="embossed text-sm font-semibold">No documents yet</p>
            <p className="engraved mt-1 text-xs">
              {isAdmin
                ? "Upload one above to start answering questions from it."
                : "An administrator needs to upload documents before you can ask about them."}
            </p>
          </div>
        ) : (
          <div className="surface p-3">
            <div className="well overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead>
                    <tr className="border-b-2 border-line-strong bg-violet/15">
                      <th className="label px-4 py-3">File</th>
                      <th className="label px-4 py-3">Status</th>
                      <th className="label px-4 py-3">Visibility</th>
                      <th className="label px-4 py-3">Indexed</th>
                      <th className="label px-4 py-3">Uploaded by</th>
                      {isAdmin && <th className="px-4 py-3" />}
                    </tr>
                  </thead>
                  <tbody>
                    {documents.map((document) => (
                      <tr
                        key={document.id}
                        className="border-b border-line transition-colors last:border-0 hover:bg-white/[0.03]"
                      >
                        <td className="px-4 py-3">
                          <p className="font-semibold text-ink">
                            {document.fileName}
                          </p>
                          <p className="engraved text-xs">
                            {formatBytes(document.sizeBytes)}
                            {/* Only shown when the format actually has pages.
                                Loose `!= null` covers both null and undefined:
                                the API omits pageCount entirely for DOCX and
                                TXT. */}
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
                        <td className="px-4 py-3 text-xs text-ink-muted">
                          {VISIBILITY_LABELS[document.visibility]}
                        </td>
                        <td className="readout px-4 py-3 text-xs">
                          {document.status === "PROCESSED"
                            ? `${document.chunkCount} passage${
                                document.chunkCount === 1 ? "" : "s"
                              }`
                            : "—"}
                        </td>
                        <td className="px-4 py-3 text-xs text-ink-muted">
                          {document.uploadedByName ?? "—"}
                        </td>
                        {isAdmin && (
                          <td className="px-4 py-3 text-right">
                            <button
                              onClick={() => handleDelete(document)}
                              aria-label={`Delete ${document.fileName}`}
                              className="btn btn-sm btn-icon btn-danger"
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
  const config: Record<
    DocumentStatus,
    { color: "green" | "red" | "amber"; label: string; pulse: boolean }
  > = {
    PROCESSED: { color: "green", label: "Indexed", pulse: false },
    FAILED: { color: "red", label: "Failed", pulse: false },
    PROCESSING: { color: "amber", label: "Indexing", pulse: true },
    PENDING: { color: "amber", label: "Queued", pulse: true },
  };
  const { color, label, pulse } = config[status];

  return (
    <span
      title={status === "FAILED" ? reason : undefined}
      className="inline-flex items-center gap-2 text-xs font-semibold text-ink-muted"
    >
      <Led color={color} pulse={pulse} />
      {label}
    </span>
  );
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
