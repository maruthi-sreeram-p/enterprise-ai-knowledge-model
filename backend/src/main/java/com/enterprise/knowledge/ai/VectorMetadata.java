package com.enterprise.knowledge.ai;

/**
 * The metadata keys written onto every embedded chunk.
 *
 * These are not merely descriptive. {@link #TENANT_ID} and {@link #VISIBILITY}
 * are the two fields the retrieval filter is built from, which is what allows
 * authorisation to be enforced by the database during the vector search rather
 * than by application code after it.
 */
public final class VectorMetadata {

    /** Owning organisation. Present on every chunk without exception. */
    public static final String TENANT_ID = "tenantId";

    /** Source document, used both for citations and for deleting a document's chunks. */
    public static final String DOCUMENT_ID = "documentId";

    /** Original file name, shown to the user in citations. */
    public static final String FILE_NAME = "fileName";

    /**
     * One-based page number. The key is omitted entirely for formats without
     * pages rather than written as null, so that "no page" is distinguishable
     * from "page unknown".
     */
    public static final String PAGE_NUMBER = "pageNumber";

    /** Copy of the document's visibility, so the filter can apply role limits. */
    public static final String VISIBILITY = "visibility";

    /** Position of this chunk within its document, useful when debugging retrieval. */
    public static final String CHUNK_INDEX = "chunkIndex";

    private VectorMetadata() {
    }
}
