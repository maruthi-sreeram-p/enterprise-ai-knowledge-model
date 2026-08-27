package com.enterprise.knowledge.auth;

import com.enterprise.knowledge.user.User;
import com.enterprise.knowledge.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Establishes the security context from the bearer token on each request.
 *
 * The token proves identity, but the database stays the authority on account
 * status and role. Re-reading the user row costs one indexed primary-key lookup
 * and means that disabling an account or demoting a user takes effect on the
 * next request rather than whenever the token happens to expire.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Optional<String> token = extractBearerToken(request);

        if (token.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parseToken(token.get())
                    .flatMap(this::reloadCurrentUser)
                    .ifPresent(principal -> authenticate(principal, request));
        }

        filterChain.doFilter(request, response);
    }

    private Optional<String> extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String value = header.substring(BEARER_PREFIX.length()).trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    /**
     * Rebuilds the principal from the current user row so that role and enabled
     * status are always the live values, not whatever they were when the token
     * was issued.
     */
    private Optional<AuthenticatedUser> reloadCurrentUser(AuthenticatedUser fromToken) {
        Optional<User> user = userRepository.findById(fromToken.getId());

        if (user.isEmpty()) {
            log.debug("Token references user {} which no longer exists", fromToken.getId());
            return Optional.empty();
        }
        if (!user.get().isEnabled()) {
            log.debug("Rejected token for disabled user {}", fromToken.getId());
            return Optional.empty();
        }
        // A tenant mismatch would mean the token was issued against different data
        // than the database now holds. Treat it as untrustworthy rather than
        // guessing which value is correct.
        if (!user.get().getTenantId().equals(fromToken.getTenantId())) {
            log.warn("Token tenant {} does not match stored tenant for user {}",
                    fromToken.getTenantId(), fromToken.getId());
            return Optional.empty();
        }

        return Optional.of(AuthenticatedUser.from(user.get()));
    }

    private void authenticate(AuthenticatedUser principal, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
