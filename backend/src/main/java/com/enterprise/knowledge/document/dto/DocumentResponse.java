package com.enterprise.knowledge.document.dto;

import com.enterprise.knowledge.document.Document;
import com.enterprise.knowledge.document.DocumentStatus;
import com.enterprise.knowledge.document.DocumentVisibility;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * @param pageCount     null for DOCX and TXT, which have no pages
 * @param failureReason present only when the status is FAILED
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DocumentResponse(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        DocumentVisibility visibility,
        int chunkCount,
        Integer pageCount,
        String failureReason,
        UUID uploadedBy,
        String uploadedByName,
        Instant createdAt,
        Instant processedAt
) {

    public static DocumentResponse from(Document document, String uploaderName) {
        return new DocumentResponse(
                document.getId(),
                document.getFileName(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getVisibility(),
                document.getChunkCount(),
                document.getPageCount(),
                document.getFailureReason(),
                document.getUploadedBy(),
                uploaderName,
                document.getCreatedAt(),
                document.getProcessedAt());
    }
}
