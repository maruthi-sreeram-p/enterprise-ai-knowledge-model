package com.enterprise.knowledge.chat.dto;

import com.enterprise.knowledge.ai.dto.SourceCitation;
import com.enterprise.knowledge.chat.MessageRole;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageResponse(
        UUID id,
        MessageRole role,
        String content,
        List<SourceCitation> sources,
        Instant createdAt
) {
}
