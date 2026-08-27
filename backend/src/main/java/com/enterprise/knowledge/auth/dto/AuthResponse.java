package com.enterprise.knowledge.auth.dto;

import com.enterprise.knowledge.user.dto.UserResponse;

import java.time.Instant;

/**
 * @param accessToken      the bearer token to send as the Authorization header
 * @param tokenType        always "Bearer"
 * @param expiresAt        absolute expiry, so a client can refresh before it lapses
 * @param expiresInSeconds lifetime in seconds
 * @param user             the authenticated user
 * @param organizationName display name of the user's tenant
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        Instant expiresAt,
        long expiresInSeconds,
        UserResponse user,
        String organizationName
) {

    public static AuthResponse of(String accessToken, Instant expiresAt, long expiresInSeconds,
                                  UserResponse user, String organizationName) {
        return new AuthResponse(accessToken, "Bearer", expiresAt, expiresInSeconds, user, organizationName);
    }
}
