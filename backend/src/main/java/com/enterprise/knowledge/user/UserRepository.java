package com.enterprise.knowledge.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Every lookup that can reach a user record other than by login takes a tenant
 * id. {@link #findByEmail} is the single exception: it backs authentication,
 * which by definition runs before a tenant is known, and the tenant is then
 * taken from the row it returns.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<User> findByTenantId(UUID tenantId, Pageable pageable);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndRole(UUID tenantId, Role role);
}
