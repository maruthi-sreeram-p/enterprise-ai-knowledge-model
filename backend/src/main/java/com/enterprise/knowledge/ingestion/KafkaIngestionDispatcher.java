package com.enterprise.knowledge.ingestion;

import com.enterprise.knowledge.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes an ingestion event to Kafka.
 *
 * The tenant id is used as the message key. Kafka guarantees ordering within a
 * partition, so keying by tenant means one organisation's uploads are processed
 * in the order they were submitted, while different organisations spread across
 * partitions and are processed in parallel.
 */
@Component
@ConditionalOnProperty(name = "app.ingestion.mode", havingValue = "KAFKA")
public class KafkaIngestionDispatcher implements DocumentIngestionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(KafkaIngestionDispatcher.class);

    private final KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate;
    private final String topic;

    public KafkaIngestionDispatcher(KafkaTemplate<String, DocumentUploadedEvent> kafkaTemplate,
                                    AppProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = properties.getIngestion().getTopic();
    }

    @Override
    public void dispatch(UUID documentId, UUID tenantId) {
        DocumentUploadedEvent event = new DocumentUploadedEvent(documentId, tenantId);

        kafkaTemplate.send(topic, tenantId.toString(), event)
                .whenComplete((result, failure) -> {
                    if (failure != null) {
                        // The document row already exists and stays PENDING, so the
                        // upload is not lost. It can be re-queued by re-uploading.
                        log.error("Could not publish ingestion event for document {}", documentId, failure);
                    } else {
                        log.debug("Published ingestion event for document {} to {}", documentId, topic);
                    }
                });
    }
}
