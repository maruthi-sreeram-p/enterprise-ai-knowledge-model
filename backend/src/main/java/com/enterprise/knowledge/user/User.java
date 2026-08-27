package com.enterprise.knowledge.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A person belonging to one tenant.
 *
 * The tenant reference is stored as a plain {@code UUID} rather than a JPA
 * association. Tenant scoping is applied as an explicit predicate on every
 * repository method, and keeping the column plain makes it impossible to
 * accidentally navigate into another tenant through a lazy relationship.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    /** Stored lower-cased and unique across the whole system, since it is the login identifier. */
    @Column(name = "email", nullable = false, unique = true, length = 320)
    private String email;

    /** BCrypt hash. The plaintext password is never stored or logged. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // Required by JPA.
    }

    public User(UUID tenantId, String name, String email, String passwordHash, Role role) {
        this.id = UUID.randomUUID();
        this.tenantId = tenantId;
        this.name = name;
        this.email = normaliseEmail(email);
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = true;
        this.createdAt = Instant.now();
    }

    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
