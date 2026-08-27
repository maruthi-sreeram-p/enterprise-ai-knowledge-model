package com.enterprise.knowledge.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A chat thread. Scoped to both a tenant and the user who owns it: a manager
 * cannot read a colleague's conversation, and no one can read another tenant's.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    private static final int MAX_TITLE_LENGTH = 120;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Conversation() {
        // Required by JPA.
    }

    public Conversation(UUID tenantId, UUID userId, String title) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.userId = userId;
        this.title = title;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Derives a readable thread title from the first question asked in it. */
    public static String titleFrom(String firstQuestion) {
        String cleaned = firstQuestion == null ? "" : firstQuestion.strip().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            return "New conversation";
        }
        return cleaned.length() <= MAX_TITLE_LENGTH
                ? cleaned
                : cleaned.substring(0, MAX_TITLE_LENGTH - 1) + "…";
    }

    public void touch() {
        this.updatedAt = Instant.now();
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
