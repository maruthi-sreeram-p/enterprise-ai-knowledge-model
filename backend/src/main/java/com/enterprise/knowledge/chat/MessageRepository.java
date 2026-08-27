package com.enterprise.knowledge.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Messages carry no tenant column of their own. They are only ever reached
 * through a conversation that has already been resolved with a tenant and user
 * check, which keeps the ownership rule in exactly one place.
 */
public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    void deleteByConversationId(UUID conversationId);

    long countByConversationId(UUID conversationId);
}
