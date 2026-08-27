package com.enterprise.knowledge.auth;

import com.enterprise.knowledge.audit.AuditAction;
import com.enterprise.knowledge.audit.AuditService;
import com.enterprise.knowledge.auth.dto.AuthResponse;
import com.enterprise.knowledge.auth.dto.LoginRequest;
import com.enterprise.knowledge.auth.dto.RegisterRequest;
import com.enterprise.knowledge.common.exception.ConflictException;
import com.enterprise.knowledge.common.exception.ResourceNotFoundException;
import com.enterprise.knowledge.tenant.Tenant;
import com.enterprise.knowledge.tenant.TenantRepository;
import com.enterprise.knowledge.user.Role;
import com.enterprise.knowledge.user.User;
import com.enterprise.knowledge.user.UserRepository;
import com.enterprise.knowledge.user.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    /**
     * A valid BCrypt hash of a value nobody knows. When an email does not exist,
     * the password is verified against this instead of returning immediately, so
     * that a wrong password and an unknown address take a comparable amount of
     * time. Without it, response latency alone would reveal which addresses are
     * registered.
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static final int MAX_SLUG_LENGTH = 90;

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public AuthService(TenantRepository tenantRepository,
                       UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuditService auditService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    /**
     * Creates an organisation and its first administrator in one step.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = User.normaliseEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        Tenant tenant = tenantRepository.save(
                new Tenant(request.organizationName().strip(), generateUniqueSlug(request.organizationName())));

        User admin = userRepository.save(new User(
                tenant.getId(),
                request.name().strip(),
                email,
                passwordEncoder.encode(request.password()),
                Role.ADMIN));

        log.info("Registered organisation {} with administrator {}", tenant.getId(), admin.getId());

        auditService.record(tenant.getId(), admin.getId(), AuditAction.USER_REGISTERED,
                admin.getId(), Map.of("role", Role.ADMIN.name(), "firstAdministrator", true));

        return buildResponse(admin, tenant);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = User.normaliseEmail(request.email());
        Optional<User> candidate = userRepository.findByEmail(email);

        if (candidate.isEmpty()) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            auditService.record(null, null, AuditAction.LOGIN_FAILED, null,
                    Map.of("reason", "UNKNOWN_EMAIL"));
            throw new BadCredentialsException("Invalid email or password.");
        }

        User user = candidate.get();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.record(user.getTenantId(), user.getId(), AuditAction.LOGIN_FAILED, null,
                    Map.of("reason", "BAD_PASSWORD"));
            throw new BadCredentialsException("Invalid email or password.");
        }

        if (!user.isEnabled()) {
            auditService.record(user.getTenantId(), user.getId(), AuditAction.LOGIN_FAILED, null,
                    Map.of("reason", "ACCOUNT_DISABLED"));
            // Reported as invalid credentials so that a disabled account is not
            // distinguishable from a wrong password.
            throw new BadCredentialsException("Invalid email or password.");
        }

        Tenant tenant = tenantRepository.findById(user.getTenantId())
                .orElseThrow(() -> ResourceNotFoundException.of("Organization", user.getTenantId()));

        auditService.record(user.getTenantId(), user.getId(), AuditAction.LOGIN, user.getId(), null);

        return buildResponse(user, tenant);
    }

    private AuthResponse buildResponse(User user, Tenant tenant) {
        JwtService.IssuedToken token = jwtService.issueToken(user);
        return AuthResponse.of(token.token(), token.expiresAt(), token.expiresInSeconds(),
                UserResponse.from(user), tenant.getName());
    }

    /**
     * Turns an organisation name into a URL-safe slug, appending a counter when
     * the natural slug is already taken.
     */
    private String generateUniqueSlug(String organizationName) {
        String base = organizationName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");

        if (base.isEmpty()) {
            base = "org";
        }
        if (base.length() > MAX_SLUG_LENGTH) {
            base = base.substring(0, MAX_SLUG_LENGTH);
        }

        String candidate = base;
        int suffix = 2;
        while (tenantRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }
}
