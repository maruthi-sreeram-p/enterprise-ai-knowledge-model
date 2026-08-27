package com.enterprise.knowledge.support;

import com.enterprise.knowledge.demo.LocalEmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the Gemini models with offline stand-ins.
 *
 * Everything else in the context stays real: the same PgVectorStore, the same
 * filter translation, the same repositories, the same security chain. Only the
 * two calls that would leave the machine are substituted, which keeps the tests
 * fast, free, and independent of an API key.
 */
@TestConfiguration
public class TestAiConfiguration {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        return new LocalEmbeddingModel();
    }

    @Bean
    @Primary
    public ChatModel chatModel() {
        return new RecordingChatModel();
    }
}
