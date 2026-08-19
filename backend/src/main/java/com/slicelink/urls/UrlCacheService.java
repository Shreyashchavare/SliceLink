package com.slicelink.urls;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Service managing Redis cache operations for URL redirection.
 *
 * <p>Implements cache-aside semantics with non-blocking error handling:
 * if Redis operations fail, a warning is logged and the operation gracefully
 * falls back to the authoritative PostgreSQL database.
 */
@Service
@EnableConfigurationProperties(UrlCacheProperties.class)
public class UrlCacheService {

    public record CachedUrlMetadata(Long urlId, Long userId) {}

    private static final Logger log = LoggerFactory.getLogger(UrlCacheService.class);
    private static final String META_PREFIX = "url:meta:";

    private final StringRedisTemplate redisTemplate;
    private final UrlCacheProperties properties;

    public UrlCacheService(StringRedisTemplate redisTemplate, UrlCacheProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Looks up the cached destination URL for a given short code.
     *
     * @param shortCode the Base62 short code
     * @return an Optional containing the original URL if present in cache, or empty on cache miss/error
     */
    public Optional<String> get(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            return Optional.empty();
        }

        String key = buildKey(shortCode);
        try {
            String cachedUrl = redisTemplate.opsForValue().get(key);
            if (cachedUrl != null && !cachedUrl.isBlank()) {
                log.debug("Cache HIT for key: {}", key);
                return Optional.of(cachedUrl);
            }
            log.debug("Cache MISS for key: {}", key);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis GET failed for key {}. Falling back to database: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Looks up cached metadata (urlId, userId) for a given short code.
     *
     * @param shortCode the Base62 short code
     * @return an Optional containing CachedUrlMetadata if present, or empty on miss/error
     */
    public Optional<CachedUrlMetadata> getMetadata(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            return Optional.empty();
        }

        String key = META_PREFIX + shortCode;
        try {
            String val = redisTemplate.opsForValue().get(key);
            if (val != null && val.contains(":")) {
                String[] parts = val.split(":");
                return Optional.of(new CachedUrlMetadata(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
            }
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis GET metadata failed for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Stores a short code destination in Redis with the configured TTL.
     *
     * @param shortCode the Base62 short code
     * @param originalUrl the target destination URL
     */
    public void put(String shortCode, String originalUrl) {
        if (shortCode == null || originalUrl == null || shortCode.isBlank() || originalUrl.isBlank()) {
            return;
        }

        String key = buildKey(shortCode);
        try {
            redisTemplate.opsForValue().set(key, originalUrl, properties.urlTtl());
            log.debug("Cached redirect for key: {} with TTL: {}", key, properties.urlTtl());
        } catch (Exception e) {
            log.warn("Redis PUT failed for key {}. Continuing without caching: {}", key, e.getMessage());
        }
    }

    /**
     * Stores a short code destination and metadata in Redis with the configured TTL.
     *
     * @param shortCode the Base62 short code
     * @param originalUrl the target destination URL
     * @param urlId the primary key ID of the URL record
     * @param userId the ID of the URL owner
     */
    public void put(String shortCode, String originalUrl, Long urlId, Long userId) {
        put(shortCode, originalUrl);
        if (shortCode == null || urlId == null || userId == null) {
            return;
        }
        String metaKey = META_PREFIX + shortCode;
        try {
            redisTemplate.opsForValue().set(metaKey, urlId + ":" + userId, properties.urlTtl());
        } catch (Exception e) {
            log.warn("Redis PUT metadata failed for key {}: {}", metaKey, e.getMessage());
        }
    }

    /**
     * Evicts a short code from Redis cache.
     *
     * @param shortCode the Base62 short code to invalidate
     */
    public void evict(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            return;
        }

        String key = buildKey(shortCode);
        try {
            redisTemplate.delete(key);
            redisTemplate.delete(META_PREFIX + shortCode);
            log.debug("Evicted cache for key: {}", key);
        } catch (Exception e) {
            log.warn("Redis EVICT failed for key {}: {}", key, e.getMessage());
        }
    }

    /**
     * Constructs the full Redis cache key for a short code.
     *
     * @param shortCode the Base62 short code
     * @return the namespaced Redis key
     */
    public String buildKey(String shortCode) {
        return properties.keyPrefix() + shortCode;
    }

    public UrlCacheProperties getProperties() {
        return properties;
    }
}
