package com.enterprise.knowledge.ingestion;

import com.enterprise.knowledge.ai.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes ingestion events and runs the pipeline.
 *
 * {@link DocumentIngestionService#ingest} handles its own failures and records
 * them on the document, so this listener does not rethrow. Letting an exception
 * escape would make Kafka redeliver the message, and a document that fails to
 * parse would fail identically every time, blocking the partition behind it.
 */
@Component
@ConditionalOnProperty(name = "app.ingestion.mode", havingValue = "KAFKA")
public class DocumentIngestionConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionConsumer.class);

    private final DocumentIngestionService ingestionService;

    public DocumentIngestionConsumer(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @KafkaListener(topics = "${app.ingestion.topic}", groupId = "knowledge-base-ingestion")
    public void onDocumentUploaded(DocumentUploadedEvent event) {
        log.debug("Received ingestion event for document {}", event.documentId());
        ingestionService.ingest(event.documentId(), event.tenantId());
    }
}
