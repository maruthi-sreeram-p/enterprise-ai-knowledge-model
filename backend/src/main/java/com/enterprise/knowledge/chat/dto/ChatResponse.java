package com.enterprise.knowledge.chat.dto;

import com.enterprise.knowledge.ai.dto.SourceCitation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param conversationId the thread this answer belongs to, newly created when
 *                       the request did not name one
 * @param grounded       false when nothing relevant was found, in which case the
 *                       answer says so and no sources are returned
 */
public record ChatResponse(
        UUID conversationId,
        UUID messageId,
        String conversationTitle,
        String answer,
        List<SourceCitation> sources,
        boolean grounded,
        Instant createdAt
) {
}
