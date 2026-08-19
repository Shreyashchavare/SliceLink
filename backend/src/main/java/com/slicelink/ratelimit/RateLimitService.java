package com.slicelink.ratelimit;

import java.time.Duration;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service managing Redis-backed rate limiting for abuse prevention.
 *
 * <p>Implements a fixed-window counter algorithm with an atomic increment.
 * Follows a FAIL-OPEN policy: if Redis is unavailable or times out, a warning
 * is logged and requests are allowed to proceed to preserve core functionality.
 */
@Service
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);

    private static final String LOGIN_NAMESPACE = "slicelink:rate-limit:login:";
    private static final String URL_CREATE_NAMESPACE = "slicelink:rate-limit:url-create:";

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public RateLimitService(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Checks if a login attempt is allowed for the given identifier.
     *
     * @param identifier the email address or login identifier
     * @return {@code true} if the request is within limits or Redis fails open; {@code false} if limited
     */
    public boolean allowLogin(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return true;
        }

        String key = buildLoginKey(identifier);
        return allow(key, properties.login().maxRequests(), properties.login().window());
    }

    /**
     * Checks if a URL creation request is allowed for the authenticated user ID.
     *
     * @param userId the primary key ID of the authenticated user
     * @return {@code true} if the request is within limits or Redis fails open; {@code false} if limited
     */
    public boolean allowUrlCreation(Long userId) {
        if (userId == null) {
            return true;
        }

        String key = buildUrlCreateKey(userId);
        return allow(key, properties.urlCreate().maxRequests(), properties.urlCreate().window());
    }

    /**
     * Executes the fixed-window rate limit check on a given Redis key.
     *
     * @param key         the namespaced Redis key
     * @param maxRequests maximum permitted requests within the window
     * @param window      duration of the rate limit window
     * @return {@code true} if allowed; {@code false} if exceeded
     */
    public boolean allow(String key, int maxRequests, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1) {
                redisTemplate.expire(key, window);
            }
            if (count != null && count > maxRequests) {
                log.debug("Rate limit exceeded for key {}: count={}, maxRequests={}", key, count, maxRequests);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Redis rate-limiting failed for key {}. Failing open: {}", key, e.getMessage());
            return true;
        }
    }

    /**
     * Builds the deterministic Redis key for login rate limiting.
     *
     * @param identifier the raw email address or identifier
     * @return the normalized namespaced Redis key
     */
    public String buildLoginKey(String identifier) {
        String normalized = identifier.trim().toLowerCase(Locale.ROOT);
        return LOGIN_NAMESPACE + normalized;
    }

    /**
     * Builds the deterministic Redis key for URL creation rate limiting.
     *
     * @param userId the user ID
     * @return the namespaced Redis key
     */
    public String buildUrlCreateKey(Long userId) {
        return URL_CREATE_NAMESPACE + userId;
    }

    public RateLimitProperties getProperties() {
        return properties;
    }
}
