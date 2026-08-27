package com.enterprise.knowledge.document;

import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.document.dto.DocumentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * Uploads a document. Returns 202 rather than 201 because indexing has been
     * accepted but not finished; the document's status field reports its progress.
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Upload a PDF, DOCX, or TXT document for indexing")
    public ResponseEntity<DocumentResponse> upload(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "visibility", defaultValue = "ALL_EMPLOYEES")
            DocumentVisibility visibility) {

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(documentService.upload(principal, file, visibility));
    }

    @GetMapping
    @Operation(summary = "List documents the caller is allowed to read")
    public ResponseEntity<Page<DocumentResponse>> listDocuments(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(documentService.listDocuments(principal, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a single document's metadata")
    public ResponseEntity<DocumentResponse> getDocument(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(documentService.getDocument(principal, id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a document, its stored file, and its embeddings")
    public ResponseEntity<Void> deleteDocument(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id) {
        documentService.deleteDocument(principal, id);
        return ResponseEntity.noContent().build();
    }
}
