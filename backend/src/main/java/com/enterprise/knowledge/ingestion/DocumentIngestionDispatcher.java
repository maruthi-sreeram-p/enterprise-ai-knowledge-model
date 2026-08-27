package com.enterprise.knowledge.ingestion;

import java.util.UUID;

/**
 * Hands a freshly uploaded document to the ingestion pipeline.
 *
 * Two implementations exist and are selected by {@code app.ingestion.mode}. The
 * upload path does not know or care which one is active, which is what allowed
 * the whole system to be built and tested without Kafka before Kafka was added.
 */
public interface DocumentIngestionDispatcher {

    void dispatch(UUID documentId, UUID tenantId);
}
