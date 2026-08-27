package com.enterprise.knowledge.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Note that there is no plain {@code findById} in use anywhere in the
 * application. Reading a document always goes through
 * {@link #findByIdAndTenantId}, so a well-formed identifier belonging to
 * another tenant simply produces an empty result.
 */
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Optional<Document> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Document> findByTenantId(UUID tenantId, Pageable pageable);

    Page<Document> findByTenantIdAndVisibilityIn(UUID tenantId,
                                                 Collection<DocumentVisibility> visibilities,
                                                 Pageable pageable);

    List<Document> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, DocumentStatus status);
}
