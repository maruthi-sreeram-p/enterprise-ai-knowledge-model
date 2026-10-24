package com.enterprise.knowledge.ai;

import com.enterprise.knowledge.common.exception.AiServiceException;
import com.enterprise.knowledge.common.exception.DocumentProcessingException;
import com.enterprise.knowledge.config.AppProperties;
import com.google.genai.errors.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes chunks to the vector store in batches, waiting out provider rate limits.
 *
 * Embedding is metered per minute (30,000 tokens on Gemini's free tier), so a
 * large document cannot be embedded in one burst. Sending every batch immediately
 * makes the provider answer 429 and the whole document fails, which is what
 * happened to a 1.9 MB PDF before this class existed. Instead, a 429 is treated as
 * "wait, then continue": the delay the provider asks for is honoured, capped, and
 * the same batch is retried.
 *
 * Retrying a batch is safe. The vector store embeds the whole batch before it
 * inserts anything, so a batch that fails on embedding has written nothing and
 * cannot leave duplicates behind.
 */
@Component
public class ThrottledVectorWriter {

    private static final Logger log = LoggerFactory.getLogger(ThrottledVectorWriter.class);

    /** The provider writes "Please retry in 18.27s." in its error text. */
    private static final Pattern RETRY_HINT = Pattern.compile("retry in ([0-9]+(?:\\.[0-9]+)?)s");

    /** Used when a 429 carries no usable hint. One quota window is a minute. */
    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(30);

    private final VectorStore vectorStore;
    private final AppProperties.Ingestion settings;
    private final Sleeper sleeper;

    // Explicit because there are two constructors: with no hint Spring looks for a
    // no-argument one and refuses to start. The second exists only for tests.
    @Autowired
    public ThrottledVectorWriter(VectorStore vectorStore, AppProperties properties) {
        this(vectorStore, properties, Thread::sleep);
    }

    /** Lets tests run without really waiting. */
    ThrottledVectorWriter(VectorStore vectorStore, AppProperties properties, Sleeper sleeper) {
        this.vectorStore = vectorStore;
        this.settings = properties.getIngestion();
        this.sleeper = sleeper;
    }

    public void addAll(List<Document> chunks) {
        int batchSize = settings.getEmbeddingBatchSize();

        for (int start = 0; start < chunks.size(); start += batchSize) {
            List<Document> batch = chunks.subList(start, Math.min(start + batchSize, chunks.size()));
            addWithRetry(batch, start, chunks.size());
        }
    }

    private void addWithRetry(List<Document> batch, int offset, int total) {
        int attempt = 0;

        while (true) {
            try {
                vectorStore.add(batch);
                return;

            } catch (RuntimeException ex) {
                if (!isQuotaExceeded(ex)) {
                    throw new AiServiceException("Embedding the document failed.", ex);
                }
                if (attempt >= settings.getMaxQuotaRetries()) {
                    log.error("Embedding quota still exhausted after {} waits at chunk {} of {}",
                            attempt, offset, total);
                    // Safe to show a user: it names no key, model, or provider detail.
                    throw new DocumentProcessingException(
                            "The embedding service's usage quota ran out before this document "
                                    + "finished indexing. Wait a few minutes and upload it again, "
                                    + "or use a plan with a higher quota.");
                }

                Duration wait = waitFor(ex);
                attempt++;
                log.warn("Embedding quota reached at chunk {} of {}. Waiting {}s, then retrying "
                        + "(attempt {} of {})", offset, total, wait.toSeconds(), attempt,
                        settings.getMaxQuotaRetries());
                pause(wait);
            }
        }
    }

    /** True when any exception in the chain is the provider's 429. */
    static boolean isQuotaExceeded(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException api && api.code() == 429) {
                return true;
            }
        }
        return false;
    }

    /**
     * How long to wait: the provider's own hint, plus a second of margin, bounded
     * above. Without the cap, a malformed or hostile hint could park an ingestion
     * thread for an unbounded time.
     */
    Duration waitFor(Throwable failure) {
        Duration cap = Duration.ofSeconds(settings.getMaxQuotaWaitSeconds());

        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ApiException api && api.message() != null) {
                Matcher matcher = RETRY_HINT.matcher(api.message());
                if (matcher.find()) {
                    long millis = (long) (Double.parseDouble(matcher.group(1)) * 1000) + 1000;
                    return Duration.ofMillis(Math.min(millis, cap.toMillis()));
                }
            }
        }
        return DEFAULT_WAIT.compareTo(cap) > 0 ? cap : DEFAULT_WAIT;
    }

    private void pause(Duration wait) {
        try {
            sleeper.sleep(wait.toMillis());
        } catch (InterruptedException interrupted) {
            // Shutdown while waiting. Restore the flag and stop rather than retry.
            Thread.currentThread().interrupt();
            throw new AiServiceException("Indexing was interrupted.", interrupted);
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }
}
