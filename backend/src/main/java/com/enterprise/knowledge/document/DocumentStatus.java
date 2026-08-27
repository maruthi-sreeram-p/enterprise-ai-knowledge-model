package com.enterprise.knowledge.document;

/**
 * Where a document is in the ingestion pipeline. A document only contributes to
 * answers once it reaches {@link #PROCESSED}.
 */
public enum DocumentStatus {

    /** Stored on disk, not yet extracted or embedded. */
    PENDING,

    /** Text extraction and embedding are in progress. */
    PROCESSING,

    /** Chunks are embedded and searchable. */
    PROCESSED,

    /** Ingestion failed; see the document's failure reason. */
    FAILED
}
