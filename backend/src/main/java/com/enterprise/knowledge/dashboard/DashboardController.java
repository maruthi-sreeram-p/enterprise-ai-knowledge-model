package com.enterprise.knowledge.dashboard;

import com.enterprise.knowledge.audit.AuditAction;
import com.enterprise.knowledge.audit.AuditLogRepository;
import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.chat.ConversationRepository;
import com.enterprise.knowledge.document.DocumentService;
import com.enterprise.knowledge.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Counts for the dashboard. Every figure is a real count from the caller's own
 * tenant, computed at request time.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DocumentService documentService;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final AuditLogRepository auditLogRepository;

    public DashboardController(DocumentService documentService,
                               UserRepository userRepository,
                               ConversationRepository conversationRepository,
                               AuditLogRepository auditLogRepository) {
        this.documentService = documentService;
        this.userRepository = userRepository;
        this.conversationRepository = conversationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping("/summary")
    @Transactional(readOnly = true)
    @Operation(summary = "Counts for the caller's organization")
    public ResponseEntity<DashboardSummary> summary(@AuthenticationPrincipal AuthenticatedUser principal) {
        Map<String, Long> documents = documentService.statistics(principal);

        return ResponseEntity.ok(new DashboardSummary(
                documents.getOrDefault("total", 0L),
                documents.getOrDefault("processed", 0L),
                documents.getOrDefault("failed", 0L),
                userRepository.countByTenantId(principal.getTenantId()),
                conversationRepository.countByTenantIdAndUserId(
                        principal.getTenantId(), principal.getId()),
                auditLogRepository.countByTenantIdAndAction(
                        principal.getTenantId(), AuditAction.AI_QUERY)));
    }

    /**
     * @param myConversations conversation count for the calling user only, since
     *                        conversations are private to the person who started them
     */
    public record DashboardSummary(
            long totalDocuments,
            long processedDocuments,
            long failedDocuments,
            long totalUsers,
            long myConversations,
            long totalAiQueries
    ) {
    }
}
