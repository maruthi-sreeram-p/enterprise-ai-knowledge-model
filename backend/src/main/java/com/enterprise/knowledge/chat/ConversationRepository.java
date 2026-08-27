package com.enterprise.knowledge.chat;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Conversations are scoped by tenant and by owning user, so the lookups take
 * both. A conversation belongs to the person who started it.
 */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

    Page<Conversation> findByTenantIdAndUserIdOrderByUpdatedAtDesc(UUID tenantId, UUID userId,
                                                                   Pageable pageable);

    long countByTenantIdAndUserId(UUID tenantId, UUID userId);
}
