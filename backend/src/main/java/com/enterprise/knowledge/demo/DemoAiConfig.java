package com.enterprise.knowledge.demo;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wiring for the {@code demo} profile: run the whole application with no Docker,
 * no PostgreSQL, and no API key.
 *
 * Three things are swapped, and nothing else. Security, tenant scoping, document
 * upload, text extraction, chunking, retrieval filtering, citation building,
 * conversations, auditing and rate limiting all run exactly as they do normally:
 *
 *   PostgreSQL   → H2 in memory (configured in application-demo.yml)
 *   pgvector     → SimpleVectorStore, Spring AI's in-memory store
 *   Gemini       → LocalEmbeddingModel and ExtractiveChatModel
 *
 * The tenant and visibility filter is applied by the in-memory store just as it
 * is by pgvector, so isolation is genuinely exercised here rather than bypassed.
 * {@code SimpleVectorStoreTenantIsolationTest} verifies that, and needs no Docker
 * to run.
 */
@Configuration
@Profile("demo")
public class DemoAiConfig {

    private static final Logger log = LoggerFactory.getLogger(DemoAiConfig.class);

    @PostConstruct
    void announceDemoMode() {
        log.warn("""

                ===============================================================
                  DEMO PROFILE ACTIVE

                  Database      : H2, in memory. All data is lost on shutdown.
                  Vector store  : in-memory. Lost on shutdown.
                  Embeddings    : local word-hash. Not semantic.
                  Chat answers  : extracted verbatim from documents.
                                  NO LANGUAGE MODEL IS CALLED.

                  For real answers, set GEMINI_API_KEY, start PostgreSQL,
                  and run without the `demo` profile.
                ===============================================================
                """);
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return new LocalEmbeddingModel();
    }

    @Bean
    public ChatModel chatModel() {
        return new ExtractiveChatModel();
    }

    /**
     * Spring AI's in-memory vector store. It evaluates the same
     * {@code Filter.Expression} that pgvector compiles into SQL, so the
     * authorisation filter built by {@code RagService} is honoured here too.
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }
}
