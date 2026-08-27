package com.enterprise.knowledge.ai;

import com.enterprise.knowledge.config.AppProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.template.NoOpTemplateRenderer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * The single {@link ChatClient} used for grounded answers.
     *
     * The template renderer is replaced with a no-op one on purpose. By default
     * Spring AI runs prompt text through StringTemplate, which treats braces as
     * placeholders. Retrieved context is arbitrary company text and routinely
     * contains braces in code samples, JSON snippets and formulas; rendering it
     * as a template would corrupt the prompt or fail the request outright. This
     * application builds its prompt itself and wants the text passed through
     * untouched.
     */
    @Bean
    public ChatClient ragChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultTemplateRenderer(new NoOpTemplateRenderer())
                .build();
    }

    /**
     * Splits page text into embeddable chunks.
     *
     * Chunk size is a genuine trade-off: larger chunks preserve more surrounding
     * context but dilute the embedding, while smaller ones match more precisely
     * but can cut a sentence away from the fact that qualifies it. The default
     * here is a middle setting and is configurable rather than hardcoded.
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter(AppProperties properties) {
        return TokenTextSplitter.builder()
                .withChunkSize(properties.getRag().getChunkSizeTokens())
                .withMinChunkSizeChars(properties.getRag().getMinChunkCharacters())
                .withMinChunkLengthToEmbed(properties.getRag().getMinChunkCharacters())
                .withKeepSeparator(true)
                .build();
    }
}
