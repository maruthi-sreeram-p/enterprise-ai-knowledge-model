package com.enterprise.knowledge.audit.dto;

import com.enterprise.knowledge.audit.AuditAction;
import com.enterprise.knowledge.audit.AuditLog;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.UUID;

/**
 * @param metadata raw JSON string as stored. It is returned verbatim rather than
 *                 parsed, because its shape varies by action and inventing a
 *                 common structure for it would lose detail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogResponse(
        UUID id,
        UUID userId,
        String userName,
        AuditAction action,
        UUID resourceId,
        String metadata,
        String ipAddress,
        Instant createdAt
) {

    public static AuditLogResponse from(AuditLog auditLog, String userName) {
        return new AuditLogResponse(auditLog.getId(), auditLog.getUserId(), userName,
                auditLog.getAction(), auditLog.getResourceId(), auditLog.getMetadataJson(),
                auditLog.getIpAddress(), auditLog.getCreatedAt());
    }
}
