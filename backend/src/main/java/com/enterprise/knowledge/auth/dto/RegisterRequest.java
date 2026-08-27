package com.enterprise.knowledge.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registers a new organisation together with its first administrator.
 *
 * This is the only path that creates a tenant. Subsequent colleagues are added
 * by that administrator through POST /api/users, so joining an existing
 * organisation is always an invitation rather than something a stranger can do
 * by guessing the organisation name.
 */
public record RegisterRequest(

        @NotBlank(message = "Organization name is required")
        @Size(max = 200, message = "Organization name must be at most 200 characters")
        String organizationName,

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email,

        // BCrypt only considers the first 72 bytes of a password.
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password
) {
}
