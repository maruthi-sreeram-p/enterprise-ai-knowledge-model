package com.enterprise.knowledge.chat;

import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.chat.dto.ChatRequest;
import com.enterprise.knowledge.chat.dto.ChatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Any authenticated role may ask a question. What differs between roles is
     * which documents the retrieval step is allowed to search, not whether the
     * endpoint can be called.
     */
    @PostMapping
    @Operation(summary = "Ask a question answered from the caller's authorized documents")
    public ResponseEntity<ChatResponse> ask(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(chatService.ask(principal, request));
    }
}
