package com.enterprise.knowledge.user.dto;

import com.enterprise.knowledge.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Used by an administrator to add a colleague to their own organisation.
 *
 * There is deliberately no tenant field. The new user is placed in the calling
 * administrator's tenant, taken from their token, so an administrator cannot
 * create accounts inside someone else's organisation.
 */
public record CreateUserRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 320, message = "Email must be at most 320 characters")
        String email,

        // BCrypt only considers the first 72 bytes of a password. Capping the
        // input at 72 avoids silently ignoring the tail of a longer one.
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        String password,

        @NotNull(message = "Role is required")
        Role role
) {
}
