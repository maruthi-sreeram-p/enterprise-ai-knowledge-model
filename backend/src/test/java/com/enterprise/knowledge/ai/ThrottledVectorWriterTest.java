package com.enterprise.knowledge.ai;

import com.enterprise.knowledge.common.exception.AiServiceException;
import com.enterprise.knowledge.common.exception.DocumentProcessingException;
import com.enterprise.knowledge.config.AppProperties;
import com.google.genai.errors.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Covers the failure that stopped a 1.9 MB PDF from indexing: Gemini's free tier
 * meters embedding per minute and answers 429, and the old code gave up on the
 * first one. Nothing here touches the network or really waits.
 */
class ThrottledVectorWriterTest {

    /** The wording Google actually returned, taken from the server log. */
    private static final String REAL_429_MESSAGE =
            "You exceeded your current quota, please check your plan and billing details. "
                    + "* Quota exceeded for metric: embed_content_free_tier_tokens, limit: 30000. "
                    + "Please retry in 18.272610327s.";

    private VectorStore vectorStore;
    private AppProperties properties;
    private List<Long> sleeps;
    private ThrottledVectorWriter writer;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        properties = new AppProperties();
        properties.getIngestion().setEmbeddingBatchSize(10);
        properties.getIngestion().setMaxQuotaRetries(3);
        properties.getIngestion().setMaxQuotaWaitSeconds(70);
        sleeps = new ArrayList<>();
        writer = new ThrottledVectorWriter(vectorStore, properties, sleeps::add);
    }

    @Test
    @DisplayName("chunks are written in batches of the configured size")
    void writesInBatches() {
        writer.addAll(chunks(25));

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(3)).add(captor.capture());
        assertThat(captor.getAllValues()).extracting(List::size).containsExactly(10, 10, 5);
        assertThat(sleeps).as("no waiting when nothing is rate limited").isEmpty();
    }

    @Test
    @DisplayName("a 429 is waited out and the same batch is retried, so the document completes")
    void quotaErrorIsWaitedOutAndRetried() {
        // Fails twice with the real error, then succeeds.
        doThrow(wrapped(quotaError(REAL_429_MESSAGE)))
                .doThrow(wrapped(quotaError(REAL_429_MESSAGE)))
                .doNothing()
                .when(vectorStore).add(anyList());

        writer.addAll(chunks(5));

        verify(vectorStore, times(3)).add(anyList());
        assertThat(sleeps).hasSize(2);
    }

    @Test
    @DisplayName("the wait honours the provider's hint plus a one second margin")
    void waitFollowsTheProvidersHint() {
        Duration wait = writer.waitFor(quotaError(REAL_429_MESSAGE));

        // 18.27s from the hint + 1s margin.
        assertThat(wait.toMillis()).isBetween(19_000L, 19_500L);
    }

    @Test
    @DisplayName("a hint is found even when the 429 is buried in a cause chain")
    void hintIsFoundInTheCauseChain() {
        Duration wait = writer.waitFor(wrapped(quotaError("Please retry in 5s.")));

        assertThat(wait.toMillis()).isEqualTo(6_000L);
    }

    @Test
    @DisplayName("an absurd hint is capped rather than parking the thread")
    void waitIsCapped() {
        Duration wait = writer.waitFor(quotaError("Please retry in 99999s."));

        assertThat(wait).isEqualTo(Duration.ofSeconds(70));
    }

    @Test
    @DisplayName("a 429 with no hint falls back to a default wait")
    void missingHintUsesTheDefault() {
        Duration wait = writer.waitFor(quotaError("You exceeded your current quota."));

        assertThat(wait).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("persistent quota exhaustion fails with a message the user can act on")
    void givesUpWithAHelpfulMessageAfterTheRetryLimit() {
        doThrow(wrapped(quotaError(REAL_429_MESSAGE))).when(vectorStore).add(anyList());

        assertThatThrownBy(() -> writer.addAll(chunks(5)))
                .isInstanceOf(DocumentProcessingException.class)
                .hasMessageContaining("quota")
                .hasMessageContaining("upload it again")
                // The text is shown to end users, so it must not carry provider detail.
                .hasMessageNotContaining("embed_content_free_tier_tokens")
                .hasMessageNotContaining("30000");

        // First attempt plus three retries.
        verify(vectorStore, times(4)).add(anyList());
    }

    @Test
    @DisplayName("an error that is not a 429 fails immediately and is not retried")
    void otherErrorsAreNotRetried() {
        doThrow(wrapped(new ClientException(400, "INVALID_ARGUMENT", "API key not valid")))
                .when(vectorStore).add(anyList());

        assertThatThrownBy(() -> writer.addAll(chunks(5)))
                .isInstanceOf(AiServiceException.class);

        verify(vectorStore, times(1)).add(anyList());
        assertThat(sleeps).isEmpty();
    }

    @Test
    @DisplayName("later batches are still written after an earlier one was rate limited")
    void remainingBatchesContinueAfterAWait() {
        doThrow(wrapped(quotaError("Please retry in 1s.")))
                .doNothing().doNothing()
                .when(vectorStore).add(anyList());

        writer.addAll(chunks(20));

        // Batch one: fail + success. Batch two: success.
        verify(vectorStore, times(3)).add(anyList());
        assertThat(sleeps).hasSize(1);
    }

    // ------------------------------------------------------------------ helpers

    private static ClientException quotaError(String message) {
        return new ClientException(429, "RESOURCE_EXHAUSTED", message);
    }

    /** Spring AI wraps the provider's exception, so tests do the same. */
    private static RuntimeException wrapped(RuntimeException inner) {
        return new RuntimeException("Failed to embed", inner);
    }

    private static List<Document> chunks(int count) {
        return IntStream.range(0, count).mapToObj(i -> new Document("chunk " + i)).toList();
    }
}
