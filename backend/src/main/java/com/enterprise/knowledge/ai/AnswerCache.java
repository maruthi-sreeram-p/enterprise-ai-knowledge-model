package com.enterprise.knowledge.ai;

import com.enterprise.knowledge.ai.dto.RagAnswer;
import com.enterprise.knowledge.auth.AuthenticatedUser;
import com.enterprise.knowledge.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Caches generated answers in Redis.
 *
 * The cache key is the interesting part. Keying on the question alone would be a
 * data leak: the same question asked in two organisations has two different
 * correct answers, and asked by an employee and an administrator in the same
 * organisation it has two different authorised answers. The key therefore
 * combines:
 *
 *   tenant id   -- so organisations never share an entry;
 *   role        -- because role determines which documents were searchable, so
 *                  two roles can legitimately get different answers;
 *   corpus version -- a counter bumped whenever a document in the tenant is
 *                  added or removed, which invalidates that tenant's entries
 *                  without having to enumerate and delete them;
 *   question hash -- a SHA-256 of the normalised question.
 *
 * Redis failures are swallowed. A cache that is down should slow the system
 * down, not break it.
 */
@Component
public class AnswerCache {

    private static final Logger log = LoggerFactory.getLogger(AnswerCache.class);

    private static final String ANSWER_PREFIX = "ai:answer:";
    private static final String CORPUS_VERSION_PREFIX = "ai:corpus-version:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AppProperties.AnswerCache config;

    public AnswerCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                       AppProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.config = properties.getAnswerCache();
    }

    public Optional<RagAnswer> lookup(AuthenticatedUser user, String question) {
        if (!config.isEnabled()) {
            return Optional.empty();
        }
        try {
            String cached = redisTemplate.opsForValue().get(buildKey(user, question));
            if (cached == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(cached, RagAnswer.class));
        } catch (Exception ex) {
            log.warn("Answer cache lookup failed; continuing without it", ex);
            return Optional.empty();
        }
    }

    public void store(AuthenticatedUser user, String question, RagAnswer answer) {
        if (!config.isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(
                    buildKey(user, question),
                    objectMapper.writeValueAsString(answer),
                    Duration.ofMinutes(config.getTtlMinutes()));
        } catch (Exception ex) {
            log.warn("Could not write to the answer cache", ex);
        }
    }

    /**
     * Invalidates every cached answer for a tenant by advancing its corpus
     * version. Called whenever a document is added to or removed from the tenant,
     * because either change can alter what a previously cached answer should say.
     */
    public void invalidateTenant(UUID tenantId) {
        if (!config.isEnabled()) {
            return;
        }
        try {
            redisTemplate.opsForValue().increment(CORPUS_VERSION_PREFIX + tenantId);
            log.debug("Advanced answer cache corpus version for tenant {}", tenantId);
        } catch (Exception ex) {
            log.warn("Could not invalidate the answer cache for tenant {}", tenantId, ex);
        }
    }

    private String buildKey(AuthenticatedUser user, String question) {
        return ANSWER_PREFIX
                + user.getTenantId() + ':'
                + user.getRole().name() + ':'
                + corpusVersion(user.getTenantId()) + ':'
                + sha256(normalise(question));
    }

    private String corpusVersion(UUID tenantId) {
        String version = redisTemplate.opsForValue().get(CORPUS_VERSION_PREFIX + tenantId);
        return version == null ? "0" : version;
    }

    /** Collapses whitespace and case so trivially different phrasings share an entry. */
    private static String normalise(String question) {
        return question.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is required of every JVM, so this cannot happen in practice.
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
