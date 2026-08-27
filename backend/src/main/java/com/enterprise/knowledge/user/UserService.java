package com.enterprise.knowledge.user;

import com.enterprise.knowledge.audit.AuditAction;
import com.enterprise.knowledge.audit.AuditService;
import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.common.exception.BadRequestException;
import com.enterprise.knowledge.common.exception.ConflictException;
import com.enterprise.knowledge.common.exception.ResourceNotFoundException;
import com.enterprise.knowledge.user.dto.CreateUserRequest;
import com.enterprise.knowledge.user.dto.UpdateUserRequest;
import com.enterprise.knowledge.user.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * User administration within a single organisation.
 *
 * Every method takes the acting user and derives the tenant from them. No
 * method accepts a tenant id from the caller, which is what makes cross-tenant
 * access impossible rather than merely discouraged.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> listUsers(AuthenticatedUser actor, Pageable pageable) {
        return userRepository.findByTenantId(actor.getTenantId(), pageable)
                .map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(AuthenticatedUser actor, UUID userId) {
        return UserResponse.from(requireUserInTenant(actor, userId));
    }

    @Transactional
    public UserResponse createUser(AuthenticatedUser actor, CreateUserRequest request) {
        String email = User.normaliseEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        User created = userRepository.save(new User(
                actor.getTenantId(),
                request.name().strip(),
                email,
                passwordEncoder.encode(request.password()),
                request.role()));

        log.info("User {} created user {} with role {} in tenant {}",
                actor.getId(), created.getId(), created.getRole(), actor.getTenantId());

        auditService.record(actor, AuditAction.USER_REGISTERED, created.getId(),
                Map.of("role", created.getRole().name()));

        return UserResponse.from(created);
    }

    @Transactional
    public UserResponse updateUser(AuthenticatedUser actor, UUID userId, UpdateUserRequest request) {
        User user = requireUserInTenant(actor, userId);
        Map<String, Object> changes = new HashMap<>();

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().strip());
            changes.put("name", "updated");
        }

        if (request.role() != null && request.role() != user.getRole()) {
            if (user.getRole() == Role.ADMIN) {
                requireAnotherAdminRemains(actor, user, "change the role of");
            }
            changes.put("roleFrom", user.getRole().name());
            changes.put("roleTo", request.role().name());
            user.setRole(request.role());
        }

        if (request.enabled() != null && request.enabled() != user.isEnabled()) {
            if (!request.enabled()) {
                if (user.getId().equals(actor.getId())) {
                    throw new BadRequestException("You cannot disable your own account.");
                }
                if (user.getRole() == Role.ADMIN) {
                    requireAnotherAdminRemains(actor, user, "disable");
                }
            }
            user.setEnabled(request.enabled());
            changes.put("enabled", request.enabled());
        }

        if (!changes.isEmpty()) {
            auditService.record(actor, AuditAction.USER_UPDATED, user.getId(), changes);
        }

        return UserResponse.from(user);
    }

    @Transactional
    public void deleteUser(AuthenticatedUser actor, UUID userId) {
        User user = requireUserInTenant(actor, userId);

        if (user.getId().equals(actor.getId())) {
            throw new BadRequestException("You cannot delete your own account.");
        }
        if (user.getRole() == Role.ADMIN) {
            requireAnotherAdminRemains(actor, user, "delete");
        }

        userRepository.delete(user);
        log.info("User {} deleted user {} from tenant {}", actor.getId(), userId, actor.getTenantId());

        auditService.record(actor, AuditAction.USER_DELETED, userId,
                Map.of("role", user.getRole().name()));
    }

    /**
     * Resolves a user inside the acting user's tenant.
     *
     * A well-formed identifier belonging to another organisation produces the
     * same 404 as an identifier that does not exist anywhere, so the API cannot
     * be used to discover whether a given user exists elsewhere.
     */
    private User requireUserInTenant(AuthenticatedUser actor, UUID userId) {
        return userRepository.findByIdAndTenantId(userId, actor.getTenantId())
                .orElseThrow(() -> ResourceNotFoundException.of("User", userId));
    }

    /**
     * Prevents an organisation from being left with no administrator, which
     * would make it permanently unmanageable.
     */
    private void requireAnotherAdminRemains(AuthenticatedUser actor, User target, String verb) {
        long admins = userRepository.countByTenantIdAndRole(actor.getTenantId(), Role.ADMIN);
        if (admins <= 1) {
            throw new BadRequestException(
                    "You cannot " + verb + " the only administrator of this organization. "
                            + "Promote another user to administrator first.");
        }
    }
}
