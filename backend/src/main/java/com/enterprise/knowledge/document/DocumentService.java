package com.enterprise.knowledge.document;

import com.enterprise.knowledge.ai.AnswerCache;
import com.enterprise.knowledge.ai.DocumentIngestionService;
import com.enterprise.knowledge.audit.AuditAction;
import com.enterprise.knowledge.audit.AuditService;
import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.common.exception.BadRequestException;
import com.enterprise.knowledge.common.exception.ForbiddenException;
import com.enterprise.knowledge.common.exception.ResourceNotFoundException;
import com.enterprise.knowledge.document.dto.DocumentResponse;
import com.enterprise.knowledge.document.extraction.DocumentTextExtractor;
import com.enterprise.knowledge.ingestion.DocumentIngestionDispatcher;
import com.enterprise.knowledge.user.User;
import com.enterprise.knowledge.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Document lifecycle: upload, list, read, delete.
 *
 * Every lookup is scoped by the acting user's tenant, and reads are additionally
 * checked against the document's visibility level.
 *
 * Note that {@link #upload} and {@link #deleteDocument} are deliberately not
 * annotated as transactional. Both need their database write committed before
 * the work that follows it, so each relies on the repository's own per-call
 * transaction rather than wrapping the whole method in one.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final DocumentStorageService storageService;
    private final DocumentTextExtractor textExtractor;
    private final DocumentIngestionDispatcher ingestionDispatcher;
    private final DocumentIngestionService ingestionService;
    private final AnswerCache answerCache;
    private final AuditService auditService;

    public DocumentService(DocumentRepository documentRepository,
                           UserRepository userRepository,
                           DocumentStorageService storageService,
                           DocumentTextExtractor textExtractor,
                           DocumentIngestionDispatcher ingestionDispatcher,
                           DocumentIngestionService ingestionService,
                           AnswerCache answerCache,
                           AuditService auditService) {
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
        this.textExtractor = textExtractor;
        this.ingestionDispatcher = ingestionDispatcher;
        this.ingestionService = ingestionService;
        this.answerCache = answerCache;
        this.auditService = auditService;
    }

    /**
     * Validates and stores an upload, then hands it to ingestion.
     *
     * The document row is committed before ingestion is dispatched. That ordering
     * matters in two ways: the client sees the file immediately with a PENDING
     * status rather than waiting for embedding, and an asynchronous consumer
     * cannot pick up an event for a row its own connection cannot yet see.
     */
    public DocumentResponse upload(AuthenticatedUser actor, MultipartFile file,
                                   DocumentVisibility visibility) {

        String originalName = sanitizeFileName(file.getOriginalFilename());

        if (!textExtractor.supports(originalName)) {
            throw new BadRequestException(
                    "Unsupported file type. Supported types are: "
                            + String.join(", ", textExtractor.supportedExtensions()) + ".");
        }

        String extension = DocumentTextExtractor.extensionOf(originalName);
        UUID documentId = UUID.randomUUID();
        String storagePath = storageService.store(file, actor.getTenantId(), documentId, extension);

        // Committed by the repository's own transaction before anything below runs.
        Document saved = documentRepository.save(new Document(
                actor.getTenantId(), originalName, storagePath,
                textExtractor.contentTypeFor(originalName), file.getSize(),
                actor.getId(), visibility));

        log.info("User {} uploaded document {} ({}) to tenant {}",
                actor.getId(), saved.getId(), originalName, actor.getTenantId());

        auditService.record(actor, AuditAction.DOCUMENT_UPLOAD, saved.getId(), Map.of(
                "fileName", originalName,
                "sizeBytes", saved.getSizeBytes(),
                "visibility", visibility.name()));

        // A new document changes what a previously cached answer should say.
        answerCache.invalidateTenant(actor.getTenantId());

        ingestionDispatcher.dispatch(saved.getId(), actor.getTenantId());

        return DocumentResponse.from(saved, actor.getName());
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> listDocuments(AuthenticatedUser actor, Pageable pageable) {
        // One code path for every role. An administrator's readable set happens to
        // be all three levels, so there is no separate branch to get wrong.
        Page<Document> documents = documentRepository.findByTenantIdAndVisibilityIn(
                actor.getTenantId(), DocumentVisibility.readableBy(actor.getRole()), pageable);

        Map<UUID, String> uploaderNames = loadUploaderNames(documents.getContent());
        return documents.map(document ->
                DocumentResponse.from(document, uploaderNames.get(document.getUploadedBy())));
    }

    @Transactional(readOnly = true)
    public DocumentResponse getDocument(AuthenticatedUser actor, UUID documentId) {
        Document document = requireReadableDocument(actor, documentId);

        auditService.record(actor, AuditAction.DOCUMENT_ACCESS, documentId,
                Map.of("fileName", document.getFileName()));

        return DocumentResponse.from(document, loadUploaderName(document.getUploadedBy()));
    }

    /**
     * Deletes the metadata row, then the embedded chunks and the stored file.
     *
     * The row is removed and committed first, because that is what makes the
     * document disappear from the user's view. The chunks and blob are then
     * cleaned up on a best-effort basis: an orphaned file on disk is a smaller
     * problem than a document the user is unable to delete.
     */
    public void deleteDocument(AuthenticatedUser actor, UUID documentId) {
        Document document = documentRepository
                .findByIdAndTenantId(documentId, actor.getTenantId())
                .orElseThrow(() -> ResourceNotFoundException.of("Document", documentId));

        documentRepository.delete(document);

        ingestionService.deleteChunks(documentId, actor.getTenantId());
        storageService.delete(document.getStoragePath());
        answerCache.invalidateTenant(actor.getTenantId());

        log.info("User {} deleted document {} from tenant {}",
                actor.getId(), documentId, actor.getTenantId());

        auditService.record(actor, AuditAction.DOCUMENT_DELETE, documentId,
                Map.of("fileName", document.getFileName()));
    }

    /** Document counts for the dashboard. */
    @Transactional(readOnly = true)
    public Map<String, Long> statistics(AuthenticatedUser actor) {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", documentRepository.countByTenantId(actor.getTenantId()));
        stats.put("processed", documentRepository.countByTenantIdAndStatus(
                actor.getTenantId(), DocumentStatus.PROCESSED));
        stats.put("failed", documentRepository.countByTenantIdAndStatus(
                actor.getTenantId(), DocumentStatus.FAILED));
        return stats;
    }

    /**
     * Resolves a document the caller is allowed to read.
     *
     * A document in another tenant produces a 404, identical to one that does not
     * exist anywhere, so the API cannot be used to probe for documents elsewhere.
     * A document in the caller's own tenant that sits above their role produces a
     * 403, because within one organisation it is more useful to be told that
     * access is restricted than to be told the file does not exist.
     */
    private Document requireReadableDocument(AuthenticatedUser actor, UUID documentId) {
        Document document = documentRepository.findByIdAndTenantId(documentId, actor.getTenantId())
                .orElseThrow(() -> ResourceNotFoundException.of("Document", documentId));

        if (!document.getVisibility().isReadableBy(actor.getRole())) {
            throw new ForbiddenException(
                    "This document is restricted to " + document.getVisibility().getMinimumRole()
                            + " users and above.");
        }
        return document;
    }

    private Map<UUID, String> loadUploaderNames(List<Document> documents) {
        Set<UUID> uploaderIds = documents.stream()
                .map(Document::getUploadedBy)
                .collect(Collectors.toSet());

        if (uploaderIds.isEmpty()) {
            return Map.of();
        }
        // A single batched lookup rather than one query per row.
        return userRepository.findAllById(uploaderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (first, second) -> first));
    }

    private String loadUploaderName(UUID uploaderId) {
        return userRepository.findById(uploaderId).map(User::getName).orElse(null);
    }

    /**
     * Reduces an uploaded filename to its bare name.
     *
     * The value is used only for display and for choosing an extension; the file
     * itself is written under a generated name. Stripping any directory component
     * keeps a crafted name such as {@code ../../etc/passwd} from ever reaching
     * the filesystem layer, and keeps citations readable.
     */
    static String sanitizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new BadRequestException("The uploaded file has no name.");
        }

        String name = originalFilename.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        name = name.strip();

        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new BadRequestException("The uploaded file has an invalid name.");
        }
        if (name.length() > 500) {
            throw new BadRequestException("The file name is too long.");
        }
        return name;
    }
}
