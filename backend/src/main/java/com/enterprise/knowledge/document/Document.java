package com.enterprise.knowledge.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata for an uploaded file. The file bytes themselves live on disk under
 * the configured storage root; the embedded chunks live in the pgvector table.
 * This row is the link between the two and the authority on who may read them.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** The name the user uploaded, used verbatim in answer citations. */
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    /** Path relative to the configured storage root. */
    @Column(name = "storage_path", nullable = false, length = 1000)
    private String storagePath;

    @Column(name = "content_type", nullable = false, length = 200)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "uploaded_by", nullable = false, updatable = false)
    private UUID uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private DocumentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 30)
    private DocumentVisibility visibility;

    /** Number of embedded chunks. Zero until ingestion completes. */
    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    /**
     * Page count, populated only for formats that genuinely have pages. DOCX and
     * TXT leave this null rather than reporting a fabricated number.
     */
    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected Document() {
        // Required by JPA.
    }

    public Document(UUID tenantId, String fileName, String storagePath, String contentType,
                    long sizeBytes, UUID uploadedBy, DocumentVisibility visibility) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.fileName = fileName;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
        this.visibility = visibility;
        this.status = DocumentStatus.PENDING;
        this.chunkCount = 0;
        this.createdAt = Instant.now();
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
        this.failureReason = null;
    }

    public void markProcessed(int chunkCount, Integer pageCount) {
        this.status = DocumentStatus.PROCESSED;
        this.chunkCount = chunkCount;
        this.pageCount = pageCount;
        this.processedAt = Instant.now();
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = DocumentStatus.FAILED;
        this.failureReason = truncate(reason, 1000);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getFileName() {
        return fileName;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public DocumentVisibility getVisibility() {
        return visibility;
    }

    public void setVisibility(DocumentVisibility visibility) {
        this.visibility = visibility;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
