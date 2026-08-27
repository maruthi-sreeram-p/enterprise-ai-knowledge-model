package com.enterprise.knowledge.auth;

import com.enterprise.knowledge.config.AppProperties;
import com.enterprise.knowledge.user.Role;
import com.enterprise.knowledge.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            "dGVzdC1vbmx5LXNpZ25pbmcta2V5LWZvci11bml0LWFuZC1pbnRlZ3JhdGlvbi10ZXN0cw==";
    private static final String DIFFERENT_SECRET =
            "YS1jb21wbGV0ZWx5LWRpZmZlcmVudC1zaWduaW5nLWtleS11c2VkLWJ5LWFuLWF0dGFja2Vy";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(propertiesWith(SECRET, 60));
        user = new User(UUID.randomUUID(), "Dana Reyes", "dana@example.test",
                "irrelevant-hash", Role.MANAGER);
    }

    @Test
    @DisplayName("a freshly issued token round-trips back to the same principal")
    void issuedTokenParsesBackToTheSamePrincipal() {
        JwtService.IssuedToken issued = jwtService.issueToken(user);

        Optional<AuthenticatedUser> parsed = jwtService.parseToken(issued.token());

        assertThat(parsed).isPresent();
        assertThat(parsed.get().getId()).isEqualTo(user.getId());
        assertThat(parsed.get().getTenantId()).isEqualTo(user.getTenantId());
        assertThat(parsed.get().getEmail()).isEqualTo(user.getEmail());
        assertThat(parsed.get().getRole()).isEqualTo(Role.MANAGER);
    }

    @Test
    @DisplayName("the tenant claim is carried by the token rather than looked up")
    void tokenCarriesTheTenantClaim() {
        AuthenticatedUser parsed = jwtService.parseToken(jwtService.issueToken(user).token()).orElseThrow();

        // This is the property the whole isolation model rests on: the tenant a
        // request acts in comes from a signed claim, not from anything the caller
        // can set.
        assertThat(parsed.getTenantId()).isEqualTo(user.getTenantId());
    }

    @Test
    @DisplayName("a token signed with another key is rejected")
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtService attackerService = new JwtService(propertiesWith(DIFFERENT_SECRET, 60));
        String forged = attackerService.issueToken(user).token();

        assertThat(jwtService.parseToken(forged)).isEmpty();
    }

    @Test
    @DisplayName("a token whose payload has been edited is rejected")
    void tamperedTokenIsRejected() {
        String token = jwtService.issueToken(user).token();
        String[] parts = token.split("\\.");

        // Flip a character in the payload segment, leaving the signature untouched.
        String payload = parts[1];
        char firstChar = payload.charAt(0);
        String tamperedPayload = (firstChar == 'A' ? 'B' : 'A') + payload.substring(1);
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThat(jwtService.parseToken(tampered)).isEmpty();
    }

    @Test
    @DisplayName("the token carries an expiry consistent with the configured lifetime")
    void tokenCarriesTheConfiguredExpiry() {
        // Expiry enforcement itself is jjwt's behaviour and is not re-tested here.
        // What is worth pinning is that this service passes the configured
        // lifetime through rather than silently using a default.
        JwtService.IssuedToken issued = new JwtService(propertiesWith(SECRET, 30)).issueToken(user);

        assertThat(issued.expiresInSeconds()).isEqualTo(1800L);
        assertThat(issued.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    @DisplayName("malformed input is rejected rather than throwing")
    void malformedTokenIsRejected() {
        assertThat(jwtService.parseToken("not-a-jwt")).isEmpty();
        assertThat(jwtService.parseToken("")).isEmpty();
        assertThat(jwtService.parseToken("a.b.c")).isEmpty();
    }

    @Test
    @DisplayName("a signing key shorter than 256 bits is refused at startup")
    void tooShortSecretIsRefused() {
        assertThatThrownBy(() -> new JwtService(propertiesWith("c2hvcnQ=", 60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    @DisplayName("a missing signing key is refused at startup")
    void missingSecretIsRefused() {
        assertThatThrownBy(() -> new JwtService(propertiesWith("", 60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("app.jwt.secret");
    }

    private static AppProperties propertiesWith(String secret, long expirationMinutes) {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret(secret);
        properties.getJwt().setExpirationMinutes(expirationMinutes);
        return properties;
    }
}
