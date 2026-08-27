package com.enterprise.knowledge.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One turn in a conversation.
 *
 * Citations are kept as a JSON string rather than a child table. They are always
 * read and written together with their message and are never queried on their
 * own, so a separate table would add a join without buying anything.
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MessageRole role;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    /** JSON array of citations, present only on assistant messages that had sources. */
    @Column(name = "sources_json", columnDefinition = "text")
    private String sourcesJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Message() {
        // Required by JPA.
    }

    public Message(UUID conversationId, MessageRole role, String content, String sourcesJson) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.role = role;
        this.content = content;
        this.sourcesJson = sourcesJson;
        this.createdAt = Instant.now();
    }

    public static Message userMessage(UUID conversationId, String content) {
        return new Message(conversationId, MessageRole.USER, content, null);
    }

    public static Message assistantMessage(UUID conversationId, String content, String sourcesJson) {
        return new Message(conversationId, MessageRole.ASSISTANT, content, sourcesJson);
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSourcesJson() {
        return sourcesJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
