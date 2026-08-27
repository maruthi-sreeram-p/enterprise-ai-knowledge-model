package com.enterprise.knowledge.chat;

import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.chat.dto.ConversationDetailResponse;
import com.enterprise.knowledge.chat.dto.ConversationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Conversation history. Every operation is scoped to the caller's own threads;
 * there is no endpoint for reading another user's history.
 */
@RestController
@RequestMapping("/api/conversations")
@Tag(name = "Conversations")
public class ConversationController {

    private final ChatService chatService;

    public ConversationController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    @Operation(summary = "List the caller's conversations, most recently used first")
    public ResponseEntity<Page<ConversationResponse>> listConversations(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PageableDefault(size = 30) Pageable pageable) {
        return ResponseEntity.ok(chatService.listConversations(principal, pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch one conversation with its full message history")
    public ResponseEntity<ConversationDetailResponse> getConversation(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(chatService.getConversation(principal, id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete one of the caller's conversations")
    public ResponseEntity<Void> deleteConversation(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable UUID id) {
        chatService.deleteConversation(principal, id);
        return ResponseEntity.noContent().build();
    }
}
