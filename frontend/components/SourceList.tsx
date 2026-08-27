"use client";

import { FileText } from "lucide-react";
import type { SourceCitation } from "../lib/types";

/**
 * Renders the passages an answer was grounded in.
 *
 * Every value shown comes from the metadata of a chunk that was actually
 * retrieved. When a source has no page number it says so, because the format it
 * came from has no pages. Inventing one would make the citation useless in
 * exactly the situation where it matters most.
 */
export default function SourceList({ sources }: { sources: SourceCitation[] }) {
  if (!sources || sources.length === 0) {
    return null;
  }

  const cited = sources.filter((source) => source.cited);
  const consulted = sources.filter((source) => !source.cited);

  return (
    <div className="mt-4 border-t border-slate-700/60 pt-3">
      <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-slate-500">
        Sources
      </p>

      <ul className="space-y-1.5">
        {cited.map((source) => (
          <SourceRow key={source.reference} source={source} emphasised />
        ))}
      </ul>

      {consulted.length > 0 && (
        <details className="mt-2 group">
          <summary className="cursor-pointer text-xs text-slate-500 transition-colors hover:text-slate-400">
            {consulted.length} more passage
            {consulted.length === 1 ? "" : "s"} searched but not cited
          </summary>
          <ul className="mt-1.5 space-y-1.5">
            {consulted.map((source) => (
              <SourceRow key={source.reference} source={source} />
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}

function SourceRow({
  source,
  emphasised = false,
}: {
  source: SourceCitation;
  emphasised?: boolean;
}) {
  return (
    <li
      className={`flex items-start gap-2 rounded-lg px-2 py-1.5 text-xs ${
        emphasised ? "bg-slate-800/60" : ""
      }`}
    >
      <span className="mt-0.5 shrink-0 font-mono text-[#60a5fa]">
        [{source.reference}]
      </span>
      <FileText className="mt-0.5 h-3.5 w-3.5 shrink-0 text-slate-500" />
      <span className="min-w-0">
        <span className="font-medium text-slate-300">{source.fileName}</span>
        {/* Loose `!= null` on purpose: it catches both null and undefined. The
            API omits pageNumber entirely when a format has no pages, so a strict
            `!== null` check would treat "absent" as "present" and render an
            empty page number — which is exactly the fabricated citation this
            feature exists to prevent. */}
        {source.pageNumber != null ? (
          <span className="text-slate-500"> · page {source.pageNumber}</span>
        ) : (
          <span
            className="text-slate-600"
            title="This file format has no page numbers, so none is reported."
          >
            {" "}
            · page not applicable
          </span>
        )}
        {source.excerpt && (
          <span className="mt-0.5 block leading-relaxed text-slate-500">
            {source.excerpt}
          </span>
        )}
      </span>
    </li>
  );
}
