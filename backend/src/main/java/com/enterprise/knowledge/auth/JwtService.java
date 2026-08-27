package com.enterprise.knowledge.auth;

import com.enterprise.knowledge.config.AppProperties;
import com.enterprise.knowledge.user.Role;
import com.enterprise.knowledge.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies the HS256 access tokens used by the API.
 *
 * The token carries the tenant id, role and email as claims so that an
 * authenticated request needs no database read to establish who is calling.
 * Because these claims are covered by the signature, a client cannot edit its
 * own tenant or role.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";

    /** HS256 requires a key of at least 256 bits. */
    private static final int MINIMUM_KEY_BYTES = 32;

    private final SecretKey signingKey;
    private final Duration tokenLifetime;
    private final String issuer;

    public JwtService(AppProperties properties) {
        this.signingKey = buildSigningKey(properties.getJwt().getSecret());
        this.tokenLifetime = Duration.ofMinutes(properties.getJwt().getExpirationMinutes());
        this.issuer = properties.getJwt().getIssuer();
    }

    /**
     * A Base64 secret is the documented form, but a sufficiently long raw string
     * is accepted too so that a local setup does not fail in a confusing way.
     * Anything shorter than 256 bits is rejected outright rather than padded.
     */
    private static SecretKey buildSigningKey(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret is not set. Provide JWT_SECRET in your environment, "
                            + "for example the output of: openssl rand -base64 48");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(configuredSecret);
        } catch (IllegalArgumentException notBase64) {
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }

        if (keyBytes.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must decode to at least " + MINIMUM_KEY_BYTES
                            + " bytes for HS256. Generate one with: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public IssuedToken issueToken(User user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(tokenLifetime);

        String token = Jwts.builder()
                .subject(user.getId().toString())
                .issuer(issuer)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TENANT_ID, user.getTenantId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getName())
                .claim(CLAIM_ROLE, user.getRole().name())
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, expiresAt, tokenLifetime.toSeconds());
    }

    /**
     * Verifies a token and rebuilds the principal from its claims.
     *
     * @return the principal, or empty when the token is missing, malformed,
     *         expired, or signed with a different key
     */
    public Optional<AuthenticatedUser> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get(CLAIM_TENANT_ID, String.class)),
                    claims.get(CLAIM_EMAIL, String.class),
                    claims.get(CLAIM_NAME, String.class),
                    Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                    true));
        } catch (JwtException | IllegalArgumentException ex) {
            // Logged at debug because an invalid token is normal traffic: expired
            // sessions and probing requests both land here.
            log.debug("Rejected JWT: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @param token     the encoded JWT
     * @param expiresAt absolute expiry instant
     * @param expiresInSeconds lifetime in seconds, convenient for clients
     */
    public record IssuedToken(String token, Instant expiresAt, long expiresInSeconds) {
    }
}
