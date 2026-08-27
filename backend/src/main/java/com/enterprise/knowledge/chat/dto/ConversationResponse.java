package com.enterprise.knowledge.chat.dto;

import com.enterprise.knowledge.chat.Conversation;

import java.time.Instant;
import java.util.UUID;

/** Summary used in the conversation list; carries no messages. */
public record ConversationResponse(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt
) {

    public static ConversationResponse from(Conversation conversation) {
        return new ConversationResponse(conversation.getId(), conversation.getTitle(),
                conversation.getCreatedAt(), conversation.getUpdatedAt());
    }
}
