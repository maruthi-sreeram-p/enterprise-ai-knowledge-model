package com.enterprise.knowledge.ratelimit;

import com.enterprise.knowledge.common.exception.RateLimitExceededException;
import com.enterprise.knowledge.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * A fixed-window rate limiter backed by Redis.
 *
 * Redis rather than an in-memory counter because the limit has to hold across
 * every instance of the service; a per-instance counter would let a user
 * multiply their allowance by the number of instances behind the load balancer.
 *
 * A fixed window is used rather than a sliding one because it is one INCR and
 * one EXPIRE, and its known weakness — up to twice the limit across a window
 * boundary — does not matter for protecting a paid API from casual overuse.
 */
@Component
public class RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final String KEY_PREFIX = "ratelimit:ai:";

    private final StringRedisTemplate redisTemplate;
    private final AppProperties.RateLimit config;

    public RateLimiter(StringRedisTemplate redisTemplate, AppProperties properties) {
        this.redisTemplate = redisTemplate;
        this.config = properties.getRateLimit();
    }

    /**
     * Records one AI request for a user and rejects it if they are over the limit.
     *
     * If Redis is unreachable the request is allowed through. That is a conscious
     * choice: the limiter protects against cost overruns, and refusing every
     * question because the cache is down would turn a minor outage into a total
     * one. The failure is logged so it does not pass unnoticed.
     *
     * @throws RateLimitExceededException when the user is over the limit
     */
    public void checkAiQuota(UUID userId) {
        if (!config.isEnabled()) {
            return;
        }

        String key = KEY_PREFIX + userId;

        try {
            Long count = redisTemplate.opsForValue().increment(key);

            if (count == null) {
                return;
            }
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(config.getWindowSeconds()));
            }
            if (count > config.getAiRequestsPerWindow()) {
                Long remaining = redisTemplate.getExpire(key);
                long retryAfter = remaining != null && remaining > 0 ? remaining : config.getWindowSeconds();

                log.info("User {} exceeded the AI request limit", userId);
                throw new RateLimitExceededException(retryAfter);
            }

        } catch (RateLimitExceededException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Rate limiting is unavailable; allowing the request through", ex);
        }
    }
}
