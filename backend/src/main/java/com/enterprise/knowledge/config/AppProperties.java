package com.enterprise.knowledge.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings that are specific to this service, as opposed to the
 * framework-level settings that Spring Boot and Spring AI already own.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Storage storage = new Storage();
    private final Rag rag = new Rag();
    private final Audit audit = new Audit();
    private final Ingestion ingestion = new Ingestion();
    private final RateLimit rateLimit = new RateLimit();
    private final AnswerCache answerCache = new AnswerCache();

    public AnswerCache getAnswerCache() {
        return answerCache;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Storage getStorage() {
        return storage;
    }

    public Rag getRag() {
        return rag;
    }

    public Audit getAudit() {
        return audit;
    }

    public Ingestion getIngestion() {
        return ingestion;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class Jwt {

        /** Base64-encoded signing key. Must decode to at least 32 bytes for HS256. */
        @NotBlank
        private String secret;

        /** Token lifetime in minutes. */
        @Min(1)
        private long expirationMinutes = 120;

        private String issuer = "enterprise-ai-knowledge-base";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMinutes() {
            return expirationMinutes;
        }

        public void setExpirationMinutes(long expirationMinutes) {
            this.expirationMinutes = expirationMinutes;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }
    }

    public static class Storage {

        /** Root directory on disk where uploaded document files are written. */
        private String root = "./storage";

        /** Largest accepted upload, in bytes. Defaults to 20 MB. */
        @Min(1)
        private long maxFileSizeBytes = 20L * 1024 * 1024;

        public String getRoot() {
            return root;
        }

        public void setRoot(String root) {
            this.root = root;
        }

        public long getMaxFileSizeBytes() {
            return maxFileSizeBytes;
        }

        public void setMaxFileSizeBytes(long maxFileSizeBytes) {
            this.maxFileSizeBytes = maxFileSizeBytes;
        }
    }

    public static class Rag {

        /** Number of chunks retrieved from the vector store per question. */
        @Min(1)
        private int topK = 6;

        /** Minimum cosine similarity a chunk must reach to be used as context. */
        private double similarityThreshold = 0.35;

        /** Target size of each document chunk, in tokens. */
        @Min(50)
        private int chunkSizeTokens = 400;

        /** Chunks shorter than this many characters are discarded as noise. */
        @Min(1)
        private int minChunkCharacters = 60;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public int getChunkSizeTokens() {
            return chunkSizeTokens;
        }

        public void setChunkSizeTokens(int chunkSizeTokens) {
            this.chunkSizeTokens = chunkSizeTokens;
        }

        public int getMinChunkCharacters() {
            return minChunkCharacters;
        }

        public void setMinChunkCharacters(int minChunkCharacters) {
            this.minChunkCharacters = minChunkCharacters;
        }
    }

    public static class Audit {

        /**
         * Whether audit records keep the verbatim text of AI questions and answers.
         * Off by default: those strings can contain confidential document content,
         * and copying them into a long-lived audit table widens the blast radius of
         * any future leak. When off, audits still record who asked, when, in which
         * tenant, and which documents were cited.
         */
        private boolean storeQueryText = false;

        public boolean isStoreQueryText() {
            return storeQueryText;
        }

        public void setStoreQueryText(boolean storeQueryText) {
            this.storeQueryText = storeQueryText;
        }
    }

    public static class Ingestion {

        /**
         * How uploaded documents reach the extraction and embedding pipeline.
         *
         * Both modes return from the upload request immediately and leave the
         * document in PENDING; embedding a large PDF takes far too long to hold an
         * HTTP request open for. What differs is durability. IN_PROCESS keeps the
         * queue in memory, so work queued at the moment of a restart is lost and
         * the document stays PENDING. KAFKA persists the event, survives a
         * restart, and allows ingestion to be scaled separately from the API.
         */
        private Mode mode = Mode.IN_PROCESS;

        private String topic = "document.uploaded";

        public Mode getMode() {
            return mode;
        }

        public void setMode(Mode mode) {
            this.mode = mode;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }

        public enum Mode {
            IN_PROCESS,
            KAFKA
        }
    }

    public static class AnswerCache {

        /**
         * Caching AI answers saves a Gemini call for repeated questions, which is
         * the single most expensive part of a request. It is off by default
         * because a cache that returns the wrong tenant's answer is far worse than
         * a slow one, so it should be a deliberate choice.
         */
        private boolean enabled = false;

        /** How long a cached answer stays valid. */
        @Min(1)
        private long ttlMinutes = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getTtlMinutes() {
            return ttlMinutes;
        }

        public void setTtlMinutes(long ttlMinutes) {
            this.ttlMinutes = ttlMinutes;
        }
    }

    public static class RateLimit {

        private boolean enabled = true;

        /** Maximum AI questions a single user may ask per window. */
        @Min(1)
        private int aiRequestsPerWindow = 20;

        /** Length of the rate-limit window, in seconds. */
        @Min(1)
        private long windowSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getAiRequestsPerWindow() {
            return aiRequestsPerWindow;
        }

        public void setAiRequestsPerWindow(int aiRequestsPerWindow) {
            this.aiRequestsPerWindow = aiRequestsPerWindow;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
