package com.slicelink.urls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class UrlCacheServiceTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private UrlCacheProperties properties;
    private UrlCacheService urlCacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        properties = new UrlCacheProperties(Duration.ofHours(24), "url:redirect:");
        urlCacheService = new UrlCacheService(redisTemplate, properties);
    }

    @Nested
    @DisplayName("Key Construction")
    class KeyConstructionTests {

        @Test
        @DisplayName("buildKey constructs namespaced key with configured prefix")
        void buildKey_formatsKeyCorrectly() {
            String key = urlCacheService.buildKey("3D7gK");
            assertThat(key).isEqualTo("url:redirect:3D7gK");
        }
    }

    @Nested
    @DisplayName("Cache Retrieval (GET)")
    class GetTests {

        @Test
        @DisplayName("get returns cached URL on cache hit")
        void get_whenKeyExists_returnsCachedUrl() {
            when(valueOperations.get("url:redirect:abc123")).thenReturn("https://example.com/target");

            Optional<String> result = urlCacheService.get("abc123");

            assertThat(result).contains("https://example.com/target");
            verify(valueOperations).get("url:redirect:abc123");
        }

        @Test
        @DisplayName("get returns empty optional on cache miss")
        void get_whenKeyDoesNotExist_returnsEmpty() {
            when(valueOperations.get("url:redirect:nonexistent")).thenReturn(null);

            Optional<String> result = urlCacheService.get("nonexistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("get returns empty optional for null or blank input without querying Redis")
        void get_whenInputBlank_returnsEmpty() {
            assertThat(urlCacheService.get(null)).isEmpty();
            assertThat(urlCacheService.get("   ")).isEmpty();
        }

        @Test
        @DisplayName("get gracefully catches RedisConnectionFailureException and returns empty optional")
        void get_whenRedisConnectionFails_returnsEmptyGracefully() {
            when(valueOperations.get(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            Optional<String> result = urlCacheService.get("abc123");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("get gracefully catches QueryTimeoutException and returns empty optional")
        void get_whenRedisTimesOut_returnsEmptyGracefully() {
            when(valueOperations.get(anyString()))
                    .thenThrow(new QueryTimeoutException("Redis command timed out"));

            Optional<String> result = urlCacheService.get("abc123");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("Cache Storage (PUT)")
    class PutTests {

        @Test
        @DisplayName("put stores value in Redis with configured TTL")
        void put_storesValueWithTtl() {
            urlCacheService.put("abc123", "https://example.com/target");

            verify(valueOperations).set("url:redirect:abc123", "https://example.com/target", Duration.ofHours(24));
        }

        @Test
        @DisplayName("put does nothing when input is null or blank")
        void put_whenInputInvalid_doesNothing() {
            assertThatCode(() -> urlCacheService.put(null, "https://example.com")).doesNotThrowAnyException();
            assertThatCode(() -> urlCacheService.put("abc", null)).doesNotThrowAnyException();
            assertThatCode(() -> urlCacheService.put("  ", "https://example.com")).doesNotThrowAnyException();
            assertThatCode(() -> urlCacheService.put("abc", "  ")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("put gracefully catches Redis exception and does not propagate error")
        void put_whenRedisFails_doesNotThrow() {
            org.mockito.Mockito.doThrow(new RedisConnectionFailureException("Connection refused"))
                    .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

            assertThatCode(() -> urlCacheService.put("abc123", "https://example.com/target"))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Cache Invalidation (EVICT)")
    class EvictTests {

        @Test
        @DisplayName("evict deletes key from Redis")
        void evict_deletesKey() {
            urlCacheService.evict("abc123");

            verify(redisTemplate).delete("url:redirect:abc123");
        }

        @Test
        @DisplayName("evict does nothing when input is null or blank")
        void evict_whenInputInvalid_doesNothing() {
            assertThatCode(() -> urlCacheService.evict(null)).doesNotThrowAnyException();
            assertThatCode(() -> urlCacheService.evict("  ")).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("evict gracefully catches Redis exception and does not propagate error")
        void evict_whenRedisFails_doesNotThrow() {
            when(redisTemplate.delete(anyString()))
                    .thenThrow(new RedisConnectionFailureException("Connection refused"));

            assertThatCode(() -> urlCacheService.evict("abc123")).doesNotThrowAnyException();
        }
    }
}
