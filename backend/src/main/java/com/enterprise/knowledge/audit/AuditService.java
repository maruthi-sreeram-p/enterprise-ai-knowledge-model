package com.enterprise.knowledge.audit;

import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Writes the audit trail.
 *
 * Two decisions worth noting:
 *
 * Audit rows are written in their own transaction. A failed upload that rolls
 * back should still leave evidence that the upload was attempted, and a record
 * of a failed login has no surrounding transaction to join. This uses an
 * explicit {@link TransactionTemplate} rather than {@code @Transactional},
 * because the annotation is applied by a proxy and would be silently ignored on
 * a call made from inside this same class.
 *
 * A failure to audit never fails the operation being audited. Losing an audit
 * row is bad, but rejecting a user's request because the audit table is
 * unavailable is worse, so the error is logged and swallowed.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private static final int MAX_IP_LENGTH = 45;

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public AuditService(AuditLogRepository auditLogRepository,
                        ObjectMapper objectMapper,
                        PlatformTransactionManager transactionManager) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void record(AuthenticatedUser actor, AuditAction action, UUID resourceId,
                       Map<String, Object> metadata) {
        record(actor.getTenantId(), actor.getId(), action, resourceId, metadata);
    }

    public void record(UUID tenantId, UUID userId, AuditAction action, UUID resourceId,
                       Map<String, Object> metadata) {
        String metadataJson = toJson(metadata);
        String ipAddress = currentClientIp();

        try {
            transactionTemplate.executeWithoutResult(status -> auditLogRepository.save(
                    new AuditLog(tenantId, userId, action, resourceId, metadataJson, ipAddress)));
        } catch (RuntimeException ex) {
            log.error("Failed to write audit record for action {}", action, ex);
        }
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise audit metadata; storing the record without it", ex);
            return null;
        }
    }

    /**
     * Reads the caller's address from the current request, preferring the first
     * entry of X-Forwarded-For when the service sits behind a proxy.
     *
     * That header is client-controlled and therefore only as trustworthy as the
     * proxy in front of the service. It is recorded as a diagnostic aid and is
     * never used as an authorisation input.
     */
    private String currentClientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",")[0].trim();
            return truncate(first);
        }
        return truncate(request.getRemoteAddr());
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_IP_LENGTH ? value : value.substring(0, MAX_IP_LENGTH);
    }
}
