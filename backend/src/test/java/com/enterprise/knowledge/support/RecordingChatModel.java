package com.enterprise.knowledge.support;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * A stand-in for Gemini that records every prompt it is given.
 *
 * Recording is the point. The strongest assertion available for tenant isolation
 * is not that the answer looks right, but that another tenant's text was never
 * placed in the prompt at all. This makes that directly checkable.
 */
public class RecordingChatModel implements ChatModel {

    private final List<String> receivedPrompts = new CopyOnWriteArrayList<>();
    private volatile String nextResponse = "Answer based on the provided context [1].";

    @Override
    public ChatResponse call(Prompt prompt) {
        receivedPrompts.add(prompt.getInstructions().stream()
                .map(Message::getText)
                .collect(Collectors.joining("\n")));

        return new ChatResponse(List.of(new Generation(new AssistantMessage(nextResponse))));
    }

    public void respondWith(String response) {
        this.nextResponse = response;
    }

    public List<String> getReceivedPrompts() {
        return List.copyOf(receivedPrompts);
    }

    public String lastPrompt() {
        if (receivedPrompts.isEmpty()) {
            throw new IllegalStateException("The chat model was never called");
        }
        return receivedPrompts.get(receivedPrompts.size() - 1);
    }

    public boolean wasCalled() {
        return !receivedPrompts.isEmpty();
    }

    public void reset() {
        receivedPrompts.clear();
    }
}
