package com.enterprise.knowledge.ai;

import com.enterprise.knowledge.common.exception.AiServiceException;
import com.enterprise.knowledge.common.exception.DocumentProcessingException;
import com.enterprise.knowledge.document.Document;
import com.enterprise.knowledge.document.DocumentRepository;
import com.enterprise.knowledge.document.DocumentStorageService;
import com.enterprise.knowledge.document.extraction.DocumentTextExtractor;
import com.enterprise.knowledge.document.extraction.ExtractedPage;
import com.enterprise.knowledge.document.extraction.ExtractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a stored file into searchable, tenant-tagged chunks.
 *
 * The pipeline is: extract text per page, split each page into chunks, tag every
 * chunk with the owning tenant and the document's visibility, then embed and
 * store. Splitting per page rather than across the whole document is what keeps
 * a chunk attached to the page it came from, which is what makes a real page
 * citation possible later.
 *
 * Nothing here runs inside a database transaction. Embedding is a network call
 * to Gemini that can take seconds per batch, and holding a connection open for
 * that long would exhaust the pool under any real load. Status changes are
 * written as their own short transactions instead.
 *
 * Note on naming: {@code Document} in this file is the JPA entity. Spring AI has
 * a type of the same name for an embeddable chunk, which is written out in full
 * wherever it appears.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final DocumentRepository documentRepository;
    private final DocumentStorageService storageService;
    private final DocumentTextExtractor textExtractor;
    private final TokenTextSplitter splitter;
    private final VectorStore vectorStore;
    private final ThrottledVectorWriter vectorWriter;

    public DocumentIngestionService(DocumentRepository documentRepository,
                                    DocumentStorageService storageService,
                                    DocumentTextExtractor textExtractor,
                                    TokenTextSplitter splitter,
                                    VectorStore vectorStore,
                                    ThrottledVectorWriter vectorWriter) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
        this.textExtractor = textExtractor;
        this.splitter = splitter;
        this.vectorStore = vectorStore;
        this.vectorWriter = vectorWriter;
    }

    /**
     * Runs the full pipeline for one document and records the outcome on the
     * document row.
     *
     * This never throws. It is called both from an upload request and from a
     * Kafka consumer, and in both cases a failure belongs on the document as
     * FAILED with a reason rather than propagated: the upload itself succeeded,
     * and the user needs to be shown what went wrong with processing.
     */
    public void ingest(UUID documentId, UUID tenantId) {
        Document document = documentRepository.findByIdAndTenantId(documentId, tenantId).orElse(null);

        if (document == null) {
            log.warn("Ingestion requested for document {} which is not in tenant {}", documentId, tenantId);
            return;
        }

        document.markProcessing();
        documentRepository.save(document);

        try {
            IngestionOutcome outcome = process(document);
            document.markProcessed(outcome.chunkCount(), outcome.pageCount());
            documentRepository.save(document);

            log.info("Indexed document {} as {} chunks", documentId, outcome.chunkCount());

        } catch (DocumentProcessingException ex) {
            // A problem with the file itself. The message is written for the user
            // and is safe to show them.
            log.warn("Could not process document {}: {}", documentId, ex.getMessage());
            failDocument(document, ex.getMessage());

        } catch (Exception ex) {
            // A problem with an upstream service. A provider message can carry
            // request details, so a generic reason is recorded and the detail
            // stays in the log.
            log.error("Unexpected failure while indexing document {}", documentId, ex);
            failDocument(document, "Indexing failed because of an internal error. Try uploading again.");
        }
    }

    private IngestionOutcome process(Document document) {
        Path file = storageService.resolve(document.getStoragePath());

        ExtractionResult extraction = textExtractor.extract(file, document.getFileName());

        if (extraction.isEmpty()) {
            throw new DocumentProcessingException("No readable text was found in this document.");
        }

        List<org.springframework.ai.document.Document> chunks = buildChunks(document, extraction);

        if (chunks.isEmpty()) {
            throw new DocumentProcessingException(
                    "The document contained text, but no passage was long enough to index.");
        }

        // Paced and retried on provider rate limits; see ThrottledVectorWriter.
        vectorWriter.addAll(chunks);
        return new IngestionOutcome(chunks.size(), extraction.pageCount());
    }

    private void failDocument(Document document, String reason) {
        document.markFailed(reason);
        documentRepository.save(document);
        // Remove any chunks written before the failure, so a partially indexed
        // document cannot contribute fragments to future answers.
        deleteChunks(document.getId(), document.getTenantId());
    }

    /**
     * Splits each page and attaches the metadata that retrieval later filters on.
     */
    private List<org.springframework.ai.document.Document> buildChunks(Document document,
                                                                       ExtractionResult extraction) {
        List<org.springframework.ai.document.Document> allChunks = new ArrayList<>();
        int chunkIndex = 0;

        for (ExtractedPage page : extraction.pages()) {
            if (page.text() == null || page.text().isBlank()) {
                continue;
            }

            Map<String, Object> metadata = baseMetadata(document);
            // Written only when the format genuinely has pages. An absent key means
            // "this format has no pages", which the citation layer reports honestly
            // instead of guessing a number.
            if (page.pageNumber() != null) {
                metadata.put(VectorMetadata.PAGE_NUMBER, page.pageNumber());
            }

            org.springframework.ai.document.Document pageDocument =
                    new org.springframework.ai.document.Document(page.text(), metadata);

            for (org.springframework.ai.document.Document chunk : splitter.split(pageDocument)) {
                // The splitter copies the page metadata onto each chunk, so only the
                // per-chunk position needs adding here.
                chunk.getMetadata().put(VectorMetadata.CHUNK_INDEX, chunkIndex);
                allChunks.add(chunk);
                chunkIndex++;
            }
        }

        return allChunks;
    }

    private Map<String, Object> baseMetadata(Document document) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put(VectorMetadata.TENANT_ID, document.getTenantId().toString());
        metadata.put(VectorMetadata.DOCUMENT_ID, document.getId().toString());
        metadata.put(VectorMetadata.FILE_NAME, document.getFileName());
        metadata.put(VectorMetadata.VISIBILITY, document.getVisibility().name());
        return metadata;
    }

    /**
     * Removes every chunk belonging to a document.
     *
     * The delete is filtered on tenant as well as document id. The document id
     * alone would be sufficient because it is a UUID, but including the tenant
     * keeps the rule "no statement touches vector rows without naming a tenant"
     * true without exception, which is a far easier property to verify.
     */
    public void deleteChunks(UUID documentId, UUID tenantId) {
        try {
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            Filter.Expression expression = builder.and(
                    builder.eq(VectorMetadata.TENANT_ID, tenantId.toString()),
                    builder.eq(VectorMetadata.DOCUMENT_ID, documentId.toString())
            ).build();

            vectorStore.delete(expression);
            log.debug("Deleted vector chunks for document {}", documentId);

        } catch (Exception ex) {
            // Logged rather than rethrown: the caller is deleting the document, and
            // failing that because of leftover vector rows would leave the user
            // unable to remove the document at all.
            log.error("Could not delete vector chunks for document {}", documentId, ex);
        }
    }

    /**
     * @param chunkCount number of chunks embedded and stored
     * @param pageCount  source page count, or null for formats without pages
     */
    private record IngestionOutcome(int chunkCount, Integer pageCount) {
    }
}
