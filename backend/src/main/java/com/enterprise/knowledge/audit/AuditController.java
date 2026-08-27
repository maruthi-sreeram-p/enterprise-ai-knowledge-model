package com.enterprise.knowledge.audit;

import com.enterprise.knowledge.audit.dto.AuditLogResponse;
import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.user.User;
import com.enterprise.knowledge.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only access to the audit trail. Administrators only, and only for their
 * own organisation.
 */
@RestController
@RequestMapping("/api/audit-logs")
@Tag(name = "Audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditController(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    @Operation(summary = "List audit records for the caller's organization")
    public ResponseEntity<Page<AuditLogResponse>> listAuditLogs(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) AuditAction action,
            @PageableDefault(size = 50) Pageable pageable) {

        Page<AuditLog> logs = action == null
                ? auditLogRepository.findByTenantIdOrderByCreatedAtDesc(principal.getTenantId(), pageable)
                : auditLogRepository.findByTenantIdAndActionOrderByCreatedAtDesc(
                        principal.getTenantId(), action, pageable);

        Map<UUID, String> userNames = loadUserNames(logs.getContent());
        return ResponseEntity.ok(logs.map(entry ->
                AuditLogResponse.from(entry, userNames.get(entry.getUserId()))));
    }

    /**
     * Resolves display names in one batched query. Names are looked up rather than
     * denormalised onto the audit row so that a renamed user is shown correctly;
     * a deleted user simply resolves to null, and the record itself survives.
     */
    private Map<UUID, String> loadUserNames(List<AuditLog> logs) {
        Set<UUID> userIds = logs.stream()
                .map(AuditLog::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName, (first, second) -> first));
    }
}
