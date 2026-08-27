package com.enterprise.knowledge.chat.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A conversation together with its full message history. */
public record ConversationDetailResponse(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<MessageResponse> messages
) {
}
