package com.enterprise.knowledge.user.dto;

import com.enterprise.knowledge.user.Role;
import jakarta.validation.constraints.Size;

/**
 * Partial update. Every field is optional; a null field leaves the current
 * value untouched. Email is not updatable because it is the login identifier.
 */
public record UpdateUserRequest(

        @Size(max = 200, message = "Name must be at most 200 characters")
        String name,

        Role role,

        Boolean enabled
) {
}
