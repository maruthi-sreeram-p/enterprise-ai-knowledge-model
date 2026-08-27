package com.enterprise.knowledge.user.dto;

import com.enterprise.knowledge.user.Role;
import com.enterprise.knowledge.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * The public view of a user. Notably absent: the password hash. Entities are
 * never serialised directly for exactly this reason.
 */
public record UserResponse(
        UUID id,
        UUID tenantId,
        String name,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getTenantId(), user.getName(),
                user.getEmail(), user.getRole(), user.isEnabled(), user.getCreatedAt());
    }
}
