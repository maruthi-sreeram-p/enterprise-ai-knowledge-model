package com.enterprise.knowledge.ingestion;

import java.util.UUID;

/**
 * The Kafka message published when a document is ready to be indexed.
 *
 * It carries identifiers only, never file content. The consumer re-reads the
 * document row and the stored file, which keeps messages small and means the
 * tenant check is re-applied on the consuming side rather than trusted from the
 * message body.
 */
public record DocumentUploadedEvent(UUID documentId, UUID tenantId) {
}
