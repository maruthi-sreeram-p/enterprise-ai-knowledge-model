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
    <div className="mt-6 border-t-2 border-dashed border-line-strong pt-4">
      <p className="eyebrow mb-4 !bg-lime">Sources</p>

      <ul className="space-y-3">
        {cited.map((source) => (
          <SourceRow key={source.reference} source={source} emphasised />
        ))}
      </ul>

      {consulted.length > 0 && (
        <details className="group mt-3">
          <summary className="engraved cursor-pointer text-xs transition-colors hover:text-ink">
            {consulted.length} more passage
            {consulted.length === 1 ? "" : "s"} searched but not cited
          </summary>
          <ul className="mt-2 space-y-2">
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
      className={`flex items-start gap-3 rounded-xl px-3 py-2.5 text-xs ${
        emphasised ? "plate" : "border-2 border-dashed border-line"
      }`}
    >
      <span className="mt-0.5 grid h-6 min-w-6 shrink-0 place-items-center rounded-full bg-brass px-1 text-[11px] font-extrabold text-[#14081f]">
        {source.reference}
      </span>
      <FileText className="mt-0.5 h-3.5 w-3.5 shrink-0 text-ink-dim" />
      <span className="min-w-0">
        <span className="font-semibold text-ink">{source.fileName}</span>
        {/* Loose `!= null` on purpose: it catches both null and undefined. The
            API omits pageNumber entirely when a format has no pages, so a strict
            `!== null` check would treat "absent" as "present" and render an
            empty page number — which is exactly the fabricated citation this
            feature exists to prevent. */}
        {source.pageNumber != null ? (
          <span className="text-ink-muted"> · page {source.pageNumber}</span>
        ) : (
          <span
            className="text-ink-dim"
            title="This file format has no page numbers, so none is reported."
          >
            {" "}
            · page not applicable
          </span>
        )}
        {source.excerpt && (
          <span className="mt-1 block leading-relaxed text-ink-dim">
            {source.excerpt}
          </span>
        )}
      </span>
    </li>
  );
}
