package com.enterprise.knowledge.ingestion;

import com.enterprise.knowledge.ai.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Runs ingestion on a bounded in-process thread pool.
 *
 * This is the default and needs no broker, which keeps the project runnable with
 * just PostgreSQL. Its limitation is honest and worth stating: the queue lives in
 * memory, so anything still queued when the process stops is lost and its
 * document stays PENDING. That is the specific problem the Kafka mode solves.
 */
@Component
@ConditionalOnProperty(name = "app.ingestion.mode", havingValue = "IN_PROCESS", matchIfMissing = true)
public class InProcessIngestionDispatcher implements DocumentIngestionDispatcher {

    private static final Logger log = LoggerFactory.getLogger(InProcessIngestionDispatcher.class);

    private final TaskExecutor ingestionExecutor;
    private final DocumentIngestionService ingestionService;

    public InProcessIngestionDispatcher(TaskExecutor documentIngestionExecutor,
                                        DocumentIngestionService ingestionService) {
        this.ingestionExecutor = documentIngestionExecutor;
        this.ingestionService = ingestionService;
    }

    @Override
    public void dispatch(UUID documentId, UUID tenantId) {
        log.debug("Queueing document {} for in-process ingestion", documentId);
        ingestionExecutor.execute(() -> ingestionService.ingest(documentId, tenantId));
    }
}
