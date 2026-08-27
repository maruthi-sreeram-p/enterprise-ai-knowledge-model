package com.enterprise.knowledge.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param conversationId the thread to continue, or null to start a new one
 * @param question       the user's question
 */
public record ChatRequest(

        UUID conversationId,

        @NotBlank(message = "Question is required")
        @Size(max = 4000, message = "Question must be at most 4000 characters")
        String question
) {
}
