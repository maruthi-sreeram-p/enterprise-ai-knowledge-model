package com.enterprise.knowledge.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable record of a security-relevant action.
 *
 * Tenant and user are nullable because some auditable events happen before an
 * identity is established, most obviously a failed login for an address that
 * does not exist.
 *
 * The metadata column holds a small JSON object. What goes into it is chosen
 * per action rather than dumped wholesale: for an AI query it records how many
 * sources were used and which documents they came from, not the question and
 * answer text, unless the operator explicitly opts in.
 */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private AuditAction action;

    /** Identifier of the entity acted upon, when the action targets one. */
    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
        // Required by JPA.
    }

    public AuditLog(UUID tenantId, UUID userId, AuditAction action, UUID resourceId,
                    String metadataJson, String ipAddress) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.action = action;
        this.resourceId = resourceId;
        this.metadataJson = metadataJson;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public AuditAction getAction() {
        return action;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
